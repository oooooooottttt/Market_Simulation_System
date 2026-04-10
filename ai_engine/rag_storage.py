import os
import re
import shutil
from functools import lru_cache
from pathlib import Path

from langchain_core.documents import Document
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import Chroma


HF_CACHE_HOME = r"D:\AI_Models_Cache\huggingface" #模型缓存路径
PRIVATE_KNOWLEDGE_DIR = Path("./private_knowledge")
KNOWLEDGE_DB_DIR = Path("./knowledge_db")
COLLECTION_NAME = "trading_rules"
SUPPORTED_FILE_TYPES = {".txt", ".md", ".pdf"}


os.environ.setdefault("HF_HOME", HF_CACHE_HOME)
os.environ.setdefault("TRANSFORMERS_CACHE", HF_CACHE_HOME)

_vector_store = None


def ensure_knowledge_directories() -> None:
    PRIVATE_KNOWLEDGE_DIR.mkdir(parents=True, exist_ok=True)


def clean_text(text: str) -> str:
    cleaned = text.replace("\u3000", " ")
    cleaned = cleaned.replace("\xa0", " ")
    cleaned = re.sub(r"\r\n?", "\n", cleaned)
    cleaned = re.sub(r"[ \t]+", " ", cleaned)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def _build_metadata(path: Path, extra: dict | None = None) -> dict:
    metadata = {
        "source_file": str(path.resolve()),
        "file_name": path.name,
        "file_type": path.suffix.lower(),
        "knowledge_type": "trading_rules",
    }
    if extra:
        metadata.update(extra)
    return metadata


def _load_text_file(path: Path) -> list[Document]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    text = clean_text(text)
    if not text:
        return []
    return [Document(page_content=text, metadata=_build_metadata(path))]


def _load_pdf_file(path: Path) -> list[Document]:
    try:
        from langchain_community.document_loaders import PyPDFLoader
    except Exception as exc:
        raise RuntimeError(
            "PDF support requires an available PDF parser. Install `pypdf` in the project venv before importing PDF files."
        ) from exc

    try:
        pages = PyPDFLoader(str(path)).load()
    except Exception as exc:
        raise RuntimeError(
            f"Failed to read PDF file `{path.name}`. Make sure `pypdf` is installed and the file is not encrypted."
        ) from exc

    documents = []
    for index, page in enumerate(pages, start=1):
        page_text = clean_text(page.page_content)
        if not page_text:
            continue
        documents.append(
            Document(
                page_content=page_text,
                metadata=_build_metadata(path, {"page": index}),
            )
        )
    return documents


def load_private_documents(input_dir: str | Path | None = None) -> list[Document]:
    ensure_knowledge_directories()
    root = Path(input_dir) if input_dir else PRIVATE_KNOWLEDGE_DIR
    if not root.exists():
        raise FileNotFoundError(f"Knowledge input directory does not exist: {root}")

    files = sorted(
        path
        for path in root.rglob("*")
        if path.is_file()
        and path.suffix.lower() in SUPPORTED_FILE_TYPES
        and not path.name.lower().startswith("readme")
    )
    if not files:
        raise FileNotFoundError(
            f"No supported knowledge files were found under `{root}`. Supported types: {sorted(SUPPORTED_FILE_TYPES)}"
        )

    documents: list[Document] = []
    for path in files:
        suffix = path.suffix.lower()
        if suffix in {".txt", ".md"}:
            documents.extend(_load_text_file(path))
        elif suffix == ".pdf":
            documents.extend(_load_pdf_file(path))

    if not documents:
        raise ValueError("Knowledge files were found, but no usable text content could be extracted.")
    return documents


def split_documents(documents: list[Document]) -> list[Document]:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=900,
        chunk_overlap=150,
        separators=["\n\n", "\n", ".", "!", "?", ";", " ", ""],
    )
    return splitter.split_documents(documents)


@lru_cache(maxsize=1)
def get_embeddings() -> HuggingFaceEmbeddings:
    return HuggingFaceEmbeddings(model_name="all-MiniLM-L6-v2")


def clear_private_knowledge_base() -> None:
    global _vector_store

    if KNOWLEDGE_DB_DIR.exists():
        shutil.rmtree(KNOWLEDGE_DB_DIR)
    _vector_store = None


def build_private_knowledge_base(input_dir: str | Path | None = None, recreate: bool = True) -> dict:
    global _vector_store

    documents = load_private_documents(input_dir)
    chunks = split_documents(documents)
    indexed_files = sorted({doc.metadata.get("file_name", "") for doc in documents if doc.metadata.get("file_name")})

    if recreate:
        clear_private_knowledge_base()

    _vector_store = Chroma.from_documents(
        documents=chunks,
        embedding=get_embeddings(),
        persist_directory=str(KNOWLEDGE_DB_DIR),
        collection_name=COLLECTION_NAME,
    )

    return {
        "input_dir": str(Path(input_dir) if input_dir else PRIVATE_KNOWLEDGE_DIR),
        "source_documents": len(documents),
        "chunks": len(chunks),
        "persist_directory": str(KNOWLEDGE_DB_DIR.resolve()),
        "supported_types": sorted(SUPPORTED_FILE_TYPES),
        "indexed_files": indexed_files,
    }


def get_vector_store() -> Chroma | None:
    global _vector_store

    if _vector_store is not None:
        return _vector_store

    if not KNOWLEDGE_DB_DIR.exists():
        return None

    _vector_store = Chroma(
        persist_directory=str(KNOWLEDGE_DB_DIR),
        embedding_function=get_embeddings(),
        collection_name=COLLECTION_NAME,
    )
    return _vector_store


def search_private_knowledge(query: str, k: int = 4) -> list[Document]:
    vector_store = get_vector_store()
    if vector_store is None:
        return []

    retriever = vector_store.as_retriever(
        search_type="mmr",
        search_kwargs={"k": k, "fetch_k": max(k * 3, 12)},
    )
    return retriever.invoke(query)


def get_private_knowledge_status() -> dict:
    ensure_knowledge_directories()
    vector_store = get_vector_store()
    raw_files = sorted(
        str(path.resolve())
        for path in PRIVATE_KNOWLEDGE_DIR.rglob("*")
        if path.is_file()
        and path.suffix.lower() in SUPPORTED_FILE_TYPES
        and not path.name.lower().startswith("readme")
    )

    chunk_count = 0
    if vector_store is not None:
        try:
            chunk_count = vector_store._collection.count()
        except Exception:
            chunk_count = 0

    return {
        "knowledge_dir": str(PRIVATE_KNOWLEDGE_DIR.resolve()),
        "db_dir": str(KNOWLEDGE_DB_DIR.resolve()),
        "db_exists": KNOWLEDGE_DB_DIR.exists(),
        "supported_types": sorted(SUPPORTED_FILE_TYPES),
        "source_files": raw_files,
        "indexed_file_names": [Path(path).name for path in raw_files],
        "chunk_count": chunk_count,
    }


if __name__ == "__main__":
    print(get_private_knowledge_status())
