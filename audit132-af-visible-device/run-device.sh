#!/usr/bin/env bash
set -euo pipefail
report_root="$PWD/audit132-af-visible-device/reports"
report_dir="$report_root/setup"
mkdir -p "$report_dir"
capture() {
 mkdir -p "$report_dir"
 adb logcat -d > "$report_dir/logcat.txt" || true
 adb shell run-as kr.tong.sky cat files/device-startup-report.json > "$report_dir/device-startup-report.json" || true
 adb shell dumpsys activity exit-info kr.tong.sky > "$report_dir/process-exit.txt" || true
 adb shell dumpsys webviewupdate > "$report_dir/webview.txt" || true
 for name in af-visible-results.png af-visible-saved.png af-visible-failure.png af132-remote-progress.json af132-remote-crash-request.json af132-expected-af-crash.json; do
  adb exec-out run-as kr.tong.sky cat "files/$name" > "$report_dir/$name" || true
 done
}
trap capture EXIT
adb wait-for-device
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put system screen_off_timeout 900000
adb logcat -G 16M
run_probe() {
 local mode="$1"
 report_dir="$report_root/$mode"
 mkdir -p "$report_dir"
 # Cleanup is between finished instrumentation runs, never during survival assertions.
 adb shell am force-stop kr.tong.sky
 adb install -r "audit132-af-visible-device/TongSKY-AF-visible-$mode-probe.apk"
 adb shell pm grant kr.tong.sky android.permission.POST_NOTIFICATIONS
 adb logcat -c
 if [ "$mode" = process ]; then
  timeout 180 adb shell am instrument -w -e crossProcess true kr.tong.sky/kr.tong.sky.LabInstrumentation | tee "$report_dir/instrumentation.txt"
 else
  timeout 300 adb shell am instrument -w kr.tong.sky/kr.tong.sky.LabInstrumentation | tee "$report_dir/instrumentation.txt"
 fi
 capture
 grep -q OFFLINE_DEVICE_PROBE_PASS "$report_dir/instrumentation.txt"
 ! grep -q OFFLINE_DEVICE_PROBE_FAIL "$report_dir/instrumentation.txt"
}
if [ "${PROBE_SCOPE:-all}" = all ]; then run_probe functional; fi
run_probe process
