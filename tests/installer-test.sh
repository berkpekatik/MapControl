#!/usr/bin/env bash
# All adb invocations go to this exported mock; never connects to a device.
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -rf -- "$test_dir"' EXIT
export MOCK_LOG="$test_dir/adb.log"
export MOCK_RESULT=Success MOCK_EXIT=0
touch "$test_dir/test apk.apk"
adb() {
    printf '%s\n' "$*" >> "$MOCK_LOG"
    printf '%s\n' "$MOCK_RESULT"
    return "$MOCK_EXIT"
}
export -f adb
checks=0
check() {
    local expected="$1"
    shift
    : > "$MOCK_LOG"
    local status=0
    bash "$@" > "$test_dir/output" 2>&1 || status=$?
    [[ "$status" == "$expected" ]] || {
        cat "$test_dir/output"
        echo "Unexpected exit: $status for $*" >&2
        exit 1
    }
    checks=$((checks + 1))
}
check 2 install.sh
[[ ! -s "$MOCK_LOG" ]]
check 2 install.sh test-device "$test_dir/missing.apk"
[[ ! -s "$MOCK_LOG" ]]
check 2 uninstall.sh
[[ ! -s "$MOCK_LOG" ]]
check 0 install.sh test-device "$test_dir/test apk.apk"
[[ "$(cat "$MOCK_LOG")" == "-s test-device install -r $test_dir/test apk.apk" ]]
check 0 uninstall.sh test-device
[[ "$(cat "$MOCK_LOG")" == '-s test-device uninstall com.mapcontrol' ]]
export MOCK_EXIT=1 MOCK_RESULT='Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]'
check 1 uninstall.sh test-device
! grep -q 'MapControl removed' "$test_dir/output"
check 1 install.sh test-device "$test_dir/test apk.apk"
! grep -q 'APK installed' "$test_dir/output"
# Some bridges report zero exit status even when the command failed.
export MOCK_EXIT=0
check 1 install.sh test-device "$test_dir/test apk.apk"
check 1 uninstall.sh test-device
export MOCK_RESULT=$'Success\r'
check 0 uninstall.sh test-device
echo "$checks installer scenarios passed (mock adb only)."
