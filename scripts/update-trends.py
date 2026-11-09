"""Fetch Korea's Google Trending Now feed without third-party proxies."""
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

SOURCE = 'https://trends.google.com/trending?geo=KR&hours=24'
RSS = 'https://trends.google.com/trending/rss?geo=KR'
def parse_feed(raw):
    root = ET.fromstring(raw)
    items, seen = [], set()
    for node in root.findall('./channel/item'):
        title = (node.findtext('title') or '').strip()
        if not title or title in seen:
            continue
        seen.add(title)
        traffic = node.findtext('{https://trends.google.com/trending/rss}approx_traffic') or ''
        items.append({'title': title, 'traffic': traffic, 'startedAt': node.findtext('pubDate') or ''})
    if not items:
        raise ValueError('Empty Google Trends feed; preserve previous successful data')
    # Preserve Google's feed order: this is not a total search-volume ranking.
    return items[:20]

def parse_page(raw):
    match = re.search(r"AF_initDataCallback\(\{key: 'ds:0',.*?data:", raw, re.S)
    if not match:
        raise ValueError('Google page structure changed')
    data, _ = json.JSONDecoder().raw_decode(raw[match.end():].lstrip())
    rows = data[1]
    if not isinstance(rows, list):
        raise ValueError('Invalid Google rows')
    items, seen = [], set()
    for row in rows:
        if not isinstance(row, list) or len(row) < 7 or row[2] != 'KR' or not isinstance(row[0], str):
            raise ValueError('Unexpected trend structure')
        title = row[0].strip()
        if title and title not in seen:
            seen.add(title)
            items.append({'title': title, 'traffic': str(row[6] or ''), 'startedAt': ''})
    if not items:
        raise ValueError('Empty trending page')
    return items[:20]

def fetch(url):
    with urlopen(Request(url, headers={'User-Agent': 'TongSeries-Trends/1.0'}), timeout=30) as response:
        return response.read()

if __name__ == '__main__':
    source = SOURCE
    try:
        items = parse_page(fetch(SOURCE).decode('utf-8'))
    except Exception as error:
        print('Trending page unavailable; trying official RSS:', type(error).__name__)
        source = RSS
        items = parse_feed(fetch(RSS))
    payload = {'source': source, 'country': 'KR', 'updatedAt': datetime.now(timezone.utc).isoformat(), 'items': items}
    target = Path(__file__).resolve().parent.parent / 'trends.json'
    temporary = target.with_suffix('.tmp')
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(target)
    print(f'Updated {len(items)} Korean trends')
