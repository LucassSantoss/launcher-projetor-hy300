#!/usr/bin/env bash
# Copia as pastas de trabalho para este repositorio (sem builds, chaves nem APKs).
# Uso: bash scripts/sincronizar.sh
set -euo pipefail
cd "$(dirname "$0")/.."
MLAB="$USERPROFILE/OneDrive/Área de Trabalho/MLAB/HY300"

copiar() { # origem destino [exclusoes...]
  local src="$1" dst="$2"; shift 2
  rm -rf "$dst" && mkdir -p "$dst"
  (cd "$src" && tar cf - --exclude=build "$@" .) | (cd "$dst" && tar xf - --warning=no-timestamp)
}

copiar /c/dev/projetor-app app
copiar /c/dev/projetor-instalador instalador --exclude=assets/Projetor.apk
copiar "$MLAB/kit" kit --exclude=Projetor.apk
copiar "$MLAB/site" site --exclude=downloads

# Nunca publicar chaves, senhas ou APKs.
if find . -path ./.git -prune -o \( -name '*.jks' -o -name '*.pk8' -o -name '*.keystore' -o -name '*senha*' -o -name '*.apk' \) -print | grep -q .; then
  echo "ERRO: arquivo sensivel ou APK no repositorio"; exit 1
fi
echo "sincronizado"
