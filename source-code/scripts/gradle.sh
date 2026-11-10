#!/usr/bin/env bash
# Chạy Gradle trong container JDK 21 (máy dev chỉ có JDK 8). Dùng: bash source-code/scripts/gradle.sh build
set -euo pipefail
cd "$(dirname "$0")/.."
HOST_DIR=$(pwd -W 2>/dev/null || pwd) # Git Bash: D:/... cho Docker Desktop
export MSYS_NO_PATHCONV=1
exec docker run --rm -v "$HOST_DIR:/p" -v ewallet-gradle-cache:/home/gradle/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /p gradle:8.14-jdk21 gradle "$@" --no-daemon
