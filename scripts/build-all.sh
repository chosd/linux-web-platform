#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[backend] ./gradlew build"
(
  cd "$ROOT_DIR/backend"
  ./gradlew build
)
