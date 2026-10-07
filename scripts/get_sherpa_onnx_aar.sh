#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$ROOT/app/libs"
URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar'
OUT="$ROOT/app/libs/sherpa-onnx-1.13.8.aar"
EXPECTED='633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
printf '%s\n' "Downloading $URL"
curl -L --fail --retry 2 -o "$OUT.part" "$URL"
ACTUAL=$(sha256sum "$OUT.part" | awk '{print $1}')
[ "$ACTUAL" = "$EXPECTED" ] || { echo "SHA-256 mismatch: $ACTUAL" >&2; rm -f "$OUT.part"; exit 1; }
mv -f "$OUT.part" "$OUT"
printf '%s\n' "Installed: $OUT"
