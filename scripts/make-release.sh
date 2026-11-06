#!/usr/bin/env bash
# 打一个「拷到服务器就能跑」的发布包（不需要 git，也不需要推送仓库）。
#
#   bash scripts/make-release.sh            # 用当前 HEAD 短 hash 命名
#   bash scripts/make-release.sh 0.1.0      # 自定义版本号
#
# 用法（在服务器上，先装好 Docker）：
#   scp dist/losnote-server-*.tar.gz user@你的服务器:~/
#   ssh user@你的服务器
#   tar xzf losnote-server-*.tar.gz && cd losnote-*/
#   echo "LOSNOTE_TOKEN=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)" > .env
#   docker compose up -d --build
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-$(git rev-parse --short HEAD)}"
OUT="dist/losnote-server-${VERSION}.tar.gz"
mkdir -p dist

# 只打部署需要的东西：服务端、网页端源码、Docker 构建上下文、文档
git archive --format=tar.gz \
  --prefix="losnote-${VERSION}/" \
  -o "$OUT" HEAD \
  server web Dockerfile docker-compose.yml .dockerignore README.md docs

echo "已生成 ${OUT}（$(du -h "$OUT" | cut -f 1)）"
echo
echo "服务器上："
echo "  tar xzf $(basename "$OUT") && cd losnote-${VERSION}"
echo "  echo \"LOSNOTE_TOKEN=\$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)\" > .env"
echo "  docker compose up -d --build"
