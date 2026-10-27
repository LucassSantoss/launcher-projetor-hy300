#!/usr/bin/env bash
# Compila o overlay de tema do sistema (sem codigo) e assina com a chave de plataforma.
set -euo pipefail
export JAVA_HOME=/c/dev/android/jdk17 PATH="/c/dev/android/jdk17/bin:$PATH"
cd "$(dirname "$0")"
SDK=/c/dev/android/sdk; BT=$SDK/build-tools/34.0.0; AJ=$SDK/platforms/android-30/android.jar; KEYS=/c/dev/android/keys
rm -rf build && mkdir -p build
"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/unsigned.apk -I "$AJ" --manifest AndroidManifest.xml \
  --min-sdk-version 28 --target-sdk-version 30 --version-code 1 --version-name 1.0 build/res.zip
"$BT/zipalign.exe" -p -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner.bat" sign --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" --out build/ProjetorTema.apk build/aligned.apk
ls -l build/ProjetorTema.apk
