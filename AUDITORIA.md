# Auditoria técnica do Projetor HY300

Data da revisão: 28 de outubro de 2026.

Esta auditoria cobre o app Android do projetor, o instalador Android via ADB, o kit Windows, o site e os scripts de build e sincronização. A prioridade é preservar a segurança do projetor, os ajustes existentes e a possibilidade de voltar ao sistema original.

A carga esperada é doméstica: um projetor, poucos celulares conectados e uma rede Wi-Fi compartilhada. Como o PIN existe para separar aparelhos confiáveis dos demais aparelhos da rede, um cliente local desconhecido ou comprometido também faz parte do modelo de risco.

## Resumo

Os itens 1 a 6 foram corrigidos na working tree em 28 de outubro de 2026. A versão 4.5.4 foi compilada, assinada com a mesma chave de plataforma do firmware e instalada como atualização em um projetor real (Askey ADT3, Android 11). Os testes de rede e preservação do estado atual passaram; os fluxos destrutivos ou que encerrariam o acesso ADB continuam pendentes, como detalhado em **Validação no projetor real**.

Nos itens marcados como corrigidos, a descrição do problema e os exemplos preservam o comportamento anterior para explicar a origem da correção. O campo **Status** descreve o estado atual.

| Prioridade | Quantidade | Significado |
|---|---:|---|
| Corrigidos, com validação parcial no HY300 | 6 | Bug, risco de segurança, perda de dados ou quebra de contrato existente |
| Corrigir em seguida | 5 | Limite ausente, risco de release ou teste que não protege o comportamento |
| Melhoria simples | 1 | Inconsistência documental sem impacto no funcionamento |

## Corrigir antes de publicar

### 1. Conexões podem esgotar o processo sem conhecer o PIN

**Status:** corrigido e validado no projetor com 8 workers, fila de 16 conexões e no máximo 4 WebSockets simultâneos.

**Evidência:** [`WebServer.java`](app/src/com/mlab/projetor/WebServer.java), agora com `ThreadPoolExecutor` limitado e rejeição explícita. A versão auditada originalmente usava `newCachedThreadPool()` e atendia cada socket antes da autenticação.

O servidor cria uma nova thread para cada conexão aceita. O PIN só é verificado depois que a conexão já ganhou uma thread e começou a enviar a requisição.

**Exemplo do problema**

Um aparelho na mesma rede abre 500 conexões na porta 8080 e não envia a primeira linha HTTP. Cada conexão pode ficar esperando por até 30 segundos. O projetor passa a manter centenas de threads e sockets, podendo encerrar o launcher por falta de memória.

O ataque não exige PIN:

```text
cliente local -> abre socket -> não envia dados -> thread fica ocupada
cliente local -> repete centenas de vezes -> processo perde memória e descritores
```

**Correção mínima sugerida**

Usar um número fixo de workers, uma fila curta e fechar imediatamente o socket rejeitado. O número precisa ser calibrado no HY300 real; o exemplo mostra a forma, não valores definitivos:

```java
private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
        4, 4, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(16));

try {
    pool.execute(() -> handle(socket));
} catch (RejectedExecutionException e) {
    socket.close();
}
```

Também deve existir um limite pequeno de WebSockets simultâneos, pois eles permanecem abertos sem timeout de leitura.

**Como verificar**

- Abrir mais conexões do que o limite e confirmar que as excedentes são fechadas.
- Confirmar que o controle autenticado continua respondendo durante o teste.
- Monitorar threads, memória e descritores no projetor.

### 2. A sincronização apaga o destino antes de validar a origem

**Status:** corrigido com cópia para diretório temporário, troca somente após sucesso e restauração do destino se a troca falhar.

**Evidência:** [`scripts/sincronizar.sh`](scripts/sincronizar.sh), função `copiar`.

A função remove a pasta do repositório antes de executar `cd` e `tar` na origem. Se a origem estiver ausente ou a cópia falhar, o destino já foi destruído.

**Exemplo do problema**

```text
/c/dev/projetor-app não está montado
-> rm -rf app é executado
-> cd /c/dev/projetor-app falha
-> o script encerra por set -e
-> app/ permanece apagada
```

Arquivos rastreados podem ser recuperados pelo Git, mas arquivos locais ainda não commitados são perdidos.

**Correção mínima sugerida**

Copiar para uma pasta temporária ao lado do destino e trocar as pastas somente depois de a cópia terminar com sucesso:

```bash
tmp=$(mktemp -d "${dst}.novo.XXXXXX")
(cd "$src" && tar cf - --exclude=build "$@" .) |
  (cd "$tmp" && tar xf - --warning=no-timestamp)

old="${dst}.anterior"
mv "$dst" "$old"
if mv "$tmp" "$dst"; then
  rm -rf "$old"
else
  mv "$old" "$dst"
  exit 1
fi
```

A implementação deve ainda limpar temporários com `trap` e validar que `src` é um diretório antes de iniciar.

**Como verificar**

- Executar com uma origem inexistente e confirmar que o destino não muda.
- Interromper uma cópia de teste e confirmar que o destino anterior continua íntegro.
- Executar o fluxo normal e comparar as árvores copiadas.

### 3. A remoção não restaura exatamente os ajustes originais

**Status:** corrigido para novas instalações. Acessibilidade e estado do componente de mouse são guardados antes da primeira alteração; instalações antigas mantêm a restauração legada porque o estado anterior já não pode ser descoberto.

**Evidência:** [`App.java`](app/src/com/mlab/projetor/App.java), método `takeOverSystemUi`; [`Sistema.java`](app/src/com/mlab/projetor/Sistema.java), método `restoreSystem`; e [`kit.ps1`](kit/ferramentas/kit.ps1), que salva `a11y_ativo`, mas não o usa na restauração.

Na instalação, o app ativa acessibilidade, adiciona o próprio serviço e desativa o modo mouse do fabricante. Na remoção, ele presume que o serviço do fabricante deve ficar ativo, em vez de restaurar os valores observados antes da instalação.

**Exemplo do problema**

Antes da instalação:

```text
accessibility_enabled = 0
modo mouse do fabricante = desativado pelo usuário
```

Depois de instalar e remover:

```text
accessibility_enabled = 1
modo mouse do fabricante = ativado
```

O app foi removido, mas o projetor não voltou ao estado anterior.

**Correção mínima sugerida**

Na primeira execução, antes de qualquer alteração, guardar:

- `enabled_accessibility_services`;
- `accessibility_enabled`;
- estado do componente `MOUSE_SVC`;
- tela inicial original, como já é feito.

Na remoção, restaurar os valores salvos literalmente. O kit Windows deve usar também o `a11y_ativo` já presente no backup.

```java
if (!prefs.contains("estado_original_salvo")) {
    prefs.edit()
            .putString("a11y_original", currentServices)
            .putInt("a11y_ativo_original", currentEnabled)
            .putInt("mouse_original", currentMouseState)
            .putBoolean("estado_original_salvo", true)
            .commit();
}
```

Versões que já foram instaladas não conseguem descobrir retroativamente todos os valores originais. Essa limitação precisa ser tratada de forma explícita na migração.

**Como verificar**

- Testar instalação e remoção partindo de acessibilidade ligada e desligada.
- Testar com o modo mouse do fabricante ligado e desligado.
- Comparar os valores antes da instalação e depois da remoção.

### 4. O instalador pode dizer que o ADB foi desligado quando não foi

**Status:** corrigido. O instalador só confirma o desligamento quando a porta 5555 fecha e o controle na porta 8080 continua acessível; nos demais casos pede conferência manual.

**Evidência:** [`Instalacao.java`](instalador/src/com/mlab/instalador/Instalacao.java), método `desligarAdb`, e [`MainActivity.java`](instalador/src/com/mlab/instalador/MainActivity.java), método de mesmo nome.

Qualquer `IOException` é ignorada. Em seguida, a interface sempre mostra “Acesso ADB desligado”.

**Exemplo do problema**

```text
socket ADB já foi encerrado
-> settings put global adb_enabled 0 não é enviado
-> IOException é ignorada
-> tela mostra “Tudo certo”
-> porta 5555 continua aberta
```

**Correção mínima sugerida**

Separar três resultados: confirmado, solicitação enviada sem confirmação e falha antes do envio. Depois do comando, tentar uma nova conexão curta à porta 5555, como o kit Windows já faz.

```java
enum AdbDisableResult {
    CONFIRMED,
    SENT_NOT_CONFIRMED,
    FAILED
}
```

Se a confirmação não for possível, a mensagem deve ser honesta: “Solicitação enviada; confirme nas Opções do desenvolvedor”.

**Como verificar**

- Desligar ADB num firmware que fecha a porta e esperar `CONFIRMED`.
- Simular socket fechado antes do comando e esperar `FAILED`.
- Testar firmware que ignora a opção e confirmar que a interface não declara sucesso.

### 5. O resultado final da atualização não é recebido

**Status:** corrigido com receiver explícito e não exportado, `PendingIntent` mutável quando necessário e tratamento de sucesso, falha e confirmação do usuário.

**Evidência:** [`Updater.java`](app/src/com/mlab/projetor/Updater.java), método `install`, e [`AndroidManifest.xml`](app/AndroidManifest.xml), que não declara receiver para `com.mlab.projetor.ATUALIZADO`.

`PackageInstaller.Session.commit()` é assíncrono. Erros ocorridos depois do `commit` chegam pelo `IntentSender`, mas não há componente que os receba. A variável `installing` só é limpa nas exceções síncronas.

**Exemplo do problema**

```text
download e SHA-256 passam
-> session.commit() retorna normalmente
-> Android rejeita a sessão por falta de espaço
-> broadcast de resultado não encontra receiver
-> installing continua true
-> novas tentativas são ignoradas
```

**Correção mínima sugerida**

Adicionar um receiver não exportado e usar um `Intent` explícito para ele:

```xml
<receiver
    android:name=".UpdateReceiver"
    android:exported="false" />
```

```java
Intent result = new Intent(context, UpdateReceiver.class);
PendingIntent pending = PendingIntent.getBroadcast(context, 1, result, appropriateFlags);
session.commit(pending.getIntentSender());
```

O receiver deve ler `PackageInstaller.EXTRA_STATUS`, limpar `installing` quando houver falha e apresentar a mensagem real. Os flags do `PendingIntent` devem ser escolhidos de acordo com a API efetivamente usada no build e testados no Android 11 do projetor.

**Como verificar**

- Publicar uma sessão inválida de teste e confirmar que o erro aparece.
- Confirmar que uma segunda tentativa é permitida após a falha.
- Confirmar que a atualização válida ainda reinicia o app e preserva os ajustes.

### 6. Encerrar o instalador com uma conexão aberta pode causar crash

**Status:** corrigido fechando o socket diretamente antes de encerrar o executor.

**Evidência:** [`MainActivity.java`](instalador/src/com/mlab/instalador/MainActivity.java), métodos `onDestroy` e `fechar`.

`onDestroy` executa `worker.shutdownNow()` e depois chama `fechar`. Quando existe um cliente ADB, `fechar` tenta enviar `c::close` ao executor já encerrado.

**Exemplo do problema**

```text
adb != null
-> onDestroy chama shutdownNow()
-> fechar chama worker.execute(c::close)
-> RejectedExecutionException
-> Activity encerra com crash
```

**Correção mínima sugerida**

Fechar o socket diretamente e só depois encerrar o executor. `Socket.close()` é a operação que também desbloqueia uma leitura pendente.

```java
private void fechar() {
    AdbClient client = adb;
    adb = null;
    if (client != null) client.close();
}

@Override
protected void onDestroy() {
    fechar();
    worker.shutdownNow();
    super.onDestroy();
}
```

**Como verificar**

- Conectar a um projetor e sair da Activity.
- Girar a tela durante a confirmação.
- Confirmar que não há crash nem socket remanescente.

## Corrigir em seguida

### 7. A lista IPTV pode consumir toda a memória

**Evidência:** [`Iptv.java`](app/src/com/mlab/projetor/Iptv.java), métodos `download` e `readAll`.

A resposta remota é copiada integralmente para um `ByteArrayOutputStream`. Uma resposta muito grande pode matar o processo do launcher em aparelhos com pouca memória.

**Exemplo do problema**

O endereço remoto responde acidentalmente com centenas de megabytes. O app mantém todo o conteúdo em memória antes de analisar a lista.

**Correção mínima sugerida**

```java
private static final int MAX_PLAYLIST_BYTES = 5 * 1024 * 1024;

if (output.size() + count > MAX_PLAYLIST_BYTES) {
    throw new IOException("lista IPTV grande demais");
}
```

O limite deve ser maior que a lista real com margem, mas pequeno o suficiente para o hardware de 1 GB.

**Como verificar**

- Analisar a lista atual normalmente.
- Servir localmente uma resposta acima do limite e confirmar falha controlada.
- Confirmar que o cache anterior continua utilizável após a falha.

### 8. A saída recebida do servidor ADB não tem limite total

**Evidência:** [`AdbClient.java`](instalador/src/com/mlab/instalador/AdbClient.java), método `exec`.

Cada frame individual é limitado, mas a soma de todos os frames é acumulada sem limite.

**Exemplo do problema**

Um aparelho na porta 5555 aceita a conexão e envia continuamente frames `A_WRTE`. O instalador acumula todos eles até o processo ficar sem memória.

**Correção mínima sugerida**

Criar uma função única para acrescentar a saída e rejeitar o excesso:

```java
private static final int MAX_OUTPUT = 1024 * 1024;

private static void append(ByteArrayOutputStream output, byte[] data) throws IOException {
    if (output.size() + data.length > MAX_OUTPUT) {
        throw new IOException("resposta do projetor grande demais");
    }
    output.write(data);
}
```

**Como verificar**

- Executar os comandos normais de instalação.
- Usar um servidor ADB simulado que ultrapasse o limite.
- Confirmar que a conexão é fechada e o usuário recebe erro legível.

### 9. O build padrão produz uma versão antiga

**Evidência:** [`app/build.sh`](app/build.sh) e [`instalador/build.sh`](instalador/build.sh).

Os scripts usam `VERSION_CODE=1` quando a variável não é informada. A versão publicada em `site/version.json` usa código 50.

**Exemplo do problema**

```bash
bash app/build.sh
# produz versionCode 1
# Android recusa instalar sobre a versão 50 como downgrade
```

**Correção mínima sugerida**

Falhar cedo em vez de criar silenciosamente um APK incorreto:

```bash
: "${VERSION_CODE:?Defina VERSION_CODE}"
: "${VERSION_NAME:?Defina VERSION_NAME}"
```

O comando de release deve registrar os dois valores usados e conferir o APK final com `aapt2 dump badging` ou ferramenta equivalente já disponível no SDK.

**Como verificar**

- Rodar sem variáveis e confirmar erro imediato.
- Rodar com os valores da release e conferir o manifesto do APK.
- Confirmar que o instalador embute exatamente o APK recém-validado.

### 10. O teste de integração contém verificações que sempre passam

**Evidência:** [`TestePC.java`](instalador/tools/TestePC.java), verificações da tela inicial e da desinstalação com argumento literal `true`.

Esses passos aparecem como aprovados mesmo quando o resultado está errado.

**Exemplo atual**

```java
check("tela inicial atual: " + Instalacao.telaInicialAtual(adb), true);
check("removeu o pacote de teste: " + adb.shell("pm uninstall ..."), true);
```

**Correção mínima sugerida**

```java
String home = Instalacao.telaInicialAtual(adb);
check("tela inicial atual: " + home, !home.isEmpty());

String uninstall = adb.shell("pm uninstall com.mlab.projetor.teste");
check("removeu o pacote de teste: " + uninstall, uninstall.contains("Success"));
```

Os limites dos itens 1, 7 e 8 e a falha assíncrona do item 5 também precisam de um teste pequeno que falhe sem a correção.

**Como verificar**

- Forçar uma desinstalação inválida e confirmar que o teste falha.
- Executar o teste no HY300 suportado.
- Não tratar “compilou” como prova dos fluxos de hardware, rede ou atualização.

### 11. Redirecionamentos escapam da lista de hosts permitidos

**Evidência:** [`Updater.java`](app/src/com/mlab/projetor/Updater.java), métodos `allowed` e `open`.

O endereço inicial do APK é validado, mas os destinos recebidos pelo cabeçalho `Location` só precisam usar HTTPS. O SHA-256 assinado ainda impede instalar bytes diferentes, mas a promessa de consultar apenas hosts permitidos deixa de ser verdadeira.

**Exemplo do problema**

```text
https://github.com/.../Projetor.apk
-> HTTP 302 Location: https://servidor-nao-permitido.example/apk
-> open aceita o novo host porque ele usa HTTPS
```

**Correção mínima sugerida**

Validar cada URL resolvida antes de abrir a próxima conexão. Como o mesmo método busca `version.json`, a restrição deve ser explícita para o fluxo de download do APK.

```java
String next = new URL(new URL(url), location).toString();
if (restrictDownloadHosts && !allowed(next)) {
    throw new SecurityException("redirecionamento não permitido");
}
url = next;
```

**Como verificar**

- Aceitar a cadeia real usada pelo GitHub Releases.
- Rejeitar um redirecionamento para host fora da lista.
- Confirmar que HTTP e mais de cinco redirecionamentos continuam rejeitados.

## Melhoria simples

### 12. O título do guia está desatualizado

**Evidência:** [`kit/GUIA.md`](kit/GUIA.md) diz “App Projetor 4.3”, enquanto app, site e instalador usam 4.5.3.

**Exemplo do problema**

Uma pessoa baixa o kit 4.5.3, abre o guia e conclui que recebeu documentação da versão 4.3.

**Correção mínima sugerida**

Atualizar o título para 4.5.3 ou remover a versão do título para não manter o mesmo número em mais um lugar.

**Como verificar**

- Buscar números de versão no repositório antes de publicar.
- Confirmar que site, instalador, `version.json` e guia descrevem a mesma release.

## Próximos passos

1. Validar os caminhos destrutivos restantes dos itens 3 a 6 em um aparelho de teste ou com recuperação física disponível.
2. Corrigir os itens 7 a 11, começando pelos limites de memória.
3. Atualizar o guia do item 12.
4. Executar o build assinado e a conferência final da release.

Cada correção deve ser um diff pequeno e verificável. Mudanças nos limites de rede devem ser calibradas no HY300; mudanças em instalação, atualização e restauração devem ser testadas no hardware antes de uma release.

## Evidências coletadas nesta revisão

Passaram:

- `git diff --check`;
- sintaxe dos scripts Bash;
- teste local da sincronização segura, incluindo origem ausente;
- teste local de detecção de porta aberta e fechada;
- build sem assinatura dos dois módulos com AAPT2, `javac` e D8 usando Android SDK 35;
- build assinado do app 4.5.4 com certificado idêntico ao app instalado;
- atualização preservando dados no Askey ADT3 com Android 11;
- preservação do launcher padrão, acessibilidade e ADB após a atualização;
- servidor HTTP autenticado, limite de workers e recuperação após 32 conexões parciais;
- limite de WebSockets: quatro respostas `101` e a quinta resposta `503`;
- processo do app estável e nenhum `FATAL EXCEPTION` após os testes;
- compilação sintática do gerador Python;
- validação sintática dos utilitários Node;
- busca por formatos comuns de chaves e tokens, sem segredo versionado encontrado.

Ainda não foram executados:

- build assinado com a toolchain Windows do projeto;
- validação do PowerShell, pois `pwsh` não está instalado;
- geração completa do site, pois `site/downloads/Projetor.apk` não está no checkout;
- restauração exata após remover uma instalação nova, para não alterar o launcher e os ajustes do projetor em uso;
- desligamento real do ADB, para não perder o acesso remoto durante esta validação;
- retorno assíncrono do `PackageInstaller` em sucesso e falha, pois a atualização de teste foi feita pelo ADB;
- encerramento do instalador Android com conexão aberta em um telefone de teste.

## Validação no projetor real

Ambiente usado em 28 de outubro de 2026:

```text
fabricante/modelo: askey / adt3
Android: 11 (API 30)
app anterior: 4.5.3 (versionCode 50)
app instalado: 4.5.4 (versionCode 51)
assinatura SHA-256: c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```

A instalação foi feita com `adb install -r`, portanto manteve os dados existentes. Depois da atualização:

```text
launcher: com.mlab.projetor/.HomeActivity
accessibility_enabled: 0 (inalterado)
enabled_accessibility_services: vazio (inalterado)
adb_enabled: 1 (inalterado)
HTTP /: 200
/api/ping autenticado: {"ok":true,"auth":true}
```

No teste de 32 conexões HTTP parciais, o processo passou de 27 para 34 threads, em vez de criar uma thread por conexão. Durante a saturação, a conexão excedente foi recusada; após o fechamento das conexões de teste, o servidor voltou a responder `200` e manteve o mesmo PID.

No teste separado de WebSockets autenticados, os resultados foram:

```text
101, 101, 101, 101, 503
```

Isso confirma o limite de quatro controles simultâneos. A resposta `503` não derrubou o processo, e o log não continha `FATAL EXCEPTION` depois dos testes.

