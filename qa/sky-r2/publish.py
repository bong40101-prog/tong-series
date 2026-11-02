from pathlib import Path
import hashlib,re,sys,shutil
root=Path(sys.argv[1]); index=root/'index.html'; old=index.read_text()
expected='e97e2e19954ebebe87790861819b5bb6f53c0a8571926b3a386b7364a0d08efe'
target='049b66eadb0776bd67c269f60b5bef8b66e60b745f108627303996104cb9e601'
name='TongSKY-1.0.40-r2.apk'; apk=Path(name); assert hashlib.sha256(apk.read_bytes()).hexdigest()==target
assert hashlib.sha256((root/'TongSKY-1.0.40.apk').read_bytes()).hexdigest()==expected
expr=r'<article\b[^>]*\bid="tongsky"[^>]*>.*?</article>'
cards=re.findall(expr,old,re.S); assert len(cards)==1
if target in cards[0] and f'href="{name}"' in cards[0] and (root/name).is_file():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==target
    sys.exit(0)
assert expected in cards[0] and '10140' in cards[0] and 'href="TongSKY-1.0.40.apk"' in cards[0], 'A newer or unexpected website release exists; do not overwrite.'
card='''<article class="app" id="tongsky"><div class="heading"><span class="icon" aria-hidden="true">SKY</span><div><h2>통SKY</h2><div class="tag">1.0.40 수정판(r2) · Android API 33 이상</div></div></div><p class="desc">대한항공·아시아나 마일리지 달력 조회와 저장 결과 확인</p><p class="tag">이번 수정</p><ul class="features"><li>대한항공: 밴쿠버·토론토 왕복을 추가해 미주·유럽 등록 공항 26곳의 양방향을 판정합니다.</li><li>한 달의 같은 요일에 세 표식이 매번 반복될 때만 비운항으로 추정합니다. 일부만 일치하면 원래 표식을 유지하고 확인필요로 표시합니다.</li><li>아시아나: 좌석 없음과 비운항을 구분합니다. 항공편 자료와 비운항 안내가 충돌하면 저장하지 않습니다.</li></ul><p class="notice">기존 앱을 삭제하지 않고 업데이트할 수 있습니다. 앱의 표시 버전은 1.0.40, 버전 코드는 10141입니다. 실제 운항을 보장하지 않으며 통SKY 홈페이지 검색은 점검 중입니다.</p><a class="download" href="TongSKY-1.0.40-r2.apk" download="TongSKY-1.0.40-r2.apk">통SKY 수정판 APK 다운로드</a><details><summary>파일 정보</summary><p>버전 코드 10141 · kr.tong.sky · 3,998,587바이트</p><p class="hash">SHA256: HASH</p></details></article>'''.replace('HASH',target)
shutil.copyfile(apk,root/name); index.write_text(old.replace(cards[0],card,1))
