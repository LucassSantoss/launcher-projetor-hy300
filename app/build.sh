#!/usr/bin/env bash
# Compila o Projetor.apk sem Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner (chave de plataforma AOSP).
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME=/c/dev/android/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
SDK=/c/dev/android/sdk
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-30/android.jar
KEYS=/c/dev/android/keys

# Lê a versão do AndroidManifest.xml (não usa mais valores hardcoded)
VERSION_CODE=$(grep -oP 'android:versionCode="\K[0-9]+' AndroidManifest.xml || echo "1")
VERSION_NAME=$(grep -oP 'android:versionName="\K[^"]+' AndroidManifest.xml || echo "1.0")
echo "Compilando versão $VERSION_NAME (code $VERSION_CODE)..."

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets \
  --java build/gen --min-sdk-version 28 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build/res.zip

javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -classpath "$BT/core-lambda-stubs.jar" -Xlint:-options \
  -d build/classes $(find src build/gen -name '*.java')

"$BT/d8.bat" --release --min-api 28 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

python -I - <<'EOF'
import shutil, zipfile
shutil.copy('build/base.apk', 'build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex', 'classes.dex')
EOF

"$BT/zipalign.exe" -p -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" \
  --out build/Projetor.apk build/aligned.apk
"$BT/apksigner.bat" verify --print-certs build/Projetor.apk | grep -i "SHA-1"
ls -l build/Projetor.apk
