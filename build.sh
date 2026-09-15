#!/data/data/com.termux/files/usr/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-35
RESOURCE_SDK_DIR=/data/data/com.termux/files/home/.cache/android-api/android-9
TOOLS_DIR=/data/data/com.termux/files/usr/bin
OUT="$PROJECT_DIR/build"
DELIVERY_DIR=/storage/emulated/0/Documents
RES_COMPILED="$OUT/res-compiled.zip"
RES_APK="$OUT/resources.apk"
GEN="$OUT/gen"
CLASSES="$OUT/classes"
DEX="$OUT/dex"
TOOL_CLASSES="$OUT/tool-classes"

rm -rf "$GEN" "$CLASSES" "$DEX" "$TOOL_CLASSES" "$RES_COMPILED" "$RES_APK" \
    "$OUT/classes.jar" "$OUT/unsigned.apk" "$OUT/Peso-a-dois-aligned.apk" \
    "$OUT/Peso-a-dois.apk"
mkdir -p "$GEN" "$CLASSES" "$DEX"
mkdir -p "$DELIVERY_DIR"

"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/res" -o "$RES_COMPILED"
"$TOOLS_DIR/aapt2" link \
    -I "$RESOURCE_SDK_DIR/android.jar" \
    --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$GEN" \
    --min-sdk-version 26 \
    --target-sdk-version 28 \
    --version-code 1 \
    --version-name 1.0.0 \
    --auto-add-overlay \
    -o "$RES_APK" -R "$RES_COMPILED"

find "$PROJECT_DIR/src" "$GEN" -type f -name '*.java' -print > "$OUT/sources.list"
javac --release 8 -encoding UTF-8 \
    -classpath "$SDK_DIR/android.jar" \
    -d "$CLASSES" \
    @"$OUT/sources.list"

mkdir -p "$TOOL_CLASSES"
javac --release 8 -d "$TOOL_CLASSES" "$PROJECT_DIR/tools/StripMethodParameters.java"
java -cp "$TOOL_CLASSES" StripMethodParameters "$CLASSES"

jar cf "$OUT/classes.jar" -C "$CLASSES" .
"$TOOLS_DIR/d8" --release --min-api 26 --lib "$SDK_DIR/android.jar" \
    --output "$DEX" "$OUT/classes.jar"

cp "$RES_APK" "$OUT/unsigned.apk"
jar uf "$OUT/unsigned.apk" -C "$DEX" classes.dex
"$TOOLS_DIR/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/Peso-a-dois-aligned.apk"

if [ ! -f "$OUT/peso-a-dois-release.keystore" ]; then
    keytool -genkeypair -noprompt \
        -keystore "$OUT/peso-a-dois-release.keystore" \
        -storepass pesadois \
        -keypass pesadois \
        -alias pesadois \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Peso a dois, OU=Local, O=Peso a dois, L=Local, ST=SP, C=BR"
fi

"$TOOLS_DIR/apksigner" sign \
    --ks "$OUT/peso-a-dois-release.keystore" \
    --ks-pass pass:pesadois \
    --key-pass pass:pesadois \
    --out "$OUT/Peso-a-dois.apk" \
    "$OUT/Peso-a-dois-aligned.apk"

"$TOOLS_DIR/apksigner" verify --verbose "$OUT/Peso-a-dois.apk"
cp "$OUT/Peso-a-dois.apk" "$DELIVERY_DIR/Peso-a-dois.apk"
printf 'APK criado: %s\n' "$OUT/Peso-a-dois.apk"
printf 'APK entregue em: %s\n' "$DELIVERY_DIR/Peso-a-dois.apk"
