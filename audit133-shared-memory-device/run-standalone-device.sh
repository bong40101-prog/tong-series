#!/usr/bin/env bash
set -euo pipefail
report_dir="$PWD/audit133-shared-memory-device/standalone-reports"
mkdir -p "$report_dir"
capture() {
 adb logcat -d > "$report_dir/logcat.txt" || true
 adb shell run-as kr.tong.sky cat files/memory-device-report.json > "$report_dir/memory-device-report.json" || true
 adb shell dumpsys activity exit-info kr.tong.sky > "$report_dir/process-exit.txt" || true
 adb shell dumpsys webviewupdate > "$report_dir/webview.txt" || true
}
trap capture EXIT
adb wait-for-device
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put system screen_off_timeout 900000
adb logcat -G 16M
adb shell am force-stop kr.tong.sky || true
adb uninstall kr.tong.sky >/dev/null 2>&1 || true
adb install audit133-shared-memory-device/TongSKY-shared-memory-standalone133-probe.apk
adb shell pm grant kr.tong.sky android.permission.POST_NOTIFICATIONS
adb logcat -c
timeout 360 adb shell am instrument -w -e standalone true kr.tong.sky/kr.tong.sky.LabInstrumentation | tee "$report_dir/instrumentation.txt"
capture
grep -q OFFLINE_MEMORY_PROBE_PASS "$report_dir/instrumentation.txt"
! grep -q OFFLINE_MEMORY_PROBE_FAIL "$report_dir/instrumentation.txt"
python3 audit133-shared-memory-device/verify-standalone-runtime.py "$report_dir"
