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
adb install TongSKY-1.0.41.apk
python3 release1041/qa/fixtures.py seed evidence/seed
adb shell mkdir -p /data/user/0/kr.tong.sky/databases
adb push evidence/seed/. /data/user/0/kr.tong.sky/databases/
uid=$(adb shell stat -c %u /data/user/0/kr.tong.sky | tr -d '\r')
adb shell chown -R "$uid:$uid" /data/user/0/kr.tong.sky/databases
adb shell restorecon -RF /data/user/0/kr.tong.sky
adb shell am start -W -n kr.tong.sky/.MainActivity
sleep 5
adb shell am force-stop kr.tong.sky
adb pull /data/user/0/kr.tong.sky/databases/. evidence/before-upgrade/
python3 release1041/qa/fixtures.py check-reset evidence/before-upgrade
python3 release1041/qa/fixtures.py fresh evidence/before-upgrade
adb push evidence/before-upgrade/. /data/user/0/kr.tong.sky/databases/
adb shell chown -R "$uid:$uid" /data/user/0/kr.tong.sky/databases
adb shell restorecon -RF /data/user/0/kr.tong.sky
adb shell dumpsys package kr.tong.sky > evidence/package-before.txt
first=$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/package-before.txt)
adb install -r TongSKY-1.0.41-r2.apk
adb shell dumpsys package kr.tong.sky > evidence/package-after.txt
grep -m1 'versionCode=10143' evidence/package-after.txt
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
adb logcat -b crash -d > evidence/crash.txt
! grep -q 'Process: kr.tong.sky' evidence/crash.txt
adb install mobile-probe.apk
adb shell am instrument -w kr.tong.sky.mobileqa/.Probe | tee evidence/mobile-probe.txt
grep -q 'PASS: API35 WebView 320css' evidence/mobile-probe.txt
! grep -q 'FAIL:' evidence/mobile-probe.txt
echo 'PASS: signed 10142 -> 10143 upgrade and relaunch preserve new KE records; mobile adapter cases passed' | tee evidence/result.txt
