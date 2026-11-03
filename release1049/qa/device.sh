#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p evidence/seed evidence/before-upgrade evidence/after-upgrade evidence/relaunch
adb root
adb wait-for-device
adb shell wm dismiss-keyguard
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
adb install TongSKY-1.0.42.apk
python3 release1041/qa/fixtures.py seed evidence/seed
adb shell mkdir -p /data/user/0/kr.tong.sky/databases /data/user/0/kr.tong.sky/shared_prefs
# Isolate reset/upgrade preservation from normal startup imports, including
# the initial published 10142 launch; the signed production APK is unchanged.
cat > evidence/offline-settings.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><boolean name="testOffline" value="true" /></map>
XML
adb push evidence/offline-settings.xml /data/user/0/kr.tong.sky/shared_prefs/settings.xml
adb push evidence/seed/. /data/user/0/kr.tong.sky/databases/
uid=$(adb shell stat -c %u /data/user/0/kr.tong.sky | tr -d '\r')
adb shell chown -R "$uid:$uid" /data/user/0/kr.tong.sky/databases /data/user/0/kr.tong.sky/shared_prefs
adb shell restorecon -RF /data/user/0/kr.tong.sky
adb shell am start -W -n kr.tong.sky/.MainActivity
sleep 5
adb shell am force-stop kr.tong.sky
adb pull /data/user/0/kr.tong.sky/databases/. evidence/before-upgrade/
python3 release1041/qa/fixtures.py check-reset evidence/before-upgrade
python3 release1041/qa/fixtures.py fresh evidence/before-upgrade
python3 - <<'PY'
import sqlite3,json
from pathlib import Path
root=Path('evidence/before-upgrade')
with sqlite3.connect(root/'tong-sky-asiana.db') as oz:
    data=json.loads(oz.execute('SELECT data FROM snapshots LIMIT 1').fetchone()[0])
data['carrier']='KE'
with sqlite3.connect(root/'tong-sky.db') as ke:
    ke.execute('INSERT INTO snapshots VALUES(?,?,?,?)',('ICN-JFK-2027-01',json.dumps(data),None,2))
for path in root.glob('*.db'):
    db=sqlite3.connect(path);db.execute('PRAGMA wal_checkpoint(TRUNCATE)');db.close()
PY
# The isolated test app is stopped. Replace the complete checkpointed fixture,
# including its journals, so an old device WAL cannot replay over new seed rows.
adb shell rm -rf /data/user/0/kr.tong.sky/databases
adb shell mkdir -p /data/user/0/kr.tong.sky/databases
adb push evidence/before-upgrade/. /data/user/0/kr.tong.sky/databases/
adb shell chown -R "$uid:$uid" /data/user/0/kr.tong.sky/databases
adb shell restorecon -RF /data/user/0/kr.tong.sky
mkdir -p evidence/seed-roundtrip
adb pull /data/user/0/kr.tong.sky/databases/. evidence/seed-roundtrip/
python3 release1041/qa/fixtures.py check-fresh evidence/seed-roundtrip
adb pull /data/user/0/kr.tong.sky/shared_prefs/calendar-reset.xml evidence/reset-before.xml
grep -q 'KE-20261103-1041' evidence/reset-before.xml
adb shell dumpsys package kr.tong.sky > evidence/package-before.txt
first=$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/package-before.txt)
adb install -r TongSKY-1.0.49.apk
adb shell dumpsys package kr.tong.sky > evidence/package-after.txt
grep -m1 'versionCode=10153' evidence/package-after.txt
test "$first" = "$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/package-after.txt)"
adb logcat -c
adb shell am start -W -n kr.tong.sky/.MainActivity
sleep 5
adb shell pidof kr.tong.sky
adb shell am force-stop kr.tong.sky
adb pull /data/user/0/kr.tong.sky/databases/. evidence/after-upgrade/
python3 release1041/qa/fixtures.py check-fresh evidence/after-upgrade
adb pull /data/user/0/kr.tong.sky/shared_prefs/calendar-reset.xml evidence/reset-marker.xml
grep -q 'KE-20261103-1041' evidence/reset-marker.xml
adb shell am start -W -n kr.tong.sky/.MainActivity
sleep 3
adb shell am force-stop kr.tong.sky
adb pull /data/user/0/kr.tong.sky/databases/. evidence/relaunch/
python3 release1041/qa/fixtures.py check-fresh evidence/relaunch
python3 - <<'PY'
import sqlite3
from pathlib import Path
for folder in ['after-upgrade','relaunch']:
    with sqlite3.connect(Path('evidence')/folder/'tong-sky.db') as ke:
        assert ke.execute("SELECT COUNT(*) FROM snapshots WHERE k='ICN-JFK-2027-01'").fetchone()[0]==1
    with sqlite3.connect(Path('evidence')/folder/'tong-sky-asiana.db') as oz, sqlite3.connect(Path('evidence/before-upgrade/tong-sky-asiana.db')) as before:
        for table in ['snapshots','errors','shared_outbox','batches','watches']:
            expected=set(before.execute('SELECT * FROM '+table).fetchall())
            actual=set(oz.execute('SELECT * FROM '+table).fetchall())
            assert expected.issubset(actual),(folder,table,'original rows changed or removed')
            print('RETAINED',folder,table,len(expected),'original rows; total',len(actual))
print('PASS: fresh KE calendar and OZ snapshots, work and settings survive upgrade and relaunch')
PY
# Open the real non-exported Activity as the emulator root test user.
for carrier in false true; do
  adb shell am start -W -n kr.tong.sky/.TripSearchActivity --ez oz "$carrier" --es origin ICN
  sleep 3
  adb shell uiautomator dump /sdcard/trip-layout.xml
  adb pull /sdcard/trip-layout.xml "evidence/trip-$carrier.xml"
  adb shell screencap -p /sdcard/trip-screen.png
  adb pull /sdcard/trip-screen.png "evidence/trip-$carrier.png"
  adb shell am force-stop kr.tong.sky
done
python3 - <<'CHECK'
from pathlib import Path
import xml.etree.ElementTree as ET
for carrier in ['false','true']:
    root=ET.parse(Path('evidence')/('trip-'+carrier+'.xml')).getroot()
    texts=[n.get('text','') for n in root.iter('node')]
    content=' '.join(texts)
    assert ('아시아나' if carrier=='true' else '대한항공')+' 추천일정' in content,content
    for label in ['대륙·지역','여행 일수','보너스 왕복','보너스＋승급','승급＋보너스']:
        assert label in content,(label,content)
    assert '불러오지 못했습니다' not in content,content
    assert '조회 시작일' not in content and '조회 종료일' not in content
print('TRIP_DEVICE_PASS: both carriers, two inputs, three categories, no loading error')
CHECK
adb logcat -b crash -d > evidence/crash.txt
! grep -q 'Process: kr.tong.sky' evidence/crash.txt
adb install mobile-probe.apk
adb shell am instrument -w kr.tong.sky.mobileqa/.Probe | tee evidence/mobile-probe.txt
grep -q 'PASS: API35 WebView 320css' evidence/mobile-probe.txt
! grep -q 'FAIL:' evidence/mobile-probe.txt
adb logcat -d -s System.out:I > evidence/mobile-probe-detail.txt
grep -q 'MOBILE_REUSE_PASS: 24 cases / 139 assertions / 320 CSS px' evidence/mobile-probe.txt
echo 'PASS: signed 10142 -> 10153 upgrade and relaunch preserve new KE records; mobile adapter cases passed' | tee evidence/result.txt
