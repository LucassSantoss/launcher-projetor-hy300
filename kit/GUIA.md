# App Projetor 4.3 — guia

O **Projetor** troca a tela inicial do seu projetor por uma mais bonita e rápida e transforma o seu celular em controle remoto. Não precisa instalar nada no celular: o controle abre no navegador (Safari ou Chrome).

O que você ganha:
- **Tela inicial nova**, com relógio grande, seus apps favoritos em cartões coloridos e os ajustes à mão. Tem tema escuro e claro.
- **Controle pelo celular**: setas, OK, voltar, volume, teclado para digitar senhas e buscas, e um modo mouse.
- **Comandos de voz**: "abrir Netflix", "volume 30", "buscar Interestelar no YouTube".
- **Siri e Atalhos** (iPhone): ligar a Netflix ou mudar o volume só falando.
- **TV ao vivo**: canais abertos brasileiros que transmitem pela internet.
- **Enviar para a tela**: mande um link, uma foto ou um vídeo do celular direto para o projetor.

O app **não apaga nada** do projetor. A tela inicial original continua lá, e dá para voltar a ela quando quiser.

---

## 1. Antes de começar

Você precisa de:
- um **computador com Windows 10 ou 11**;
- o **projetor e o computador no mesmo Wi‑Fi**;
- um projetor **compatível**. O app foi feito para o **HY300** e modelos parecidos (Android 11, processador Allwinner H713). Se o seu não for compatível, o instalador avisa e **não muda nada**.

No projetor, o **acesso ADB** (também chamado de "depuração USB") precisa estar ligado. Em muitos HY300 ele já vem ligado. Se o instalador não encontrar o projetor, faça assim:
1. No projetor, abra **Configurações → Preferências do dispositivo → Sobre**.
2. Aperte OK **7 vezes** em **Build** (ou "Versão"). Vai aparecer "Você agora é um desenvolvedor".
3. Volte e entre em **Opções do desenvolvedor**. Ligue **Depuração USB**.

## 2. Instalar

1. Descompacte o kit numa pasta do computador.
2. Dê dois cliques em **`instalar.bat`**.
   - Se o Windows mostrar "O Windows protegeu o computador", clique em **Mais informações → Executar assim mesmo**.
3. Se o computador não tiver a ferramenta **ADB** (do Google), o instalador pergunta se pode baixá‑la. Responda **S**. Ela é baixada do site do Google, e o instalador confere a assinatura digital antes de usar.
4. O instalador procura o projetor no seu Wi‑Fi e mostra uma lista. Digite o número do seu projetor.
   - Se não aparecer, escolha **D** e digite o endereço IP do projetor. Ele fica em **Configurações → Rede e Internet**, no nome do seu Wi‑Fi.
   - Se o projetor mostrar **"Permitir depuração USB?"**, marque **Sempre permitir** e escolha **OK** com o controle.
5. Pronto: o app é instalado e vira a tela inicial. O modo mouse do fabricante é desligado, porque o app tem o seu próprio.
6. No final, o instalador pergunta se pode **desligar o acesso ADB**. Responda **S** (recomendado). Veja o porquê em [Segurança](#7-segurança).

> Se aparecer **"Este projetor não é compatível com o app"**, o sistema do seu aparelho é diferente do que o app precisa. Nada foi alterado e o projetor continua como estava.

## 3. Usar a tela inicial

- **Abrir um app:** use as setas do controle e aperte OK.
- **Favoritos:** em **Apps**, segure OK sobre um app para adicioná‑lo ou removê‑lo dos favoritos (até 8). Na tela inicial, segurar OK sobre um favorito também o remove.
- **Recentes:** o que você assistiu por mais de 5 minutos aparece embaixo do relógio.
- **Dock** (a barra de baixo): Wi‑Fi, Bluetooth, Ajustes, Apps e o botão de tema **Claro/Escuro**. Nos projetores que têm, também aparecem Keystone (corrigir o formato da imagem), Imagem e HDMI.
- **Celular** (no Dock): mostra o **QR code** do controle. Aponte a câmera do celular e o controle abre já conectado. Aperte Voltar para fechar.
- **Barra de cima:** mostra o **endereço do controle** e o **PIN**.

## 4. Controle pelo celular

1. Deixe o celular no **mesmo Wi‑Fi** do projetor.
2. Na tela inicial do projetor, aperte o botão **Celular** do Dock. Abra a **câmera** do celular, aponte para o **QR code** e toque no link que aparecer. O controle abre já conectado, sem digitar nada.
3. Sem câmera? No navegador do celular, digite o endereço da barra de cima da tela inicial (por exemplo, `http://192.168.0.20:8080`) e depois o **PIN de 6 dígitos**.
4. Para virar um ícone na tela do celular:
   - **iPhone (Safari):** Compartilhar → **Adicionar à Tela de Início**.
   - **Android (Chrome):** menu ⋮ → **Adicionar à tela inicial**.

A página tem três abas:
- **Controle**
  - Deslize na área grande para mover a seleção. Toque para dar OK.
  - Botões de Voltar, Início e Play/Pause.
  - Volume deslizante.
  - **Teclado**: escreve no projetor (senhas, buscas).
  - **Microfone** (no centro): comandos de voz.
  - Seus **favoritos**: toque para abrir no projetor.
- **Mouse**: um dedo move a seta; um toque clica; tocar e segurar faz o clique longo; dois dedos rolam a tela.
- **Enviar**: cole um link para abrir no projetor ou mande uma foto ou vídeo da galeria.

Em **Ajustes** (ícone no topo da página):
- **Gerar novo PIN**: troca o PIN na hora. Os outros celulares e os atalhos da Siri vão precisar do PIN novo.
- **Acesso pelo PC (ADB)**: deixe **desligado**. Ligue só quando for desinstalar o app pelo computador.
- **Esquecer este aparelho**: apaga o PIN salvo neste celular.

> **Dica:** no roteador, reserve um IP fixo para o projetor (procure por "reserva de DHCP" ou "IP fixo"). Assim o endereço não muda e o ícone e os atalhos continuam funcionando.

## 5. Comandos de voz

Toque no **microfone** e fale. No iPhone, se o microfone não abrir, toque no microfone do **teclado** do iPhone e dite.

Exemplos:
- "abrir Netflix", "abrir Globoplay", "tela inicial", "voltar";
- "buscar Interestelar no YouTube", "buscar Round 6 na Netflix";
- "pausar", "continuar", "avançar", "próximo episódio";
- "volume 30", "aumentar o volume", "mudo";
- "abrir YouTube e buscar receita de bolo" (dá para juntar comandos);
- "desligar".

Se o comando não for reconhecido, o app faz uma busca com o que você falou.

## 6. Siri e Atalhos (iPhone)

Nos exemplos abaixo, troque:
- **`IP`** pelo endereço do projetor (o do rodapé, sem o `:8080`);
- **`PIN`** pelo seu PIN de 6 dígitos.

### Atalho "Projetor" (o mais prático)
1. Abra o app **Atalhos** e toque em **+**. Dê o nome **Projetor**.
2. Adicione estas ações, nesta ordem:
   1. **Ditar texto** (idioma: Português).
   2. **Obter conteúdo do URL**:
      - URL: `http://IP:8080/api/voz?t=PIN`
      - Método: **POST**
      - Corpo da solicitação: **JSON**, com um campo `s` = **Texto ditado**.
   3. **Obter valor do dicionário**, com a chave `fala`.
   4. **Falar texto**.
3. Diga **"E aí Siri, Projetor"** e, quando ela perguntar, fale o comando. Funciona até com o iPhone bloqueado.

### Atalho de um toque (exemplo: "Netflix no projetor")
1. No **Atalhos**, toque em **+** e adicione **Obter conteúdo do URL**.
2. URL: `http://IP:8080/api/open?app=netflix&t=PIN`
3. Dê o nome **Netflix no projetor** e diga: **"E aí Siri, Netflix no projetor"**.

Outros endereços que você pode usar no lugar:

| O que faz | Endereço |
|---|---|
| Abrir um app | `http://IP:8080/api/open?app=netflix&t=PIN` |
| Apertar uma tecla | `http://IP:8080/api/key?k=playpause&t=PIN` |
| Volume em 30% | `http://IP:8080/api/volume?set=30&t=PIN` |
| Aumentar, baixar ou mudo | `http://IP:8080/api/volume?dir=up&t=PIN` (`up`, `down` ou `mute`) |
| Abrir um link | `http://IP:8080/api/url?u=LINK&t=PIN` |
| Escrever um texto | `http://IP:8080/api/text?s=TEXTO&enter=1&t=PIN` |
| Ligar ou desligar | `http://IP:8080/api/power?t=PIN` |

- **Apps:** `netflix`, `youtube`, `disney`, `prime`, `globoplay`, `spotify`, `espn`, `tv`, `wifi`, `bluetooth`, `configuracoes`, `inicio`, ou qualquer nome da lista de apps.
- **Teclas:** `up`, `down`, `left`, `right`, `ok`, `back`, `home`, `menu`, `playpause`, `play`, `pause`, `fwd`, `rew`, `next`, `prev`, `vol_up`, `vol_down`, `mute`, `power`.

### "Enviar ao Projetor" no menu Compartilhar
1. Crie um atalho chamado **Enviar ao Projetor**. Nos detalhes (ícone ⓘ), ligue **Mostrar na Planilha de Compartilhamento** e escolha o tipo **URLs**.
2. Adicione **Obter conteúdo do URL**: URL `http://IP:8080/api/url?t=PIN`, Método **POST**, Corpo **JSON** com o campo `u` = **Entrada do Atalho**.
3. No YouTube ou no Safari: **Compartilhar → Enviar ao Projetor**.

> Se você gerar um PIN novo, troque o PIN também nos atalhos.

## 7. Segurança

- **Cada projetor tem o seu PIN**, sorteado na primeira vez que o app abre. Ninguém mais tem esse número.
- O controle **só funciona dentro da sua rede**. O app recusa conexões que venham da internet. **Não** abra a porta 8080 no roteador.
- **PIN errado:** depois de 10 tentativas erradas, aquele aparelho fica travado por 1 minuto (e mais tempo a cada nova rodada). Se houver muitas tentativas erradas na mesma hora, o controle trava por 15 minutos e o projetor avisa na tela.
- Achou que alguém descobriu o PIN? Abra **Ajustes → Gerar novo PIN**.
- **Use um Wi‑Fi com senha.** A conexão entre o celular e o projetor não é criptografada; numa rede aberta, alguém por perto poderia ver o PIN.
- **Desligue o acesso ADB** depois de instalar. Com ele ligado, em muitos projetores desse tipo, qualquer computador no seu Wi‑Fi consegue controlar o aparelho por completo, sem senha. O instalador oferece desligar no final; depois, use **Ajustes → Acesso pelo PC (ADB)** no celular.
- Por que o app precisa de tantas permissões? Para apertar teclas, mudar o volume e trocar a tela inicial, ele roda como **app do sistema**. Isso só é possível em projetores cujo sistema foi feito com a chave de teste pública do Android, como o HY300. Nos outros, o Android simplesmente recusa a instalação.

## 8. Desinstalar

1. Se você desligou o acesso ADB, ligue de novo: no celular, abra o controle → **Ajustes → Acesso pelo PC (ADB)**. (Ou pelo projetor: **Opções do desenvolvedor → Depuração USB**.)
2. No computador, dê dois cliques em **`desinstalar.bat`** e escolha o projetor.
3. O desinstalador:
   - volta para a **tela inicial original**;
   - **remove** o app Projetor;
   - **religa o modo mouse** do fabricante;
   - pergunta se pode **reiniciar** o projetor (recomendado: desfaz também o ajuste de áudio da Netflix);
   - oferece desligar o acesso ADB de novo.

## 9. Problemas comuns

| Problema | O que fazer |
|---|---|
| O instalador não acha o projetor | Confira se estão no mesmo Wi‑Fi e se a Depuração USB está ligada (seção 1). Tente digitar o IP com a opção **D**. |
| "Este projetor não é compatível" | O sistema do aparelho não aceita o app. Nada foi alterado. |
| O celular não abre a página | Confira o Wi‑Fi do celular (não use dados móveis) e o endereço do rodapé. O IP pode ter mudado. |
| "PIN incorreto" | Confira o PIN no rodapé do projetor. Se alguém gerou um novo, use o novo. |
| A Netflix dá erro 5.0 nos anúncios | O app aplica um ajuste automático a cada vez que o projetor liga. Se continuar, reinicie o projetor. |
| Quero a tela antiga de volta | Rode o `desinstalar.bat` (seção 8). |
