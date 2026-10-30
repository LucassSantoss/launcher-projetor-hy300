#!/usr/bin/env bash
# Variaveis comuns dos build-macos.sh (usado com "source"). Mesmas versoes do Windows:
# JDK 17, build-tools 34.0.0 e android-30. Tudo fica em ANDROID_DEV (padrao: ~/dev/android):
#   jdk17/  sdk/  keys/   (mesma estrutura de C:\dev\android)
# Para usar outra pasta: ANDROID_DEV=/caminho bash build-macos.sh

ANDROID_DEV=${ANDROID_DEV:-$HOME/dev/android}

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "$ANDROID_DEV/jdk17/Contents/Home" ]]; then
    JAVA_HOME=$ANDROID_DEV/jdk17/Contents/Home   # tar.gz do Temurin no macOS
  elif [[ -d "$ANDROID_DEV/jdk17" ]]; then
    JAVA_HOME=$ANDROID_DEV/jdk17
  elif [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17)    # JDK 17 instalado no sistema
  else
    echo "ERRO: JDK 17 nao encontrado. Coloque em $ANDROID_DEV/jdk17 ou defina JAVA_HOME." >&2
    exit 1
  fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

SDK=$ANDROID_DEV/sdk
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-30/android.jar
KEYS=$ANDROID_DEV/keys

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$AJ"; do
  if [[ ! -e "$f" ]]; then
    echo "ERRO: nao encontrei $f (instale build-tools 34.0.0 e android-30 em $SDK)." >&2
    exit 1
  fi
done
