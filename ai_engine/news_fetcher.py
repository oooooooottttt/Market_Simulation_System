import feedparser
from bs4 import BeautifulSoup
from urllib.parse import quote


def _clean_summary(raw_summary: str) -> str:
    soup = BeautifulSoup(raw_summary or "", "html.parser")
    return " ".join(soup.get_text(" ", strip=True).split())


def fetch_yahoo_news(symbol: str, limit: int = 5) -> list[dict]:
    symbol = symbol.upper().strip()
    encoded_symbol = quote(symbol, safe=".")
    url = f"https://feeds.finance.yahoo.com/rss/2.0/headline?s={encoded_symbol}&region=US&lang=en-US"
    feed = feedparser.parse(url)

    if not feed.entries:
        return []

    news_list = []
    for entry in feed.entries[:limit]:
        news_list.append(
            {
                "title": getattr(entry, "title", "").strip(),
                "date": getattr(entry, "published", "Unknown date"),
                "summary": _clean_summary(getattr(entry, "summary", "")),
                "link": getattr(entry, "link", ""),
            }
        )

    return news_list
