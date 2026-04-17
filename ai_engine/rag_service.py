import json
import os
import re
from typing import Annotated
from typing_extensions import TypedDict

from langchain.tools import tool
from langchain_core.documents import Document
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.config import get_stream_writer
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, tools_condition

from news_fetcher import fetch_yahoo_news
from rag_storage import search_private_knowledge


SYSTEM_PROMPT = """
你是一名市场投研助手。

你的工作方式必须遵守以下规则：
1. 优先使用工具获取依据，再给出结论。
2. 对于市场新闻相关问题，优先调用 `get_market_news`。
3. 对于交易原则、私有知识库、风控规则相关问题，优先调用 `search_trading_rules`。
4. 如果工具返回的信息不足，必须明确说明依据不足，不要编造。
5. 最终回答必须使用中文，并严格使用以下结构：
   1. 市场解读
   2. 匹配到的交易原则
   3. 操作建议
   4. 风险提示与失效条件
6. 你可以输出简短、清晰、可执行的结论，但不要暴露原始内部推理。
7. 当你需要更多依据时，继续调用工具；当依据足够时，直接给出最终回答。
8. 调用 `get_market_news` 时，只能传入单个股票代码，例如 `TSLA`、`AAPL`、`BRK.B`，不要传关键词、句子或空格分隔的短语。
"""


class MarketAgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    symbol: str


TICKER_PATTERN = re.compile(r"\b[A-Z]{1,5}(?:\.[A-Z]{1,2})?\b")


def _format_news_items(news_items: list[dict]) -> str:
    if not news_items:
        return "No recent market news was found."

    lines = []
    for index, item in enumerate(news_items[:5], start=1):
        title = item.get("title", "Untitled").strip()
        summary = item.get("summary", "").replace("\n", " ").strip()
        date = item.get("date", "Unknown date")
        link = item.get("link", "")
        lines.append(
            f"[News {index}] time={date} | title={title} | summary={summary[:280]} | link={link}"
        )
    return "\n".join(lines)


def _format_rule_documents(documents: list[Document]) -> str:
    if not documents:
        return "No matching private trading rules were found."

    lines = []
    for index, document in enumerate(documents, start=1):
        file_name = document.metadata.get("file_name", "unknown_file")
        page = document.metadata.get("page")
        locator = f"{file_name} page {page}" if page else file_name
        snippet = document.page_content.replace("\n", " ").strip()
        lines.append(f"[Rule {index}] source={locator} | content={snippet[:500]}")
    return "\n".join(lines)


class MarketRAGService:
    def __init__(self, redis_client):
        self.redis = redis_client
        self.llm = ChatOpenAI(
            model="deepseek-chat",
            api_key=os.environ.get("DEEPSEEK_API_KEY_FOR_Ttth"),
            base_url="https://api.deepseek.com",
            timeout=120.0,
            temperature=0.5,
        )
        self.tools = [
            self._build_news_tool(),
            self._build_rules_tool(),
        ]
        self.tool_node = ToolNode(self.tools)
        self.llm_with_tools = self.llm.bind_tools(self.tools)
        self.graph = self._build_graph()

    def _build_news_tool(self):
        @tool("get_market_news")
        def get_market_news(symbol: str) -> str:
            """Fetch recent market news for a stock ticker."""

            writer = get_stream_writer()
            raw_symbol = str(symbol or "").upper().strip()
            clean_symbol = self._normalize_ticker(raw_symbol)
            if not clean_symbol:
                writer(
                    {
                        "type": "thinking",
                        "content": "未识别出有效股票代码，无法继续查询相关新闻。",
                    }
                )
                return json.dumps(
                    {
                        "summary": "No valid ticker symbol could be extracted.",
                        "news_count": 0,
                        "formatted_context": "No recent market news was found.",
                    },
                    ensure_ascii=False,
                )

            if clean_symbol != raw_symbol:
                writer(
                    {
                        "type": "thinking",
                        "content": f"新闻查询参数已规范化为股票代码 {clean_symbol}。",
                    }
                )

            writer(
                {
                    "type": "thinking",
                    "content": f"正在查询 {clean_symbol} 的最新市场新闻...",
                }
            )

            news_items = self._get_news_items(clean_symbol)
            writer(
                {
                    "type": "thinking",
                    "content": f"已获取 {len(news_items)} 条与 {clean_symbol} 相关的新闻。",
                }
            )
            return json.dumps(
                {
                    "summary": f"Retrieved {len(news_items)} news items for {clean_symbol}.",
                    "news_count": len(news_items),
                    "formatted_context": _format_news_items(news_items),
                },
                ensure_ascii=False,
            )

        return get_market_news

    def _normalize_ticker(self, value: str) -> str:
        text = str(value or "").upper().strip()
        if not text:
            return ""

        if " " not in text and TICKER_PATTERN.fullmatch(text):
            return text

        match = TICKER_PATTERN.search(text)
        return match.group(0) if match else ""

    def _build_rules_tool(self):
        @tool("search_trading_rules")
        def search_trading_rules(question: str) -> str:
            """Search the private trading-rule knowledge base for relevant rules."""

            writer = get_stream_writer()
            writer(
                {
                    "type": "thinking",
                    "content": "正在检索私有交易规则与风控知识库...",
                }
            )

            rule_docs = search_private_knowledge(question, k=4)
            writer(
                {
                    "type": "thinking",
                    "content": f"已命中 {len(rule_docs)} 条相关规则片段。",
                }
            )
            return json.dumps(
                {
                    "summary": f"Matched {len(rule_docs)} private rule snippets.",
                    "rule_count": len(rule_docs),
                    "formatted_context": _format_rule_documents(rule_docs),
                },
                ensure_ascii=False,
            )

        return search_trading_rules

    def _build_graph(self):
        builder = StateGraph(MarketAgentState)
        builder.add_node("agent", self._call_model)
        builder.add_node("tools", self.tool_node)
        builder.add_edge(START, "agent")
        builder.add_conditional_edges(
            "agent",
            tools_condition,
            {
                "tools": "tools",
                "__end__": END,
            },
        )
        builder.add_edge("tools", "agent")
        return builder.compile()

    def _get_news_items(self, symbol: str) -> list[dict]:
        cache_key = f"market:news:{symbol}"
        cached_data = self.redis.get(cache_key)
        if cached_data:
            return json.loads(cached_data)

        news_items = fetch_yahoo_news(symbol, limit=5)
        self.redis.setex(cache_key, 86400, json.dumps(news_items))
        return news_items

    def _call_model(self, state: MarketAgentState):
        writer = get_stream_writer()
        writer(
            {
                "type": "thinking",
                "content": "正在思考...",
            }
        )

        response = self.llm_with_tools.invoke(
            [
                SystemMessage(content=SYSTEM_PROMPT),
                *state["messages"],
            ]
        )
        return {"messages": [response]}

    def stream_events(self, symbol: str, question: str):
        initial_state: MarketAgentState = {
            "symbol": symbol.upper().strip(),
            "messages": [HumanMessage(content=question.strip())],
        }
        final_parts: list[str] = []

        for mode, chunk in self.graph.stream(
            initial_state,
            stream_mode=["custom", "messages"],
        ):
            if mode == "custom":
                yield chunk
                continue

            if mode == "messages":
                message_chunk, metadata = chunk
                if metadata.get("langgraph_node") != "agent":
                    continue

                content = getattr(message_chunk, "content", "")
                if isinstance(content, list):
                    text = "".join(
                        item.get("text", "")
                        for item in content
                        if isinstance(item, dict)
                    )
                else:
                    text = str(content or "")

                if text:
                    final_parts.append(text)

        if final_parts:
            yield {
                "type": "thinking",
                "content": "依据收集完成，正在整理最终结论...",
            }
            yield {"type": "final", "content": "".join(final_parts).strip()}

        yield {"type": "done", "content": ""}

    def analyze(self, symbol: str, question: str) -> dict:
        news_items = self._get_news_items(symbol.upper().strip())
        rule_docs = search_private_knowledge(question.strip(), k=4)

        final_parts: list[str] = []
        for event in self.stream_events(symbol, question):
            if event.get("type") == "final":
                final_parts.append(event.get("content", ""))

        return {
            "reply": "".join(final_parts).strip(),
            "used_news_count": len(news_items),
            "used_rule_count": len(rule_docs),
        }
