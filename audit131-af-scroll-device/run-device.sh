#!/usr/bin/env bash
set -euo pipefail
mkdir -p audit131-af-scroll-device/reports
report_dir="$PWD/audit131-af-scroll-device/reports"
trap 'adb logcat -d > "$report_dir/logcat.txt" || true; adb shell run-as kr.tong.sky cat files/device-startup-report.json > "$report_dir/device-startup-report.json" || true; adb shell dumpsys activity exit-info kr.tong.sky > "$report_dir/process-exit.txt" || true; adb shell dumpsys webviewupdate > "$report_dir/webview.txt" || true; adb exec-out run-as kr.tong.sky cat files/af-scroll-results.png > "$report_dir/af-scroll-results.png" || true; adb exec-out run-as kr.tong.sky cat files/af-scroll-saved.png > "$report_dir/af-scroll-saved.png" || true; adb exec-out run-as kr.tong.sky cat files/af-scroll-failure.png > "$report_dir/af-scroll-failure.png" || true' EXIT
adb wait-for-device
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put system screen_off_timeout 600000
adb logcat -G 16M
adb logcat -c
adb install -r audit131-af-scroll-device/TongSKY-AF-scroll-offline-probe.apk
adb shell pm grant kr.tong.sky android.permission.POST_NOTIFICATIONS
timeout 300 adb shell am instrument -w kr.tong.sky/kr.tong.sky.LabInstrumentation | tee "$report_dir/instrumentation.txt"
grep -q OFFLINE_DEVICE_PROBE_PASS "$report_dir/instrumentation.txt"
! grep -q OFFLINE_DEVICE_PROBE_FAIL "$report_dir/instrumentation.txt"
