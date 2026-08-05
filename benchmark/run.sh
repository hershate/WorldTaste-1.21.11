#!/usr/bin/env bash
# 编译并运行 WorldTaste 配方匹配微基准。无外部依赖（仅需 JDK 21+）。
# 产物/说明见 benchmark/README.md 与 note/report/perf/。
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/*.java
java -cp out bench.Main
