#!/usr/bin/env bash
# Versao macOS do build.sh: aapt2 -> javac -> d8 -> zipalign -> apksigner (chave de plataforma AOSP).
# Pastas do JDK/SDK/chaves: ver scripts/ambiente-macos.sh (padrao ~/dev/android).
set -euo pipefail
cd "$(dirname "$0")"
source ../scripts/ambiente-macos.sh

# Le a versao do AndroidManifest.xml (o grep -P do Linux/Git Bash nao existe no macOS)
VERSION_CODE=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' AndroidManifest.xml | head -1)
VERSION_NAME=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}
echo "Compilando versão $VERSION_NAME (code $VERSION_CODE)..."

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets \
  --java build/gen --min-sdk-version 28 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build/res.zip

javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -classpath "$BT/core-lambda-stubs.jar" -Xlint:-options \
  -d build/classes $(find src build/gen -name '*.java')

"$BT/d8" --release --min-api 28 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

python3 -I - <<'PY'
import shutil, zipfile
shutil.copy('build/base.apk', 'build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex', 'classes.dex')
PY

"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" \
  --out build/Projetor.apk build/aligned.apk
"$BT/apksigner" verify --print-certs build/Projetor.apk | grep -i "SHA-1"
ls -l build/Projetor.apk
