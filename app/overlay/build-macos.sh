#!/usr/bin/env bash
# Versao macOS do build.sh: compila o overlay de tema (sem codigo) e assina com a chave de plataforma.
set -euo pipefail
cd "$(dirname "$0")"
source ../../scripts/ambiente-macos.sh

rm -rf build && mkdir -p build
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$AJ" --manifest AndroidManifest.xml \
  --min-sdk-version 28 --target-sdk-version 30 --version-code 1 --version-name 1.0 build/res.zip
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" --out build/ProjetorTema.apk build/aligned.apk
ls -l build/ProjetorTema.apk
