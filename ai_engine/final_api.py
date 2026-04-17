import json
import os

import redis
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, model_validator

from news_fetcher import fetch_yahoo_news
from rag_service import MarketRAGService
from rag_storage import (
    build_private_knowledge_base,
    clear_private_knowledge_base,
    get_private_knowledge_status,
)


os.environ.setdefault("HF_HOME", r"D:\AI_Models_Cache\huggingface")

app = FastAPI(title="Market AI Engine")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

r = redis.Redis(host="localhost", port=6379, db=0, decode_responses=True)
rag_service = MarketRAGService(redis_client=r)


class ChatReq(BaseModel):
    model_config = ConfigDict(extra="ignore")
    symbol: str
    question: str

    @model_validator(mode="before")
    @classmethod
    def normalize_question_field(cls, data):
        if not isinstance(data, dict):
            return data

        normalized = dict(data)
        if not normalized.get("question") and normalized.get("message"):
            normalized["question"] = normalized["message"]
        return normalized


class KnowledgeBuildReq(BaseModel):
    input_dir: str | None = None
    recreate: bool = True


def _sse_payload(payload: dict) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    raw_body = (await request.body()).decode("utf-8", errors="ignore")
    print(
        "[validation-error]",
        json.dumps(
            {
                "path": str(request.url.path),
                "method": request.method,
                "errors": exc.errors(),
                "body": raw_body,
            },
            ensure_ascii=False,
        ),
    )
    return JSONResponse(
        status_code=422,
        content={
            "status": "error",
            "message": "Invalid request payload.",
            "errors": exc.errors(),
        },
    )


@app.get("/api/news/{symbol}")
async def get_raw_news(symbol: str):
    symbol = symbol.upper().strip()
    cache_key = f"market:news:{symbol}"
    cached_data = r.get(cache_key)
    if cached_data:
        return {"status": "success", "data": json.loads(cached_data), "source": "redis"}

    news_list = fetch_yahoo_news(symbol, limit=10)
    r.setex(cache_key, 86400, json.dumps(news_list))
    return {"status": "success", "data": news_list, "source": "api"}


@app.get("/api/knowledge/status")
async def knowledge_status():
    return {"status": "success", "data": get_private_knowledge_status()}


@app.post("/api/knowledge/rebuild")
async def rebuild_knowledge_base(req: KnowledgeBuildReq):
    try:
        result = build_private_knowledge_base(input_dir=req.input_dir, recreate=req.recreate)
        return {"status": "success", "data": result}
    except Exception as exc:
        return {"status": "error", "message": str(exc)}


@app.post("/api/knowledge/clear")
async def clear_knowledge_base():
    try:
        clear_private_knowledge_base()
        return {"status": "success", "message": "Private knowledge base cleared."}
    except Exception as exc:
        return {"status": "error", "message": str(exc)}


@app.post("/api/ai/chat")
async def chat_with_rag(req: ChatReq):
    try:
        result = rag_service.analyze(symbol=req.symbol, question=req.question)
        return {
            "reply": result["reply"],
            "used_news_count": result["used_news_count"],
            "used_rule_count": result["used_rule_count"],
        }
    except Exception as exc:
        return {
            "reply": f"RAG request failed: {exc}",
            "used_news_count": 0,
            "used_rule_count": 0,
        }


@app.post("/api/ai/chat/stream")
async def chat_with_rag_stream(req: ChatReq):
    def event_stream():
        try:
            for event in rag_service.stream_events(symbol=req.symbol, question=req.question):
                yield _sse_payload(event)
        except Exception as exc:
            yield _sse_payload({"type": "error", "content": f"RAG request failed: {exc}"})
            yield _sse_payload({"type": "done", "content": ""})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# Start command:
# uvicorn final_api:app --reload --port 5000
