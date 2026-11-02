#!/usr/bin/env bash
set -euo pipefail
p=kr.tong.sky
old=TongSKY-1.0.40.apk; new=TongSKY-1.0.40-r2.apk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p evidence
adb shell wm dismiss-keyguard || true
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable; adb shell svc data disable
# Simulate a current user who already granted notifications, not a permission dialog on a fresh install.
adb install -g "$old"
adb shell dumpsys package "$p" | tr -d '\r' > evidence/base-package.txt
grep -m1 'versionCode=10140' evidence/base-package.txt
before=$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/base-package.txt); test -n "$before"
adb install -r "$new"
adb shell dumpsys package "$p" | tr -d '\r' > evidence/new-package.txt; grep -m1 'versionCode=10141' evidence/new-package.txt
after=$(awk -F= '/firstInstallTime=/{print $2;exit}' evidence/new-package.txt); test "$before" = "$after"; printf 'same firstInstallTime: %s\n' "$after"
comp=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$p" | tr -d '\r' | tail -1); [[ "$comp" == "$p/"* ]]
adb logcat -c; adb shell am start -W -n "$comp" | tee evidence/start.txt; grep -q 'Status: ok' evidence/start.txt
sleep 6; adb shell pidof "$p"; adb shell dumpsys activity activities > evidence/activities.txt; grep -m1 -Ei '(mResumedActivity|topResumedActivity|ResumedActivity).*kr\.tong\.sky' evidence/activities.txt
adb logcat -b crash -d > evidence/crash.txt; if grep -q 'Process: kr.tong.sky' evidence/crash.txt; then exit 1; fi
adb exec-out screencap -p > evidence/startup.png
# Invoke the signed application's own classifier, not a reimplementation.
adb push "$new" /data/local/tmp/tong-candidate.apk; adb push /tmp/qa10141/probe.jar /data/local/tmp/tong-probe.jar
adb shell chmod 444 /data/local/tmp/tong-candidate.apk /data/local/tmp/tong-probe.jar
adb shell 'CLASSPATH=/data/local/tmp/tong-probe.jar:/data/local/tmp/tong-candidate.apk app_process /system/bin ScopeProbe /data/local/tmp/tong-candidate.apk' | tee evidence/dex-probe.txt
grep -Fq 'PASS released DEX: 52 directions, 4 Canada directions, 156 synthetic calendars, 156 OZ carrier-isolation controls' evidence/dex-probe.txt
