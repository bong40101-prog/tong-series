#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p evidence-background
adb root
adb wait-for-device
adb shell wm dismiss-keyguard
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
adb install /tmp/debug.apk
adb install /tmp/background-probe.apk
adb shell pm grant kr.tong.sky android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am instrument -w kr.tong.sky.backgroundqa/.BackgroundProbe | tee evidence-background/result.txt
adb logcat -b crash -d > evidence-background/crash.txt
adb logcat -d > evidence-background/logcat.txt
grep -q 'BACKGROUND_PASS:' evidence-background/result.txt
! grep -q 'FAIL:' evidence-background/result.txt
! grep -q 'Process: kr.tong.sky' evidence-background/crash.txt
