#!/usr/bin/env bash
# LosNote 服务器更新入口（稳健版）。
#
# 目标不是“尽量把能做的做完”，而是执行结束后能证明：
#   正在运行的容器来自 origin/main 的最新 commit，且健康检查与前端入口一致。
# 每一层校验失败都会立即退出，不会带着旧代码或旧镜像继续。
#
# 分层：
#   L1 代码   .env / 依赖命令 / 干净工作区 / main 分支 / fetch + 快进 / HEAD == origin/main
#   L2 备份   /data/losnote.db 用 VACUUM INTO 做时间戳快照，校验可读，保留最近 14 份
#   L3 重建   docker compose up -d --build --force-recreate（会短暂停服）
#   L4 启动   losnote 处于 Running，StartedAt 晚于本次构建开始
#   L5 验证   /api/health 返回 ok:true，首页引用与容器内 index.html 同一入口 JS
#             端口取自 `docker compose port losnote 8787`，不读 PORT 环境变量
#
# 安全：本脚本不读取也不打印访问令牌；不修改 .env；不删除 losnote-data 数据卷。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
SELF="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"
ORIGINAL_ARGS=("$@")

NO_PULL=0

usage() {
  cat <<'USAGE'
用法：bash update.sh [选项]

默认流程：L1 前置检查与快进 → L2 备份 SQLite → L3 重建镜像与容器
          → L4 校验启动时间 → L5 校验 /api/health 与前端入口指纹。

选项：
  --help      显示本帮助
  --no-pull   不执行 git pull，但仍会 git fetch origin main 并校验 HEAD == origin/main

端口：
  健康检查使用 `docker compose port losnote 8787` 返回的宿主端口；
  不再读取 PORT 环境变量或 .env 的 PORT。

注意：
  L3 使用 --force-recreate，即使没有新提交也会短暂停服后重启容器。

依赖：
  git、docker compose v2、flock（util-linux，需要支持 -n）。

内部变量：
  LOSNOTE_UPDATE_LOCKED 仅用于脚本自身取锁后重入，请勿手动设置。

失败时：
  前置检查失败：退出非 0，只打印原因与“未改动工作区，无需回滚”；
  运行阶段失败：退出非 0，打印失败阶段、docker compose logs --tail=100 与回滚提示。
数据卷 losnote-data 不会被脚本删除。
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --no-pull)
      NO_PULL=1
      ;;
    *)
      echo "未知参数：$1" >&2
      echo "运行 bash update.sh --help 查看用法。" >&2
      exit 2
      ;;
  esac
  shift
done

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_GREEN=$'\033[1;32m'
  C_YELLOW=$'\033[1;33m'
  C_RED=$'\033[1;31m'
  C_RESET=$'\033[0m'
else
  C_GREEN=''
  C_YELLOW=''
  C_RED=''
  C_RESET=''
fi

say() { printf '\n%s→ %s%s\n' "$C_GREEN" "$*" "$C_RESET"; }
warn() { printf '%s! %s%s\n' "$C_YELLOW" "$*" "$C_RESET" >&2; }

FAILED_STAGE=""
OLD_HEAD=""
OLD_IMAGE=""
BACKUP_FILE=""
BUILD_START_EPOCH=""
STARTED_AT=""
BASE_URL=""
HEALTH_URL=""

fail_precheck() {
  local reason="$1"
  trap - ERR
  printf '\n%s× [L1 代码] %s%s\n' "$C_RED" "$reason" "$C_RESET" >&2
  printf '  前置检查失败，未改动工作区，无需回滚。\n' >&2
  exit 1
}

# 并发锁：fd 9 上 flock，成功后不关闭；worker 通过 fork/exec 继承同一锁。
# wrapper 等待 worker：只杀 wrapper 时 worker 仍持有锁；worker 退出后内核释放。
# 锁文件优先 /run/lock；世界可写或属主/权限不合适时回退 $HOME/.cache/losnote-update（0700）。
# 不使用 /tmp；没有 PID/stale 清理。
if [ "${LOSNOTE_UPDATE_LOCKED:-0}" != "1" ]; then
  LOCK_KEY="$(printf '%s' "$SCRIPT_DIR" | cksum | awk '{print $1}')"
  LOCK_BASES=()
  if [ -d /run/lock ] && [ -w /run/lock ]; then
    RUN_LOCK_MODE="$(stat -c %a /run/lock 2>/dev/null || echo '')"
    case "${RUN_LOCK_MODE: -1}" in
      2|3|6|7) ;;  # 世界可写，优先私有目录
      *) LOCK_BASES+=(/run/lock) ;;
    esac
  fi
  if [ -n "${HOME:-}" ] && [ -d "${HOME:-}" ] && [ -w "${HOME:-}" ]; then
    LOCK_BASES+=("${HOME}/.cache/losnote-update")
  fi
  if [ "${#LOCK_BASES[@]}" -eq 0 ]; then
    fail_precheck "无法找到可写的私有锁目录（/run/lock 或 \$HOME/.cache）"
  fi

  command -v flock >/dev/null 2>&1 || fail_precheck "没有找到 flock（util-linux）。"
  LOCK_FILE=""
  for base in "${LOCK_BASES[@]}"; do
    if [ "$base" != "/run/lock" ]; then
      if ! mkdir -p -m 700 "$base" 2>/dev/null; then
        warn "无法创建私有锁目录，跳过：$base"
        continue
      fi
      if ! chmod 700 "$base" 2>/dev/null; then
        warn "无法设置私有锁目录权限，跳过：$base"
        continue
      fi
    fi
    candidate="${base}/losnote-update-${LOCK_KEY}.lock"
    if [ -L "$candidate" ]; then
      warn "锁文件是符号链接，跳过：$candidate"
      continue
    fi
    if ! : >>"$candidate" 2>/dev/null; then
      warn "无法创建锁文件，跳过：$candidate"
      continue
    fi
    if [ ! -O "$candidate" ]; then
      warn "锁文件不属于当前用户，跳过：$candidate"
      continue
    fi
    if ! chmod 600 "$candidate" 2>/dev/null; then
      warn "无法设置锁文件权限，跳过：$candidate"
      continue
    fi
    # 注意：exec 只做 fd 重定向，不能在这里重定向 shell 的 stderr（会永久生效）。
    if ! exec 9>>"$candidate"; then
      warn "无法打开锁文件，跳过：$candidate"
      continue
    fi
    LOCK_FILE="$candidate"
    break
  done
  if [ -z "$LOCK_FILE" ]; then
    fail_precheck "无法创建可用的锁文件（已尝试 /run/lock 与 \$HOME/.cache）"
  fi

  # 一次性取锁；成功后保持 fd 9 打开，worker 通过 fork/exec 继承同一锁。
  if ! flock -n 9; then
    exec 9>&-
    fail_precheck "已有另一个 update.sh 正在执行（$LOCK_FILE），本次退出。"
  fi

  export LOSNOTE_UPDATE_LOCKED=1
  bash "$SELF" ${ORIGINAL_ARGS[@]+"${ORIGINAL_ARGS[@]}"} &
  worker=$!
  rc=0
  wait "$worker" || rc=$?
  exit "$rc"
fi

# 仅供 wrapper→worker 通信，避免被后续子进程继承。
unset LOSNOTE_UPDATE_LOCKED

dump_logs() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose logs --tail=100 2>/dev/null || true
  fi
}

recovery_hint() {
  printf '\n可恢复信息：\n' >&2
  printf '  失败阶段：%s\n' "${FAILED_STAGE:-未知}" >&2
  printf '  查看日志：docker compose logs --tail=100\n' >&2
  if [ -n "${BACKUP_FILE:-}" ]; then
    printf '  数据库备份：%s（位于数据卷的 /data/backups/ 下）\n' "$BACKUP_FILE" >&2
    printf '  恢复数据库（WAL/SHM 必须一起清理，详见 docs/deploy.md 第 6 节）：\n' >&2
    printf '    docker compose stop\n' >&2
    printf "    docker compose run --rm --no-deps --entrypoint sh losnote -c 'rm -f /data/losnote.db /data/losnote.db-wal /data/losnote.db-shm && cp %s /data/losnote.db'\n" "$BACKUP_FILE" >&2
    printf '    docker compose start\n' >&2
  else
    printf '  数据库备份：本次未生成（数据库不存在或备份阶段之前已失败）\n' >&2
    printf '  恢复数据库：参考 docs/deploy.md 第 6 节；必须先 stop，并清理 /data/losnote.db、losnote.db-wal、losnote.db-shm 后再拷回备份。\n' >&2
  fi
  if [ -n "${OLD_HEAD:-}" ]; then
    printf '  代码回滚提示（仅在确认放弃本次更新时）：git switch --detach %s && docker compose up -d --build\n' "$OLD_HEAD" >&2
  else
    printf '  代码回滚提示：git switch --detach <更新前的 commit> && docker compose up -d --build\n' >&2
  fi
  if [ -n "${OLD_IMAGE:-}" ]; then
    printf '  镜像回滚提示：docker tag %s losnote:latest && docker compose up -d --no-build\n' "$OLD_IMAGE" >&2
  else
    printf '  镜像回滚提示：docker tag <更新前的镜像 ID> losnote:latest && docker compose up -d --no-build\n' >&2
  fi
  printf '  数据卷 losnote-data 不会被本脚本删除；请勿在排错时手动删除该卷。\n' >&2
}

fail_stage() {
  local stage="$1"
  shift
  trap - ERR
  FAILED_STAGE="$stage"
  printf '\n%s× [%s] %s%s\n' "$C_RED" "$stage" "$*" "$C_RESET" >&2
  printf '\n--- docker compose logs --tail=100 ---\n' >&2
  dump_logs >&2 || true
  printf '%s\n' '--- 日志结束 ---' >&2
  recovery_hint
  exit 1
}

on_error() {
  local code=$?
  trap - ERR
  FAILED_STAGE="未预期命令（第 ${BASH_LINENO[0]:-?} 行）"
  printf '\n%s× %s，退出码 %s%s\n' "$C_RED" "$FAILED_STAGE" "$code" "$C_RESET" >&2
  printf '\n--- docker compose logs --tail=100 ---\n' >&2
  dump_logs >&2 || true
  printf '%s\n' '--- 日志结束 ---' >&2
  recovery_hint
  exit "$code"
}
trap on_error ERR

# 不读取 .env 内容；只检查存在性。访问令牌由 docker compose 自行读取。
# ---------------- L1 前置检查与快进 ----------------

say "L1 代码：前置检查"

[ -f .env ] || fail_precheck "缺少 .env。先执行 cp .env.example .env 并填写访问令牌。"

command -v git >/dev/null 2>&1 || fail_precheck "没有找到 git。"
command -v docker >/dev/null 2>&1 || fail_precheck "没有找到 docker。"
docker compose version >/dev/null 2>&1 || fail_precheck "docker compose v2 不可用（试试 docker compose version）。"

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail_precheck "当前目录不是 git 仓库：$SCRIPT_DIR"

BRANCH="$(git symbolic-ref -q --short HEAD || true)"
if [ -z "$BRANCH" ]; then
  fail_stage "L1 代码" "当前是 detached HEAD。请先执行：git switch main"
fi
if [ "$BRANCH" != "main" ]; then
  fail_stage "L1 代码" "当前分支是 $BRANCH，不是 main。请先执行：git switch main"
fi

DIRTY="$(git status --porcelain)"
if [ -n "$DIRTY" ]; then
  printf '%s\n' "$DIRTY" >&2
  fail_stage "L1 代码" "工作区有未提交改动，已停止，没有覆盖任何文件。可先执行：git stash push -u -m \"update-save\"，更新完成后再 git stash pop"
fi

OLD_HEAD="$(git rev-parse HEAD)"
OLD_SCRIPT_HASH="$(git hash-object update.sh 2>/dev/null || true)"
OLD_IMAGE="$(docker inspect --format '{{.Image}}' losnote 2>/dev/null || true)"

if ! git fetch origin main; then
  fail_stage "L1 代码" "git fetch origin main 失败。检查网络/SSH 后重试；本次没有改动工作区"
fi

ORIGIN_HEAD="$(git rev-parse origin/main 2>/dev/null || true)"
[ -n "$ORIGIN_HEAD" ] || fail_stage "L1 代码" "无法读取 origin/main，请检查远程配置：git remote -v"

if [ "$NO_PULL" -eq 1 ]; then
  if [ "$OLD_HEAD" != "$ORIGIN_HEAD" ]; then
    printf '\n本地与 origin/main 的差异：\n' >&2
    git log --oneline --left-right --boundary HEAD...origin/main >&2 || true
    fail_stage "L1 代码" "使用了 --no-pull，但本地 HEAD 与 origin/main 不一致。去掉 --no-pull 让脚本安全快进"
  fi
  say "--no-pull：已跳过 git pull"
else
  if ! git pull --ff-only origin main; then
    printf '\n本地与远程的差异：\n' >&2
    git log --oneline --left-right --boundary HEAD...origin/main >&2 || true
    git rev-list --left-right --count HEAD...origin/main >&2 || true
    fail_stage "L1 代码" "无法快进到 origin/main（本地与远程分叉或远程被改写）。脚本没有执行 reset 或自动 merge，请手动处理后重试"
  fi
fi

HEAD_NOW="$(git rev-parse HEAD)"
ORIGIN_NOW="$(git rev-parse origin/main)"
if [ "$HEAD_NOW" != "$ORIGIN_NOW" ]; then
  fail_stage "L1 代码" "拉取后 HEAD 仍不等于 origin/main（HEAD=$HEAD_NOW，origin/main=$ORIGIN_NOW）"
fi

DIRTY_AFTER="$(git status --porcelain)"
if [ -n "$DIRTY_AFTER" ]; then
  printf '%s\n' "$DIRTY_AFTER" >&2
  fail_stage "L1 代码" "拉取后工作区不干净，停止更新"
fi

NEW_SCRIPT_HASH="$(git hash-object update.sh 2>/dev/null || true)"
if [ -n "$OLD_SCRIPT_HASH" ] && [ "$OLD_SCRIPT_HASH" != "$NEW_SCRIPT_HASH" ]; then
  warn "update.sh 在本次拉取中发生变化：当前进程仍按旧脚本执行，完成后再运行一次 bash update.sh 即可"
fi

HEAD_SHORT="$(git rev-parse --short=12 HEAD)"
HEAD_SUBJECT="$(git show -s --format=%s HEAD)"
say "代码已是最新：$HEAD_SHORT  $HEAD_SUBJECT"

# ---------------- L2 备份 ----------------

say "L2 备份：/data/losnote.db"

BACKUP_TS="$(date +%Y%m%d-%H%M%S-%N)"
read -r -d '' BACKUP_JS <<'JS' || true
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const dbPath = '/data/losnote.db';
const backupDir = '/data/backups';
const ts = process.env.BACKUP_TS;
const dest = path.join(backupDir, `losnote-${ts}.db`);
const tmp = `${dest}.tmp`;

if (!fs.existsSync(dbPath)) {
  console.log('BACKUP_SKIPPED=no-db');
  process.exit(0);
}

try {
  fs.mkdirSync(backupDir, { recursive: true });
  try {
    fs.chmodSync(backupDir, 0o700);
  } catch (err) {
    // 既有目录可能属于 root；权限收紧失败不应阻塞更新，但要在输出中提示。
    console.log(`BACKUP_WARNING=无法把备份目录设为 0700：${err && err.message ? err.message : err}`);
  }
  // 清理上次中断留下的临时文件，避免永久占用空间。
  for (const name of fs.readdirSync(backupDir)) {
    if (/^losnote-.*\.db\.tmp$/.test(name)) {
      fs.rmSync(path.join(backupDir, name), { force: true });
    }
  }
  fs.rmSync(tmp, { force: true });

  const src = new DatabaseSync(dbPath);
  src.exec(`VACUUM INTO '${tmp.replace(/'/g, "''")}'`);
  src.close();

  const check = new DatabaseSync(tmp);
  const row = check.prepare('SELECT count(*) AS n FROM notes').get();
  check.close();

  const size = fs.statSync(tmp).size;
  if (!(size > 0)) throw new Error('备份文件大小为 0');

  fs.chmodSync(tmp, 0o600);
  fs.renameSync(tmp, dest);
  fs.chmodSync(dest, 0o600);

  const files = fs.readdirSync(backupDir)
    .filter((name) => /^losnote-.*\.db$/.test(name))
    .map((name) => {
      const p = path.join(backupDir, name);
      return { p, mtime: fs.statSync(p).mtimeMs };
    })
    .sort((a, b) => b.mtime - a.mtime);
  for (const item of files.slice(14)) fs.rmSync(item.p, { force: true });

  console.log(`BACKUP_FILE=${dest}`);
  console.log(`BACKUP_BYTES=${size}`);
  console.log(`BACKUP_NOTES=${row.n}`);
  console.log(`BACKUP_RETAINED=${Math.min(files.length, 14)}`);
} catch (err) {
  try { fs.rmSync(tmp, { force: true }); } catch {}
  console.error(`备份失败：${err && err.message ? err.message : err}`);
  process.exit(1);
}
JS

CONTAINER_RUNNING="$(docker inspect --format '{{.State.Running}}' losnote 2>/dev/null || true)"

if [ "$CONTAINER_RUNNING" = "true" ]; then
  if ! BACKUP_OUT="$(docker compose exec -T -e BACKUP_TS="$BACKUP_TS" losnote node --no-warnings -e "$BACKUP_JS" 2>&1)"; then
    printf '%s\n' "$BACKUP_OUT" >&2
    fail_stage "L2 备份" "VACUUM INTO 备份失败，未重建、未重启容器"
  fi
else
  if ! BACKUP_OUT="$(docker compose run -T --rm --no-deps -e BACKUP_TS="$BACKUP_TS" --entrypoint node losnote --no-warnings -e "$BACKUP_JS" 2>&1)"; then
    printf '%s\n' "$BACKUP_OUT" >&2
    fail_stage "L2 备份" "容器未运行，用一次性容器备份失败，未重建、未重启容器"
  fi
fi

if printf '%s\n' "$BACKUP_OUT" | grep -q '^BACKUP_SKIPPED=no-db$'; then
  say "没有 /data/losnote.db（首次部署），跳过备份"
else
  BACKUP_FILE="$(printf '%s\n' "$BACKUP_OUT" | sed -n 's/^BACKUP_FILE=//p' | tail -n 1)"
  BACKUP_BYTES="$(printf '%s\n' "$BACKUP_OUT" | sed -n 's/^BACKUP_BYTES=//p' | tail -n 1)"
  BACKUP_NOTES="$(printf '%s\n' "$BACKUP_OUT" | sed -n 's/^BACKUP_NOTES=//p' | tail -n 1)"
  BACKUP_RETAINED="$(printf '%s\n' "$BACKUP_OUT" | sed -n 's/^BACKUP_RETAINED=//p' | tail -n 1)"
  BACKUP_WARNING="$(printf '%s\n' "$BACKUP_OUT" | sed -n 's/^BACKUP_WARNING=//p' | tail -n 1)"
  [ -n "$BACKUP_FILE" ] || fail_stage "L2 备份" "备份命令没有返回文件路径"
  if [ -n "$BACKUP_WARNING" ]; then
    warn "$BACKUP_WARNING"
  fi
  printf '  备份文件：%s（%s 字节，notes=%s，保留 %s 份）\n' "$BACKUP_FILE" "$BACKUP_BYTES" "$BACKUP_NOTES" "$BACKUP_RETAINED"
fi

# ---------------- L3 重建 ----------------

say "L3 重建：docker compose up -d --build（--force-recreate 会短暂停服）"
BUILD_START_EPOCH="$(date +%s)"
if ! docker compose up -d --build --force-recreate; then
  fail_stage "L3 重建" "docker compose up -d --build 失败，未完成部署"
fi

# 健康检查端口以 compose 实际端口映射为准，不读 PORT/.env，避免访问错端口。
PORT_MAPPING="$(docker compose port losnote 8787 2>/dev/null | tail -n 1 | tr -d '\r' || true)"
HOST_ADDRS=()
HOST_PORT=""
if [ -n "$PORT_MAPPING" ]; then
  HOST_PORT="${PORT_MAPPING##*:}"
  RAW_HOST="${PORT_MAPPING%:*}"
  case "$RAW_HOST" in
    ""|0.0.0.0) HOST_ADDRS=("127.0.0.1") ;;
    "[::]"|"::") HOST_ADDRS=("127.0.0.1" "[::1]") ;;
    \[*\]) HOST_ADDRS=("$RAW_HOST") ;;
    *:*) HOST_ADDRS=("[$RAW_HOST]") ;;
    *) HOST_ADDRS=("$RAW_HOST") ;;
  esac
else
  HOST_ADDRS=("127.0.0.1")
fi
if ! [[ "${HOST_PORT:-}" =~ ^[0-9]+$ ]]; then
  HOST_PORT=8787
  warn "无法从 docker compose port losnote 8787 读取宿主端口，回退 8787"
fi
HOST_ADDR="${HOST_ADDRS[0]}"
BASE_URL="http://${HOST_ADDR}:${HOST_PORT}"
HEALTH_URL="${BASE_URL}/api/health"
printf '  健康检查候选：%s（端口 %s，来自 docker compose port losnote 8787）\n' "${HOST_ADDRS[*]}" "$HOST_PORT"

# ---------------- L4 启动 ----------------

say "L4 启动：检查容器状态与启动时间"

RUNNING="$(docker inspect --format '{{.State.Running}}' losnote 2>/dev/null || true)"
if [ "$RUNNING" != "true" ]; then
  fail_stage "L4 启动" "容器 losnote 不在 Running 状态（当前：${RUNNING:-不存在}）"
fi

STARTED_AT="$(docker inspect --format '{{.State.StartedAt}}' losnote 2>/dev/null || true)"
if [ -z "$STARTED_AT" ] || [ "$STARTED_AT" = "0001-01-01T00:00:00Z" ]; then
  fail_stage "L4 启动" "无法读取容器 StartedAt（当前：${STARTED_AT:-空}）"
fi

STARTED_EPOCH="$(date -d "$STARTED_AT" +%s 2>/dev/null || true)"
if [ -z "$STARTED_EPOCH" ]; then
  if command -v node >/dev/null 2>&1; then
    STARTED_EPOCH="$(node -e 'const t = Date.parse(process.argv[1]); if (Number.isFinite(t)) console.log(Math.floor(t / 1000))' "$STARTED_AT" 2>/dev/null || true)"
  fi
fi
[ -n "$STARTED_EPOCH" ] || fail_stage "L4 启动" "无法解析容器启动时间：$STARTED_AT"

if [ "$STARTED_EPOCH" -lt "$BUILD_START_EPOCH" ]; then
  fail_stage "L4 启动" "容器 StartedAt（$STARTED_AT）早于本次构建开始时间，可能仍在运行旧容器"
fi
printf '  容器已启动：%s\n' "$STARTED_AT"

# ---------------- L5 验证 ----------------

say "L5 验证：健康检查与前端入口指纹"

http_get() {
  local url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -fsS --max-time 10 "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO- --timeout=10 "$url"
  elif command -v node >/dev/null 2>&1; then
    node -e 'fetch(process.argv[1]).then(async (r) => { if (!r.ok) process.exit(1); process.stdout.write(await r.text()); }).catch(() => process.exit(1))' "$url"
  else
    return 127
  fi
}

HEALTH_BODY=""
HEALTH_OK=0
HEALTH_LAST=$(( ${#HOST_ADDRS[@]} - 1 ))
for HOST_ADDR in "${HOST_ADDRS[@]}"; do
  # 多候选时首个只等 5 秒，避免 IPv6-only 等 30 秒；最后一个候选保留 30 秒。
  if [ "$HOST_ADDR" = "${HOST_ADDRS[$HEALTH_LAST]}" ]; then
    HEALTH_WAIT=30
  else
    HEALTH_WAIT=5
  fi
  BASE_URL="http://${HOST_ADDR}:${HOST_PORT}"
  HEALTH_URL="${BASE_URL}/api/health"
  for ((i = 0; i < HEALTH_WAIT; i++)); do
    if HEALTH_BODY="$(http_get "$HEALTH_URL" 2>/dev/null)" \
      && printf '%s' "$HEALTH_BODY" | grep -Eq '"ok"[[:space:]]*:[[:space:]]*true'; then
      HEALTH_OK=1
      break
    fi
    HEALTH_BODY=""
    sleep 1
  done
  if [ "$HEALTH_OK" -eq 1 ]; then
    break
  fi
done
if [ "$HEALTH_OK" -ne 1 ]; then
  fail_stage "L5 验证" "GET /api/health 未在超时时间内返回 200 且 ok:true（端口 ${HOST_PORT}，已尝试：${HOST_ADDRS[*]}）"
fi
printf '  health：%s（%s）\n' "$HEALTH_BODY" "$HEALTH_URL"

extract_js_asset() {
  grep -oE '(src|href)="[^"]+\.js"' | head -n 1 | cut -d'"' -f2 || true
}

if ! CONTAINER_HTML="$(docker compose exec -T losnote cat /app/web-dist/index.html 2>/dev/null)"; then
  fail_stage "L5 验证" "无法读取容器内 /app/web-dist/index.html"
fi
INDEX_ASSET="$(printf '%s' "$CONTAINER_HTML" | extract_js_asset)"
[ -n "$INDEX_ASSET" ] || fail_stage "L5 验证" "容器内 index.html 没有引用入口 JS"

if ! PAGE_HTML="$(http_get "$BASE_URL/" 2>/dev/null)"; then
  fail_stage "L5 验证" "GET $BASE_URL/ 失败，无法校验首页入口资源"
fi
SERVED_ASSET="$(printf '%s' "$PAGE_HTML" | extract_js_asset)"
[ -n "$SERVED_ASSET" ] || fail_stage "L5 验证" "首页 HTML 没有引用入口 JS"

if [ "$SERVED_ASSET" != "$INDEX_ASSET" ]; then
  fail_stage "L5 验证" "首页入口资源（$SERVED_ASSET）与容器内 index.html（$INDEX_ASSET）不一致"
fi
printf '  前端入口：%s\n' "$INDEX_ASSET"

# ---------------- 完成 ----------------

printf '\n%s✓ 更新完成%s\n' "$C_GREEN" "$C_RESET"
printf '  commit：%s（%s）\n' "$(git rev-parse --short=12 HEAD)" "$(git rev-parse HEAD)"
printf '  健康检查：%s\n' "$HEALTH_URL"
printf '  容器启动：%s\n' "$STARTED_AT"
if [ -n "$BACKUP_FILE" ]; then
  printf '  数据库备份：%s\n' "$BACKUP_FILE"
else
  printf '  数据库备份：无（首次部署）\n'
fi
printf '  结束时间：%s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
