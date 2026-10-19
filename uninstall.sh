#!/usr/bin/env bash
# Remove only MapControl; no vehicle setting reset or temporary-file deletion.
set -euo pipefail
if [[ $# -ne 1 || -z "$1" || "$1" == -* ]]; then
    echo "Usage: bash uninstall.sh DEVICE_SERIAL" >&2
    exit 2
fi
output=$(adb -s "$1" uninstall com.mapcontrol 2>&1) || {
    printf '%s\n' "$output" >&2
    echo "Removal failed. If MapControl is device owner, use its own removal screen first." >&2
    exit 1
}
printf '%s\n' "$output"
if ! printf '%s\n' "$output" | tr -d '\r' | grep -qx 'Success'; then
    echo "Removal was not confirmed by Android." >&2
    exit 1
fi
echo "MapControl removed. Previous vehicle settings were not restored."
