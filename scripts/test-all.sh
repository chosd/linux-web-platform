#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[frontend] pnpm run build"
(
  cd "$ROOT_DIR/frontend"
  pnpm install
  pnpm run build
)

echo "[backend] ./gradlew test"
(
  cd "$ROOT_DIR/backend"
  ./gradlew test
)
