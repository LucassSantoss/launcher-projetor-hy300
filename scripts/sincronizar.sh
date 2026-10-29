#!/usr/bin/env bash
# Copia as pastas de trabalho para este repositorio (sem builds, chaves nem APKs).
# Uso: bash scripts/sincronizar.sh
set -euo pipefail

copiar() { # origem destino [exclusoes...]
  local src="$1" dst="$2" tmp old; shift 2
  if [[ ! -d "$src" ]]; then
    echo "ERRO: origem nao encontrada: $src"
    return 1
  fi
  tmp=$(mktemp -d "${dst}.novo.XXXXXX")
  if ! (cd "$src" && tar cf - --exclude=build "$@" .) | (cd "$tmp" && tar xf -); then
    rm -rf "$tmp"
    return 1
  fi
  old="${dst}.anterior"
  rm -rf "$old"
  if [[ -e "$dst" ]] && ! mv "$dst" "$old"; then
    rm -rf "$tmp"
    return 1
  fi
  if mv "$tmp" "$dst"; then
    rm -rf "$old"
  else
    [[ ! -e "$old" ]] || mv "$old" "$dst"
    rm -rf "$tmp"
    return 1
  fi
}

if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then return 0; fi

cd "$(dirname "$0")/.."
MLAB="$USERPROFILE/OneDrive/Área de Trabalho/MLAB/HY300"

copiar /c/dev/projetor-app app
copiar /c/dev/projetor-instalador instalador --exclude=assets/Projetor.apk
copiar "$MLAB/kit" kit --exclude=Projetor.apk
copiar "$MLAB/site" site --exclude=downloads

# Nunca publicar chaves, senhas ou APKs.
if find . -path ./.git -prune -o \( -name '*.jks' -o -name '*.pk8' -o -name '*.keystore' -o -name '*senha*' -o -name '*.apk' \) -print | grep -q .; then
  echo "ERRO: arquivo sensivel ou APK no repositorio"; exit 1
fi
echo "sincronizado"
