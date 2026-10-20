#!/usr/bin/env bash
# Compila e executa os testes da camada de domínio SEM Android SDK,
# usando o compilador Kotlin e o JUnit embutidos na distribuição do Gradle.
# Uso: GRADLE_HOME=/opt/gradle ./scripts/run-domain-tests.sh
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_HOME="${GRADLE_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v gradle)")")")}"
L="$GRADLE_HOME/lib"
jar() { for f in "$L"/$1 "$L"/plugins/$1; do [ -e "$f" ] && { echo "$f"; return 0; }; done; echo "jar não encontrado: $1" >&2; return 1; }

KOTLINC_CP="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlin-daemon-embeddable-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-[0-9]*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar')"
LIBS="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlinx-serialization-core-jvm-*.jar'):$(jar 'kotlinx-serialization-json-jvm-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'junit-4*.jar'):$(jar 'hamcrest-core-*.jar')"

SRC=app/src/main/java/br/com/meucontrole/financeiro
OUT=build/domain-tests
rm -rf "$OUT" && mkdir -p "$OUT"

FILES=$(find "$SRC/domain" "$SRC/backup" -name '*.kt'; find app/src/test/java -name '*.kt')

echo "Compilando $(echo "$FILES" | wc -l) arquivos..."
java -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -cp "$LIBS" -d "$OUT" $FILES 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' || true

[ -d "$OUT/br" ] || { echo "Falha na compilação"; exit 1; }

CLASSES=$(cd "$OUT" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g')
echo "Executando: $CLASSES"
java -cp "$OUT:$LIBS" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'
