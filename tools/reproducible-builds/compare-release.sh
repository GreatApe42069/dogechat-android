#!/usr/bin/env bash

set -euo pipefail

FIRST_DIR="${1:?usage: compare-release.sh FIRST_DIR SECOND_DIR}"
SECOND_DIR="${2:?usage: compare-release.sh FIRST_DIR SECOND_DIR}"

artifacts=(
  BUILDINFO.json
  SHA256SUMS.unsigned
  dogechat-android-arm64-unsigned.apk
  dogechat-android-armv7-unsigned.apk
  dogechat-android-release-unsigned.aab
  dogechat-android-universal-unsigned.apk
  dogechat-android-x86-unsigned.apk
  dogechat-android-x86_64-unsigned.apk
)

for artifact in "${artifacts[@]}"; do
  if ! cmp -s "$FIRST_DIR/$artifact" "$SECOND_DIR/$artifact"; then
    echo "error: reproducibility mismatch: $artifact" >&2
    if command -v diffoscope >/dev/null 2>&1; then
      diffoscope "$FIRST_DIR/$artifact" "$SECOND_DIR/$artifact" || true
    fi
    exit 1
  fi
  echo "MATCH $artifact"
done

echo "All unsigned APK, AAB, checksum, and build-info bytes match."
