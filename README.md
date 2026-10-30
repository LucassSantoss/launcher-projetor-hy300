# Projetor — MLAB SOLUCTION

Tela inicial nova e controle pelo celular para projetores **HY300** e parecidos (Android 11, Allwinner).

- Tela inicial com relógio, favoritos, recentes e TV ao vivo
- Celular como controle (iPhone ou Android, pelo navegador, via QR code)
- Voz e Atalhos da Siri
- Atualização automática e botão para voltar à tela original

**Guia com fotos e downloads:** https://mlabsoluction.pages.dev
**APKs:** veja em [Releases](../../releases)

## Como instalar

| Jeito | Precisa de |
|---|---|
| Direto no projetor | Pendrive (ou o app Downloader da Play Store) |
| Instalador Android | Celular Android no mesmo Wi‑Fi, ADB ligado no projetor |
| Claude Code / kit Windows | Computador no mesmo Wi‑Fi, ADB ligado no projetor |

O passo a passo de cada um está no site.

## Pastas

| Pasta | O que é |
|---|---|
| `app/` | App Projetor (`com.mlab.projetor`). Compila com `bash build.sh` no Windows ou `bash build-macos.sh` no macOS (aapt2, javac, d8, sem Gradle) |
| `instalador/` | Instalador para celular Android (`com.mlab.instalador`): cliente ADB por Wi‑Fi em Java puro |
| `kit/` | Instalar e desinstalar pelo Windows (`instalar.bat`, `desinstalar.bat`) |
| `site/` | Site publicado (Cloudflare Pages). Fonte em `site-fonte/` |
| `scripts/` | `sincronizar.sh` copia as pastas de trabalho para cá |

### Compilar no macOS

Os `build-macos.sh` (em `app/`, `app/overlay/` e `instalador/`) usam as mesmas versões do Windows: JDK 17, build-tools 34.0.0 e android-30. O `scripts/ambiente-macos.sh` procura cada coisa sozinho:

| O quê | Onde procura |
|---|---|
| JDK 17 | `JAVA_HOME`, `~/dev/android/jdk17`, o `openjdk@17` do Homebrew e as JVMs do sistema |
| SDK | `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `~/dev/android/sdk` e `~/Library/Android/sdk` (Android Studio) |
| Chaves | sempre `~/dev/android/keys` |

Para forçar outro caminho, defina `JAVA_HOME`, `ANDROID_HOME` ou `ANDROID_DEV` (que move a pasta das chaves): `ANDROID_DEV=/caminho bash app/build-macos.sh`.

**1. JDK 17** (precisa ser a 17: o `javac` roda com `-source 8` e o `d8` do build-tools 34 não acompanha versões mais novas). Com o Homebrew:

```bash
brew install openjdk@17
```

Não é preciso ligá-la ao sistema: o script acha a do Homebrew mesmo que o `java -version` aponte outra.

**2. SDK do Android** (build-tools 34.0.0 e android-30). Se você já tem o Android Studio com essas versões, pule este passo. Senão:

```bash
brew install --cask android-commandlinetools
```

```bash
yes | sdkmanager --sdk_root="$HOME/dev/android/sdk" "build-tools;34.0.0" "platforms;android-30"
```

Em Macs com Apple Silicon, o `aapt2` do build-tools 34 é x86_64 e precisa do Rosetta: `softwareupdate --install-rosetta`.

**3. Chaves de assinatura.** O `Projetor.apk` e o overlay são assinados com a chave de teste `platform` do AOSP, a mesma do firmware do HY300. Ela é pública, mas não vem neste repositório (o `.gitignore` bloqueia `*.pk8`). Baixe do AOSP oficial (https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security/) ou do espelho no GitHub:

```bash
mkdir -p ~/dev/android/keys
git clone --depth 1 --filter=blob:none --sparse https://github.com/aosp-mirror/platform_build.git /tmp/aosp-build
```

```bash
cd /tmp/aosp-build && git sparse-checkout set target/product/security
```

```bash
cp target/product/security/platform.pk8 target/product/security/platform.x509.pem ~/dev/android/keys/
```

Confira a impressão digital do certificado:

```bash
openssl x509 -in ~/dev/android/keys/platform.x509.pem -noout -fingerprint -sha1
```

Deve ser `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA`. O `build-macos.sh` imprime o SHA-1 do APK no final; se for o mesmo valor, ele foi assinado com essa chave.

**4. Compilar:**

```bash
bash app/build-macos.sh
```

O APK sai em `app/build/Projetor.apk`. Para instalar, desinstale antes qualquer versão anterior sem `sharedUserId`: o Android recusa a troca (`INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`).

**5. Instalar no projetor pelo ADB.** O `adb` vem no `platform-tools`, que não faz parte do build-tools:

```bash
brew install --cask android-platform-tools
```

Com a depuração USB ou por rede ativada no projetor, conecte (pule este passo se for por USB):

```bash
adb connect IP_DO_PROJETOR:5555
```

Desinstale a versão anterior (necessário por causa do `sharedUserId`, ver passo 4) e instale a nova:

```bash
adb uninstall com.mlab.projetor
```

```bash
adb install app/build/Projetor.apk
```

Se aparecer `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`, a versão antiga ainda está instalada. Se o erro for de assinatura, o firmware do aparelho não usa a chave `platform` do AOSP.

Só para quem vai compilar o app de celular: o `instalador/build-macos.sh` usa uma chave **própria** (`keys/instalador.jks` e `keys/instalador-senha.txt`, com uma linha `senha: ...`). Ela não é a do AOSP e nunca deve ir para o repositório. Para criar uma:

```bash
keytool -genkeypair -keystore ~/dev/android/keys/instalador.jks -alias instalador -keyalg RSA -keysize 2048 -validity 10000
```

```bash
echo "senha: SUA_SENHA" > ~/dev/android/keys/instalador-senha.txt
```

## Iniciar o projetor direto no app

> **Esta seção é um passo manual e opcional, feito por `adb` em cada aparelho.** O app **não** desativa tela inicial nenhuma: instalar o APK apenas o torna a tela inicial padrão (via `RoleManager`), e a de fábrica continua instalada e ativa. Num aparelho em que estes comandos não forem rodados, não existe o risco de ficar sem tela inicial descrito mais abaixo — o preço é que o boot ainda cai na tela de fábrica.

Definir o app como tela inicial padrão **não basta** em alguns firmwares. O sistema escolhe a tela inicial ainda na fase de boot travado (antes do `LOCKED_BOOT_COMPLETED`), quando só apps `directBootAware` contam. Nesse instante o app ainda não existe, e o firmware abre a tela inicial de fábrica, que fica na frente. O `BOOT_COMPLETED` chega uns 6 segundos depois, tarde demais.

A forma que resolve é deixar um único launcher disponível, desativando o de fábrica. No projetor testado (um `t950s_be311`, Amlogic, Android 13) ele é o `com.htc.htclauncherhighenglishd08`. **Em outro aparelho o nome quase certamente é outro**, então descubra o do seu antes de desativar qualquer coisa:

```bash
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Desative o de fábrica (troque pelo pacote que apareceu):

```bash
adb shell pm disable-user --user 0 com.htc.htclauncherhighenglishd08
```

### Como voltar atrás

`disable-user` **não apaga nada**: o APK continua em `/system/app/` e o pacote segue instalado, só fica escondido da lista de telas iniciais. Para reativar:

```bash
adb shell pm enable com.htc.htclauncherhighenglishd08
```

Vale anotar esse comando fora do projetor. Se o aparelho ficar sem nenhuma tela inicial, a tela fica vazia, mas o Android continua rodando e o adb não depende do launcher. Nos firmwares de desenvolvimento (`ro.debuggable=1`) o adb por rede já vem ligado na porta 5555 e sobrevive a reinicializações:

```bash
adb connect IP_DO_PROJETOR:5555 && adb shell pm enable com.htc.htclauncherhighenglishd08
```

Os Ajustes do Android também abrem sem tela inicial:

```bash
adb shell am start -n com.android.tv.settings/.MainSettings
```

O "Voltar à tela original e remover o app" do próprio app religa a tela inicial de fábrica antes de se remover, e se recusa a continuar se não houver nenhuma para devolver.

### Atualizar o app depois disso

Com a tela inicial de fábrica desativada, o app passa a ser a **única** do aparelho. Isso muda o que cada comando significa:

| Comando | O que acontece |
|---|---|
| `adb install -r Projetor.apk` | Seguro. É o caminho normal para novas versões. |
| `adb uninstall com.mlab.projetor` | **Deixa o projetor sem nenhuma tela inicial.** |

A atualização por cima preserva tudo: PIN, favoritos e o papel de tela inicial. O app reinicia sozinho pelo `MY_PACKAGE_REPLACED` e o controle do celular volta junto. Se outro app estiver em primeiro plano na hora, ele continua na frente — o Android bloqueia início de activity em segundo plano; basta apertar HOME.

```bash
adb install -r app/build/Projetor.apk
```

Duas condições para o `-r` funcionar: a **mesma chave de assinatura** e um `versionCode` **igual ou maior**. Rebaixar a versão é recusado.

Só é preciso desinstalar quando a chave de assinatura muda. Nesse caso, **reative a tela inicial de fábrica antes**, senão o aparelho fica sem nenhuma entre a desinstalação e a instalação:

```bash
adb shell pm enable com.htc.htclauncherhighenglishd08
```

```bash
adb uninstall com.mlab.projetor && adb install app/build/Projetor.apk
```

Depois, desative de novo se quiser voltar a iniciar direto no app.

## Segurança

- O app roda como **app de sistema**: é assinado com a chave de teste pública do AOSP, a mesma do firmware desses projetores. Em aparelhos com outro firmware, o Android recusa a instalação.
- Como essa chave é pública, as **atualizações automáticas** só são aceitas com uma assinatura ECDSA própria (`version.json`), cuja chave privada não está neste repositório.
- O controle pelo celular só funciona na rede local e pede um PIN de 6 dígitos sorteado em cada projetor.

## Aviso

Sem vínculo com os fabricantes dos projetores nem com os apps citados. Sem garantia: use por sua conta.

© MLAB SOLUCTION
