#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/sincronizar.sh

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/origem" "$tmp/destino"
printf 'novo\n' > "$tmp/origem/arquivo.txt"
printf 'antigo\n' > "$tmp/destino/antigo.txt"

copiar "$tmp/origem" "$tmp/destino"
[[ "$(cat "$tmp/destino/arquivo.txt")" == "novo" ]]
[[ ! -e "$tmp/destino/antigo.txt" ]]

if copiar "$tmp/inexistente" "$tmp/destino"; then
  echo "FALHA: origem inexistente foi aceita"
  exit 1
fi
[[ "$(cat "$tmp/destino/arquivo.txt")" == "novo" ]]

echo "OK: sincronizacao preserva o destino quando a origem falha"
