#!/data/data/com.termux/files/usr/bin/sh
# Build manual do Peso a dois (sem Gradle). Uso: sh build.sh
# Etapas: testes do núcleo -> aapt2 -> javac -> StripMethodParameters -> d8 -> zipalign -> apksigner.
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_HOME=${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk}
export JAVA_HOME
JAVAC="$JAVA_HOME/bin/javac"
KEYTOOL="$JAVA_HOME/bin/keytool"
SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-35
RESOURCE_SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-9
TOOLS_DIR=/data/data/com.termux/files/usr/bin
MANIFEST="$PROJECT_DIR/AndroidManifest.xml"
OUT="$PROJECT_DIR/build"
DELIVERY_DIR=/storage/emulated/0/Documents/APK
# Keystore fora do repositório (nunca commitar). Mesmo alias e senha das versões anteriores.
KEY_DIR="$HOME/.android-keys"
KEYSTORE="$KEY_DIR/peso-a-dois.keystore"
RES_COMPILED="$OUT/res-compiled.zip"
RES_APK="$OUT/resources.apk"
GEN="$OUT/gen"
CLASSES="$OUT/classes"
DEX="$OUT/dex"
TOOL_CLASSES="$OUT/tool-classes"

# A versão vem do manifest, que é a fonte única (aapt2 lê versionCode/versionName dele).
VERSION_CODE=$(sed -n 's/.*android:versionCode="\([0-9][0-9]*\)".*/\1/p' "$MANIFEST" | head -n 1)
VERSION_NAME=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFEST" | head -n 1)
if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
    printf 'Versão não encontrada em %s\n' "$MANIFEST" >&2
    exit 1
fi
printf 'Compilando versão %s (código %s)\n' "$VERSION_NAME" "$VERSION_CODE"

# Testes JVM do núcleo (src/com/pesoadois/core): se algum falhar, o build para aqui.
sh "$PROJECT_DIR/tests/run.sh"

rm -rf "$GEN" "$CLASSES" "$DEX" "$TOOL_CLASSES" "$RES_COMPILED" "$RES_APK" \
    "$OUT/classes.jar" "$OUT/unsigned.apk" "$OUT/Peso-a-dois-aligned.apk" \
    "$OUT/Peso-a-dois.apk"
mkdir -p "$GEN" "$CLASSES" "$DEX"
mkdir -p "$DELIVERY_DIR"

"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/res" -o "$RES_COMPILED"
"$TOOLS_DIR/aapt2" link \
    -I "$RESOURCE_SDK_DIR/android.jar" \
    --manifest "$MANIFEST" \
    --java "$GEN" \
    --min-sdk-version 26 \
    --target-sdk-version 28 \
    --auto-add-overlay \
    -o "$RES_APK" -R "$RES_COMPILED"

find "$PROJECT_DIR/src" "$GEN" -type f -name '*.java' -print > "$OUT/sources.list"
"$JAVAC" --release 8 -encoding UTF-8 -Xlint:all -Xlint:-options \
    -classpath "$SDK_DIR/android.jar" \
    -d "$CLASSES" \
    @"$OUT/sources.list"

# O javac do JDK 21 grava o atributo MethodParameters nos construtores de classes internas
# não estáticas (parâmetros sem nome, flags synthetic/mandated). Algumas versões do D8 avisam
# ou rejeitam esse atributo, e o ART pode lançar MalformedParametersException em reflexão.
# O D8 atual aceita, mas o passo é barato e funciona como rede de segurança. NÃO remover.
mkdir -p "$TOOL_CLASSES"
"$JAVAC" --release 8 -Xlint:-options -d "$TOOL_CLASSES" "$PROJECT_DIR/tools/StripMethodParameters.java"
"$JAVA_HOME/bin/java" -cp "$TOOL_CLASSES" StripMethodParameters "$CLASSES"

jar cf "$OUT/classes.jar" -C "$CLASSES" .
"$TOOLS_DIR/d8" --release --min-api 26 --lib "$SDK_DIR/android.jar" \
    --output "$DEX" "$OUT/classes.jar"

cp "$RES_APK" "$OUT/unsigned.apk"
jar uf "$OUT/unsigned.apk" -C "$DEX" classes.dex
"$TOOLS_DIR/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/Peso-a-dois-aligned.apk"

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
if [ ! -f "$KEYSTORE" ]; then
    "$KEYTOOL" -genkeypair -noprompt \
        -keystore "$KEYSTORE" \
        -storepass pesadois \
        -keypass pesadois \
        -alias pesadois \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Peso a dois, OU=Local, O=Peso a dois, L=Local, ST=SP, C=BR"
    chmod 600 "$KEYSTORE"
    printf 'Keystore criado: %s (guarde um backup; sem ele não há atualização de versão)\n' "$KEYSTORE"
fi

"$TOOLS_DIR/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:pesadois \
    --key-pass pass:pesadois \
    --out "$OUT/Peso-a-dois.apk" \
    "$OUT/Peso-a-dois-aligned.apk"

"$TOOLS_DIR/apksigner" verify --verbose "$OUT/Peso-a-dois.apk"
cp "$OUT/Peso-a-dois.apk" "$DELIVERY_DIR/Peso-a-dois.apk"
printf 'APK criado: %s\n' "$OUT/Peso-a-dois.apk"
printf 'APK entregue em: %s\n' "$DELIVERY_DIR/Peso-a-dois.apk"
