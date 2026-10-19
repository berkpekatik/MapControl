#!/usr/bin/env bash
# Minimal installer; no privileged vehicle setup.
set -euo pipefail
if [[ $# -ne 2 || -z "$1" || "$1" == -* || ! -f "$2" || "$2" == -* ]]; then
    echo "Usage: bash install.sh DEVICE_SERIAL APK_PATH" >&2
    exit 2
fi
output=$(adb -s "$1" install -r "$2" 2>&1) || {
    printf '%s\n' "$output" >&2
    exit 1
}
printf '%s\n' "$output"
if ! printf '%s\n' "$output" | tr -d '\r' | grep -qx 'Success'; then
    echo "Installation was not confirmed by Android." >&2
    exit 1
fi
echo "APK installed. No device-owner role, privileged grants or vehicle settings were applied."
