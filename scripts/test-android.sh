#!/usr/bin/env bash
# 在 WSL / Linux 上跑安卓 JVM 单元测试（含 Robolectric 的 v5→v6 迁移与同步 LWW 用例）。
# 依赖 .toolchain 里的 JDK 21 + Android SDK，见 AGENTS.md「本地构建」一节。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEPS="$ROOT/.toolchain/robolectric-deps"
SDK34=android-all-instrumented-14-robolectric-10818077-i7
SDK35=android-all-instrumented-15-robolectric-12650502-i7
mkdir -p "$DEPS"
for a in "$SDK34" "$SDK35"; do
  if [ ! -f "$DEPS/$a.jar" ]; then
    echo "下载 $a.jar ..."
    curl -sSL -o "$DEPS/$a.jar" \
      "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/$a/$a.jar" || true
  fi
done

export JAVA_HOME="${JAVA_HOME:-$ROOT/.toolchain/jdk-21.0.13+9}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.toolchain/gradle-home}"
export ANDROID_HOME="${ANDROID_HOME:-$ROOT/.toolchain/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ROOT/.toolchain/android-user-home}"

exec "$ROOT/.toolchain/gradle-8.13/bin/gradle" testDebugUnitTest --no-daemon --console=plain "$@"
