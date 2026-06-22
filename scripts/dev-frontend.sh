#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
  cd "$ROOT_DIR/frontend"
  pnpm install
fi

cd "$ROOT_DIR/frontend"
exec pnpm run dev
