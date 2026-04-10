import json
import os

from langchain_core.documents import Document
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import RunnableLambda
from openai import OpenAI

from news_fetcher import fetch_yahoo_news
from rag_storage import search_private_knowledge


ANALYSIS_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            (
                "你是一名纪律严明的市场投研助手。"
                "你必须同时参考实时市场新闻和用户私有交易原则来回答问题。"
                "当私有交易原则与新闻同时存在时，优先用交易原则约束结论。"
                "如果私有知识库没有命中，请明确说明，不要编造原则。"
                "回答必须使用中文，表达简洁、专业、直接，结论要可执行。"
            ),
        ),
        (
            "human",
            (
                "股票代码：{symbol}\n"
                "用户问题：{question}\n\n"
                "实时市场新闻：\n{news_context}\n\n"
                "私有交易原则：\n{rules_context}\n\n"
                "请严格按以下结构回答：\n"
                "1. 市场解读\n"
                "2. 匹配到的交易原则\n"
                "3. 操作建议\n"
                "4. 风险提示与失效条件"
            ),
        ),
    ]
)


def _to_openai_messages(messages) -> list[dict]:
    role_map = {
        "human": "user",
        "ai": "assistant",
        "system": "system",
        "tool": "tool",
    }
    return [
        {"role": role_map.get(message.type, message.type), "content": message.content}
        for message in messages
    ]


def _format_news_items(news_items: list[dict]) -> str:
    if not news_items:
        return "当前没有获取到该股票的近期新闻。"

    lines = []
    for index, item in enumerate(news_items[:5], start=1):
        title = item.get("title", "Untitled").strip()
        summary = item.get("summary", "").replace("\n", " ").strip()
        date = item.get("date", "未知时间")
        link = item.get("link", "")
        lines.append(
            f"[新闻{index}] 时间={date} | 标题={title} | 摘要={summary[:280]} | 链接={link}"
        )
    return "\n".join(lines)


def _format_rule_documents(documents: list[Document]) -> str:
    if not documents:
        return "私有知识库当前没有检索到相关交易原则。"

    lines = []
    for index, document in enumerate(documents, start=1):
        file_name = document.metadata.get("file_name", "未知文件")
        page = document.metadata.get("page")
        locator = f"{file_name} 第{page}页" if page else file_name
        snippet = document.page_content.replace("\n", " ").strip()
        lines.append(f"[原则{index}] 来源={locator} | 内容={snippet[:500]}")
    return "\n".join(lines)


class MarketRAGService:
    def __init__(self, redis_client):
        self.redis = redis_client
        self.client = OpenAI(
            api_key=os.environ.get("DEEPSEEK_API_KEY_FOR_Ttth"),
            base_url="https://api.deepseek.com",
            timeout=120.0,
        )
        self.chain = RunnableLambda(self._collect_context) | RunnableLambda(self._generate_answer)

    def _get_news_items(self, symbol: str) -> list[dict]:
        cache_key = f"market:news:{symbol}"
        cached_data = self.redis.get(cache_key)
        if cached_data:
            return json.loads(cached_data)

        news_items = fetch_yahoo_news(symbol, limit=5)
        self.redis.setex(cache_key, 86400, json.dumps(news_items))
        return news_items

    def _collect_context(self, payload: dict) -> dict:
        symbol = payload["symbol"].upper().strip()
        question = payload["question"].strip()
        news_items = self._get_news_items(symbol)
        rule_docs = search_private_knowledge(question, k=4)

        return {
            "symbol": symbol,
            "question": question,
            "news_items": news_items,
            "rule_docs": rule_docs,
            "news_context": _format_news_items(news_items),
            "rules_context": _format_rule_documents(rule_docs),
        }

    def _generate_answer(self, payload: dict) -> dict:
        messages = ANALYSIS_PROMPT.format_messages(
            symbol=payload["symbol"],
            question=payload["question"],
            news_context=payload["news_context"],
            rules_context=payload["rules_context"],
        )

        completion = self.client.chat.completions.create(
            model="deepseek-chat",
            messages=_to_openai_messages(messages),
            stream=False,
        )

        return {
            "reply": completion.choices[0].message.content,
            "used_news_count": len(payload["news_items"]),
            "used_rule_count": len(payload["rule_docs"]),
        }

    def analyze(self, symbol: str, question: str) -> dict:
        return self.chain.invoke({"symbol": symbol, "question": question})
