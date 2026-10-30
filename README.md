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

Os `build-macos.sh` (em `app/`, `app/overlay/` e `instalador/`) usam as mesmas versões do Windows: JDK 17, build-tools 34.0.0 e android-30. Por padrão procuram tudo em `~/dev/android` (`jdk17/`, `sdk/` e `keys/`, a mesma estrutura de `C:\dev\android`). Para usar outra pasta, defina `ANDROID_DEV`:

```bash
ANDROID_DEV=/caminho/android bash app/build-macos.sh
```

Em Macs com Apple Silicon, o `aapt2` do build-tools 34 é x86_64 e precisa do Rosetta (`softwareupdate --install-rosetta`).

## Segurança

- O app roda como **app de sistema**: é assinado com a chave de teste pública do AOSP, a mesma do firmware desses projetores. Em aparelhos com outro firmware, o Android recusa a instalação.
- Como essa chave é pública, as **atualizações automáticas** só são aceitas com uma assinatura ECDSA própria (`version.json`), cuja chave privada não está neste repositório.
- O controle pelo celular só funciona na rede local e pede um PIN de 6 dígitos sorteado em cada projetor.

## Aviso

Sem vínculo com os fabricantes dos projetores nem com os apps citados. Sem garantia: use por sua conta.

© MLAB SOLUCTION
