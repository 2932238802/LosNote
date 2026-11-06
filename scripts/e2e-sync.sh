#!/usr/bin/env bash
# 跨语言端到端：起一个**真** Node 服务 + 临时库，让 Robolectric 里的真 SyncClient 走一遍
# 「手机 A 建题复习 → 同步 → 换设备 → 拉回 SRS 进度」。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PORT="${LOSNOTE_E2E_PORT:-8895}"
TOKEN="e2e-token-$(date +%s)"
E2E_DIR="$ROOT/.toolchain/e2e"
mkdir -p "$E2E_DIR"
rm -f "$E2E_DIR"/e2e.db "$E2E_DIR"/e2e.db-wal "$E2E_DIR"/e2e.db-shm

export LOSNOTE_TOKEN="$TOKEN"
export LOSNOTE_PORT="$PORT"
export LOSNOTE_DB="$E2E_DIR/e2e.db"
export LOSNOTE_STATIC="$ROOT/web/dist"

node server/src/index.js >"$E2E_DIR/server.log" 2>&1 &
SRV=$!
cleanup() { kill "$SRV" 2>/dev/null || true; wait "$SRV" 2>/dev/null || true; }
trap cleanup EXIT

ok=0
for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then ok=1; break; fi
  sleep 0.5
done
if [ "$ok" != 1 ]; then
  echo "服务没起来，日志："
  tail -20 "$E2E_DIR/server.log"
  exit 1
fi
echo "服务已就绪 http://127.0.0.1:$PORT（临时库 $E2E_DIR/e2e.db）"

exec bash "$ROOT/scripts/test-android.sh" \
  --tests "com.example.losnote.sync.SyncServerE2ETest" \
  -Plosnote.e2e.server="http://127.0.0.1:$PORT" \
  -Plosnote.e2e.token="$TOKEN"
