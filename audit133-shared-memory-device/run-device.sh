#!/usr/bin/env bash
set -euo pipefail
report_root="$PWD/audit133-shared-memory-device/reports"
report_dir="$report_root/setup"
mkdir -p "$report_dir"
capture() {
 mkdir -p "$report_dir"
 adb logcat -d > "$report_dir/logcat.txt" || true
 adb shell run-as kr.tong.sky cat files/memory-device-report.json > "$report_dir/memory-device-report.json" || true
 adb shell dumpsys activity exit-info kr.tong.sky > "$report_dir/process-exit.txt" || true
 adb shell dumpsys webviewupdate > "$report_dir/webview.txt" || true
 adb shell getprop dalvik.vm.heapgrowthlimit > "$report_dir/heapgrowthlimit.txt" || true
 adb shell getprop dalvik.vm.heapsize > "$report_dir/heapsize.txt" || true
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
 # Fresh synthetic app data/signers between independent regression phases only.
 adb shell am force-stop kr.tong.sky || true
 adb uninstall kr.tong.sky >/dev/null 2>&1 || true
 adb install "audit133-shared-memory-device/TongSKY-shared-memory-$mode-probe.apk"
 adb shell pm grant kr.tong.sky android.permission.POST_NOTIFICATIONS
 adb logcat -c
 set +e
 if [ "$mode" = baseline132 ]; then
  timeout 480 adb shell am instrument -w -e baseline true kr.tong.sky/kr.tong.sky.LabInstrumentation | tee "$report_dir/instrumentation.txt"
 else
  timeout 480 adb shell am instrument -w kr.tong.sky/kr.tong.sky.LabInstrumentation | tee "$report_dir/instrumentation.txt"
 fi
 local rc=${PIPESTATUS[0]}
 set -e
 printf '%s\n' "$rc" > "$report_dir/runner-exit.txt"
 capture
 if [ "$mode" = baseline132 ]; then
  python3 audit133-shared-memory-device/verify-runtime.py baseline "$report_dir"
 else
  grep -q OFFLINE_MEMORY_PROBE_PASS "$report_dir/instrumentation.txt"
  ! grep -q OFFLINE_MEMORY_PROBE_FAIL "$report_dir/instrumentation.txt"
  python3 audit133-shared-memory-device/verify-runtime.py fixed "$report_dir"
 fi
}
run_probe baseline132
run_probe fixed133
