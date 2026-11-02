#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p evidence/seed evidence/after-reset evidence/after-relaunch
adb root
adb wait-for-device
adb shell wm dismiss-keyguard
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
adb install TongSKY-1.0.40-r2.apk
adb shell dumpsys package kr.tong.sky > evidence/before-package.txt
first=$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/before-package.txt)
test -n "$first"
python3 release1041/qa/fixtures.py seed evidence/seed
adb shell mkdir -p /data/user/0/kr.tong.sky/databases /data/user/0/kr.tong.sky/shared_prefs
adb push evidence/seed/. /data/user/0/kr.tong.sky/databases/
uid=$(adb shell stat -c %u /data/user/0/kr.tong.sky | tr -d '\r')
adb shell chown -R "$uid:$uid" /data/user/0/kr.tong.sky/databases
adb shell restorecon -RF /data/user/0/kr.tong.sky
adb install -r TongSKY-1.0.41.apk
adb shell dumpsys package kr.tong.sky > evidence/after-package.txt
grep -m1 'versionCode=10142' evidence/after-package.txt
test "$first" = "$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/after-package.txt)"
adb logcat -c
adb shell am start -W -n kr.tong.sky/.MainActivity | tee evidence/start.txt
grep -q 'Status: ok' evidence/start.txt
sleep 5
adb shell pidof kr.tong.sky
adb shell am force-stop kr.tong.sky
adb pull /data/user/0/kr.tong.sky/databases/. evidence/after-reset/
adb pull /data/user/0/kr.tong.sky/shared_prefs/calendar-reset.xml evidence/reset-marker.xml
grep -q 'KE-20261103-1041' evidence/reset-marker.xml
python3 release1041/qa/fixtures.py check-reset evidence/after-reset | tee evidence/reset-result.txt
python3 release1041/qa/fixtures.py fresh evidence/after-reset
adb push evidence/after-reset/. /data/user/0/kr.tong.sky/databases/
adb shell chown -R "$uid:$uid" /data/user/0/kr.tong.sky/databases
adb shell restorecon -RF /data/user/0/kr.tong.sky
adb shell am start -W -n kr.tong.sky/.MainActivity | tee evidence/restart.txt
grep -q 'Status: ok' evidence/restart.txt
sleep 5
adb shell pidof kr.tong.sky
adb shell am force-stop kr.tong.sky
adb pull /data/user/0/kr.tong.sky/databases/. evidence/after-relaunch/
python3 release1041/qa/fixtures.py check-fresh evidence/after-relaunch | tee evidence/relaunch-result.txt
adb logcat -b crash -d > evidence/crash.txt
! grep -q 'Process: kr.tong.sky' evidence/crash.txt
echo 'PASS: installed signed release 10141 -> 10142; KE-only reset; repeat launch preserved data' | tee evidence/result.txt
