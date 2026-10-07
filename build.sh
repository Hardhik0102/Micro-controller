#!/usr/bin/env bash
# Compile everything into ./out (JDK javac, or the jdk.compiler module of a JRE)
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
find src -name "*.java" > /tmp/ms51-sources.txt
if command -v javac >/dev/null 2>&1; then
  javac -d out @/tmp/ms51-sources.txt
else
  java -m jdk.compiler/com.sun.tools.javac.Main -d out @/tmp/ms51-sources.txt
fi
echo "Build OK -> ./out"
