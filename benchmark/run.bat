@echo off
REM 编译并运行 WorldTaste 配方匹配微基准。无外部依赖（仅需 JDK 21+）。
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
java -Dstdout.encoding=UTF-8 -cp out bench.Main
