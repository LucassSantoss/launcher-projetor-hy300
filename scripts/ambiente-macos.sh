#!/usr/bin/env bash
# Variaveis comuns dos build-macos.sh (usado com "source"). Mesmas versoes do Windows:
# JDK 17, build-tools 34.0.0 e android-30.
#
# As chaves ficam sempre em ANDROID_DEV/keys (padrao: ~/dev/android/keys).
# O JDK e o SDK sao procurados em varios lugares (ver abaixo); para forcar um
# caminho, defina JAVA_HOME, ANDROID_HOME ou ANDROID_DEV antes de chamar o build.

ANDROID_DEV=${ANDROID_DEV:-$HOME/dev/android}

# --- JDK 17 ---------------------------------------------------------------
# Precisa ser a 17: o javac roda com -source 8 -target 8 e o d8 do build-tools 34
# nao acompanha versoes mais novas.
jdk_e_17() {
  [[ -x "$1/bin/javac" ]] && "$1/bin/javac" -version 2>&1 | grep -q ' 17\.'
}

if [[ -z "${JAVA_HOME:-}" ]]; then
  # Caminhos fixos primeiro; depois as JVMs instaladas no sistema (via find, que
  # nao depende do shell expandir "*" quando nada casa).
  candidatos=$(
    printf '%s\n' \
      "$ANDROID_DEV/jdk17/Contents/Home" \
      "$ANDROID_DEV/jdk17" \
      /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
      /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    find /Library/Java/JavaVirtualMachines "$HOME/Library/Java/JavaVirtualMachines" \
      -maxdepth 1 -name '*17*' 2>/dev/null | sed 's|$|/Contents/Home|'
  )
  while IFS= read -r candidato; do
    if jdk_e_17 "$candidato"; then JAVA_HOME=$candidato; break; fi
  done <<< "$candidatos"
fi
# Ultimo recurso: o java_home do sistema. O "-v 17" aceita versoes maiores, entao
# o resultado ainda precisa ser conferido (num Mac so com a 24, ele devolve a 24).
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  candidato=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  if [[ -n "$candidato" ]] && jdk_e_17 "$candidato"; then JAVA_HOME=$candidato; fi
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "ERRO: JDK 17 nao encontrado. Instale com 'brew install openjdk@17'" >&2
  echo "      ou coloque em $ANDROID_DEV/jdk17, ou defina JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# --- SDK do Android -------------------------------------------------------
# Aceita a pasta do ANDROID_DEV e tambem a do Android Studio (~/Library/Android/sdk).
if [[ -z "${ANDROID_HOME:-}" ]]; then
  for candidato in \
      "$ANDROID_DEV/sdk" \
      "${ANDROID_SDK_ROOT:-}" \
      "$HOME/Library/Android/sdk"; do
    if [[ -n "$candidato" && -d "$candidato/build-tools/34.0.0" ]]; then
      ANDROID_HOME=$candidato; break
    fi
  done
fi
SDK=${ANDROID_HOME:-$ANDROID_DEV/sdk}
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-30/android.jar
KEYS=$ANDROID_DEV/keys

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$AJ"; do
  if [[ ! -e "$f" ]]; then
    echo "ERRO: nao encontrei $f" >&2
    echo "      Instale build-tools 34.0.0 e android-30, ou defina ANDROID_HOME." >&2
    exit 1
  fi
done
