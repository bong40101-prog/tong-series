#!/usr/bin/env bash
set -euo pipefail
p=kr.tong.sky
mkdir -p qa-result
trap 'adb logcat -d -b crash > qa-result/crash.txt || true' EXIT
adb wait-for-device
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell wm dismiss-keyguard
adb install -g TongSKY-1.0.40.apk
adb shell dumpsys package "$p" > qa-result/before.txt
grep -q 'versionCode=10140 ' qa-result/before.txt
adb shell am start -W -n "$p/.MainActivity" > qa-result/start-before.txt
grep -q 'Status: ok' qa-result/start-before.txt
first=$(sed -n 's/^[[:space:]]*firstInstallTime=//p' qa-result/before.txt | head -1 | tr -d '\r')
test -n "$first"
uid0=$(adb shell cmd package list packages -U "$p" | tr -d '\r')
sleep 2
adb install -r -g TongSKY-1.0.40-r2.apk
adb shell dumpsys package "$p" > qa-result/after.txt
grep -q 'versionCode=10141 ' qa-result/after.txt
second=$(sed -n 's/^[[:space:]]*firstInstallTime=//p' qa-result/after.txt | head -1 | tr -d '\r')
test "$first" = "$second"
test "$uid0" = "$(adb shell cmd package list packages -U "$p" | tr -d '\r')"
for i in 1 2; do
  adb shell am force-stop "$p"
  adb shell am start -W -n "$p/.MainActivity" > "qa-result/start-$i.txt"
  grep -q 'Status: ok' "qa-result/start-$i.txt"
  sleep 5
  adb shell pidof "$p" | grep -q '[0-9]'
  adb shell uiautomator dump /sdcard/sky-qa.xml >/dev/null
  adb pull /sdcard/sky-qa.xml "qa-result/ui-$i.xml" >/dev/null
  grep -q 'package="kr.tong.sky"' "qa-result/ui-$i.xml"
done
adb logcat -d -b crash > qa-result/crash.txt
if grep -qE 'Process: kr\.tong\.sky([,:]|$)' qa-result/crash.txt; then exit 1; fi
printf '%s\n' 'API35 offline: upgrade 10140 to 10141 without uninstall; UID and firstInstallTime retained; two candidate launches with visible native UI; no package crash in crash buffer.' | tee qa-result/result.txt
