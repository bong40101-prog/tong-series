"""Official Google KR trends: whole pool -> category -> at most 20. Never pad."""
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET
SOURCE = 'https://trends.google.com/trending?geo=KR&hours=24&hl=ko'
WEEK_SOURCE = 'https://trends.google.com/trending?geo=KR&hours=168&hl=ko'
RSS = 'https://trends.google.com/trending/rss?geo=KR'
GROUPS = [
    ('all', '전체', None), ('food', '음식', {5}), ('people', '사람·연예', {4}),
    ('camping', '캠핑', set()), ('cars', '차량', {1}), ('travel', '여행', {19}),
    ('finance', '경제·금융', {3}), ('tech', 'IT·과학', {15,18}),
    ('sports', '스포츠', {17}), ('health', '건강', {7})
]
CAMPING = re.compile(r'캠핑|글램핑|차박|텐트|야영|캠핑장|백패킹|오토캠핑|\bcamping\b|\bglamping\b|\bcampsite\b|\bbackpacking\b', re.I)

def parse_page(raw):
    match = re.search(r"AF_initDataCallback\(\{key: 'ds:0',.*?data:", raw, re.S)
    if not match: raise ValueError('Google page structure changed')
    data, _ = json.JSONDecoder().raw_decode(raw[match.end():].lstrip())
    if not isinstance(data, list) or len(data)<2 or not isinstance(data[1], list): raise ValueError('Invalid Google rows')
    items, seen = [], set()
    for row in data[1]:
        if not isinstance(row,list) or len(row)<11 or row[2]!='KR' or not isinstance(row[0],str) or not isinstance(row[10],list):
            raise ValueError('Unexpected trend structure')
        title=row[0].strip()
        if not title or title in seen: continue
        seen.add(title)
        related=[x for x in (row[9] if isinstance(row[9],list) else []) if isinstance(x,str)]
        categories=[x for x in row[10] if type(x) is int]
        if len(categories)!=len(row[10]): raise ValueError('Unexpected categories')
        items.append({'title':title,'traffic':str(row[6] or ''),'categoryIds':categories,'related':related})
    if not items: raise ValueError('Empty page; preserve successful data')
    return items

def parse_feed(raw):
    root=ET.fromstring(raw);items=[];seen=set()
    for node in root.findall('./channel/item'):
        title=(node.findtext('title') or '').strip()
        if title and title not in seen:
            seen.add(title)
            items.append({'title':title,'traffic':node.findtext('{https://trends.google.com/trending/rss}approx_traffic') or ''})
    if not items: raise ValueError('Empty feed')
    return items

def select(items, ident, ids):
    if ident=='all': return items
    if ident=='camping': return [x for x in items if CAMPING.search(' '.join([x['title']]+x.get('related',[])))]
    return [x for x in items if set(x.get('categoryIds',[])) & ids]

def build_payload(day_items, week_items, previous=None, now=None, day_source=SOURCE):
    now=now or datetime.now(timezone.utc).isoformat()
    prior={x['id']:x for x in (previous or {}).get('groups',[]) if isinstance(x,dict) and 'id' in x}
    groups=[]
    for ident,label,ids in GROUPS:
        pool=day_items if ident=='all' else week_items
        hours=24 if ident=='all' else 168
        if pool is None:
            if ident in prior:
                groups.append({**prior[ident],'refreshFailed':True});continue
            groups.append({'id':ident,'label':label,'windowHours':hours,'items':[],'updatedAt':None,'status':'unavailable'});continue
        matching=select(pool,ident,ids)
        groups.append({'id':ident,'label':label,'windowHours':hours,'source':day_source if ident=='all' else WEEK_SOURCE,
            'method':'related_keyword_filter' if ident=='camping' else ('google_feed_order' if ident=='all' else 'google_category'),
            'googleCategoryIds':sorted(ids or []),'updatedAt':now,'status':'ok','refreshFailed':False,
            'totalAvailable':len(matching),'items':[{'title':x['title'],'traffic':x['traffic']} for x in matching[:20]]})
    all_group=groups[0]
    return {'schema':2,'country':'KR','source':all_group.get('source',SOURCE),'updatedAt':all_group.get('updatedAt'),
            'items':all_group['items'],'groups':groups}

def fetch(url):
    with urlopen(Request(url,headers={'User-Agent':'TongSeries-Trends/2.0'}),timeout=25) as response:
        raw=response.read(8_000_001)
        if len(raw)>8_000_000:raise ValueError('Response too large')
        return raw

def update(target,fetcher=fetch):
    try: previous=json.loads(target.read_text(encoding='utf-8'))
    except (OSError,ValueError): previous={}
    day=week=None;day_source=SOURCE
    try: day=parse_page(fetcher(SOURCE).decode('utf-8'))
    except Exception as error:
        print('24h page unavailable:',type(error).__name__)
        try:day=parse_feed(fetcher(RSS));day_source=RSS
        except Exception as error:print('RSS unavailable:',type(error).__name__)
    try:week=parse_page(fetcher(WEEK_SOURCE).decode('utf-8'))
    except Exception as error:print('7d page unavailable:',type(error).__name__)
    if day is None and week is None:raise RuntimeError('All sources failed; previous file unchanged')
    payload=build_payload(day,week,previous,day_source=day_source)
    temporary=target.with_suffix('.tmp');temporary.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');temporary.replace(target)
    print('Updated categories:',', '.join(f"{g['label']} {len(g['items'])}" for g in payload['groups']))

if __name__=='__main__':update(Path(__file__).resolve().parent.parent/'trends.json')
