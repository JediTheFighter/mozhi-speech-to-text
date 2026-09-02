#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/core/stt/src/main/cpp/whisper.cpp"
if [[ -f "$DEST/CMakeLists.txt" ]]; then
  echo "whisper.cpp already present at $DEST"
  exit 0
fi
mkdir -p "$(dirname "$DEST")"
git clone --depth 1 --branch v1.7.5 https://github.com/ggml-org/whisper.cpp.git "$DEST"
echo "Cloned whisper.cpp v1.7.5"
