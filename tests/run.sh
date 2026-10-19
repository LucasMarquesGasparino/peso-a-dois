#!/bin/sh
# Testes JVM do núcleo puro (src/com/pesoadois/core). Não usa Android: se algum arquivo
# do núcleo importar android.* ou org.json, a compilação falha de propósito.
# Uso: sh tests/run.sh   (também é chamado por build.sh antes de compilar o APK)
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk}
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
OUT="$ROOT/build/test-classes"

rm -rf "$OUT"
mkdir -p "$OUT"

set -- "$ROOT"/tests/*.java
for source in "$ROOT"/src/com/pesoadois/core/*.java; do
    if [ -f "$source" ]; then set -- "$@" "$source"; fi
done

"$JAVAC" --release 8 -encoding UTF-8 -Xlint:all -Xlint:-options -d "$OUT" "$@"
"$JAVA" -cp "$OUT" CoreTests
