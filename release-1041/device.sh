#!/usr/bin/env bash
set -euo pipefail
adb shell svc wifi disable; adb shell svc data disable
adb install --no-streaming TongSKY-1.0.40.apk
p=kr.tong.sky
uid_before=$(adb shell pm list packages -U "$p" | tr -d '\r'); test -n "$uid_before"
adb shell am start -W -n "$p/.MainActivity" | tee /tmp/tong-old-launch; grep -q 'Status: ok' /tmp/tong-old-launch
adb shell am force-stop "$p"; adb logcat -b crash -c
adb install --no-streaming -r TongSKY-1.0.41.apk
test "$uid_before" = "$(adb shell pm list packages -U "$p" | tr -d '\r')"
adb shell dumpsys package "$p" > /tmp/tong-pkg; grep -F 'versionCode=10142' /tmp/tong-pkg; grep -F 'versionName=1.0.41' /tmp/tong-pkg
adb shell am start -W -n "$p/.MainActivity" | tee /tmp/tong-new-launch; grep -q 'Status: ok' /tmp/tong-new-launch
sleep 5; adb shell pidof "$p"
adb shell uiautomator dump /sdcard/tong-1041.xml; adb shell cat /sdcard/tong-1041.xml > /tmp/tong-window; grep -q 'package="kr.tong.sky"' /tmp/tong-window
adb logcat -b crash -d > /tmp/tong-crash; if grep -q 'Process: kr.tong.sky' /tmp/tong-crash; then cat /tmp/tong-crash; exit 1; fi
echo 'SIGNED 10140 -> 10142 in-place update, same package UID, activity and UI visible, no crash in smoke test. Offline only.'
