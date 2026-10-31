# Nova tela inicial em listas (estilo Google TV / Projectivy)

Telas de referência (1280×720, a resolução do HY300), geradas a partir de `mockup.html`:

| Arquivo | O que mostra |
|---|---|
| `tela-1.png` | Abertura: foco em **Favoritos**, destaque do app focado no topo, medidores ligados |
| `tela-2.png` | Usuário desceu até **Conexões**, foco em "Controle no celular" |
| `tela-3.png` | Última lista, **Sistema** |

## 1. Ideia da tela

```
┌──────────────────────────────────────────────────────────────────────┐
│ 21:47  sábado, 31 de outubro        [📱 IP:8080 · PIN] [wifi][bt][⚙] │  barra de status (sem foco)
│                                                                      │
│ FILMES E SÉRIES                               (42°)(CPU)(RAM)(Esp)(⇣)│  destaque: muda com o foco
│ Netflix                                                              │  + medidores (opcional)
│ Filmes, séries e documentários.                                      │
│                                                                      │
│ Favoritos                                                            │  ← lista focada (100% opaca)
│ [YouTube] [NETFLIX] [Globoplay] [Prime] [Disney+] [Spotify] [ + ]  ▶ │    rola na horizontal
│ Continuar assistindo                                                 │  ← demais listas a 45%
│ [Avatar…] [Jornal Nacional] [Lo-fi…] [TV ao vivo]                    │
│ Projetor                                                             │
│ [Keystone] [Imagem] [HDMI] [TV ao vivo]                              │
│ Conexões                                                             │
│ [Wi‑Fi] [Bluetooth] [Controle no celular]                            │
│ Sistema                                                              │
│ [Ajustes do Android] [Todos os apps] [Tema] [Medidores] [Internet]…  │
└──────────────────────────────────────────────────────────────────────┘
```

- **◀ ▶** anda dentro da lista; **▲ ▼** troca de lista. A tela rola na vertical para que a lista focada fique sempre na mesma altura (logo abaixo do destaque), como no Google TV.
- O **destaque** (tipo, nome e uma frase) mostra o item focado; atrás dele, um brilho na cor do app (`Ui.appColor`, que já existe). Sem imagens da internet, então nada pesa nem depende de rede.
- A barra de status é só informação, sem foco, para a navegação ficar simples. Tudo que ela mostra também está nas listas.

### Listas e de onde vem cada item

| Lista | Itens | Origem no código de hoje |
|---|---|---|
| Favoritos | Artes 16:9 dos apps + cartão "+" | `Apps.favorites`, `Apps.banner`, `tile()`, `addTile()` |
| Continuar assistindo | Últimos 4 recentes, agora **com foco** (OK abre o app) | `Prefs.recents`, `loadRecents()` |
| Projetor | Keystone, Imagem, HDMI (só se o aparelho tiver), TV ao vivo | `Hardware.findAvailable(...)` do `dock()` |
| Conexões | Wi‑Fi (com nome da rede), Bluetooth, Controle no celular (abre o QR) | `dock()` + `toggleQr()` |
| Sistema | Ajustes do Android, Todos os apps, Tema claro/escuro, Mostrar/ocultar medidores, Testar internet, Procurar atualização, Novo PIN, Voltar à tela original | hoje dentro de `settingsMenu()` e do Dock; cada opção vira um cartão que chama o **mesmo** método |

Listas sem itens (por exemplo, Recentes vazio) não aparecem.

## 2. O que NÃO muda

- **Controle físico e controle pelo celular.** Os dois chegam como teclas DPAD normais (o `Controller` injeta com `SOURCE_DPAD`), então tudo passa pelo mesmo `dispatchKeyEvent` da Activity. `KeyService`, `Controller`, `WebServer` e `assets/web/index.html` não são tocados.
- **"Selecionado: X" no celular**: cada item focável continua chamando `App.inst.controller.onScreen(getPackageName(), label)` e tem `contentDescription`.
- **Atalhos de teclas**: OK abre; segurar OK abre opções; Voltar fecha o QR (e não sai da tela inicial).
- `AppsActivity`, `TvActivity`, `Prefs`, `Apps`, atualização automática, remoção do app, tema claro/escuro, QR do controle, medidores e teste de internet: mesma lógica, só mudam de lugar.
- Manifesto, permissões, build (`build.sh`, sem Gradle e sem androidx).

## 3. Implementação

### 3.1 Estrutura de views (só `android.widget`, sem bibliotecas novas)

```
FrameLayout (raiz, papel de parede Ui.wallpaper)
├── View tint            brilho na cor do app focado (GradientDrawable radial)
├── LinearLayout top     relógio (TextClock), data, chips de status
├── LinearLayout hero    kind / name / desc  +  medidores (UsageRing, já existentes)
├── ScrollView rowsScroll   (setFocusable(false), setSmoothScrollingEnabled(true))
│   └── LinearLayout rows (vertical)
│       ├── Row "Favoritos"  = título + HorizontalScrollView > LinearLayout itens
│       ├── Row "Continuar assistindo"
│       ├── ...
└── qrPanel (igual ao de hoje)
```

Um novo arquivo pequeno, `Row.java`, monta uma lista: título, `HorizontalScrollView` sem barra e a fileira de itens, e guarda `lastIndex` (o último item focado nela).

### 3.2 Navegação (o ponto mais importante)

Não confiar no `FocusFinder` padrão para ▲ ▼: entre listas de tamanhos diferentes ele pula para o item mais próximo na tela, e o foco "anda de lado". Regras:

1. `HomeActivity.dispatchKeyEvent` trata só `ACTION_DOWN` de `DPAD_UP` e `DPAD_DOWN` quando o QR está fechado:
   - acha a lista atual (`rowOf(getCurrentFocus())`);
   - vai para a lista de cima/baixo e foca o item `lastIndex` dela (limitado ao tamanho), como no Google TV;
   - na primeira lista, ▲ não faz nada; na última, ▼ não faz nada (não sai da tela);
   - devolve `true`. Todas as outras teclas seguem o caminho normal (`super.dispatchKeyEvent`).
2. ◀ ▶ ficam com o comportamento padrão dentro do `LinearLayout` horizontal; no primeiro e no último item, `setNextFocusLeftId/RightId` apontam para o próprio item, para o foco não escapar para outra lista.
3. A cada foco: atualiza `lastIndex`, o destaque e o `onScreen(...)`, depois rola:
   - na horizontal, `hsv.smoothScrollTo(item.getLeft() - margem, 0)` quando o item sair da área visível;
   - na vertical, `rowsScroll.smoothScrollTo(0, top da lista anterior)`, para a lista focada ficar sempre na segunda posição (ou na primeira, se for a de Favoritos).
4. Lógica de índices em uma classe Java pura (`RowNav`: `next(rowAtual, direção, tamanhos, lastIndex)`), testada no estilo de `tests/test_favorites.py`, sem Android.
5. Ao voltar para a tela inicial (`onResume`), o foco volta ao último item usado; na primeira abertura, ao primeiro favorito.

### 3.3 Segurar OK (opções)

Hoje segurar OK num favorito remove na hora. Proposta: abrir um diálogo curto **Mover para a esquerda / Mover para a direita / Remover dos favoritos**, com o mesmo `dialog()` de hoje. Para mover, um `Prefs.moveFav(pkg, delta)` novo, reaproveitando a lista de `Prefs.favs` (com teste em `test_favorites.py`). Nos outros cartões, segurar OK não faz nada.

### 3.4 Visual e foco

- Favoritos: 212×119 dp (16:9), cantos de 12 dp. No foco: escala 1,12 e contorno branco de 3 dp (`Ui.RING`).
- Cartões de ajuste: 150×96 dp, vidro (`Ui.glass`). No foco ficam sólidos e claros (`Ui.focusable(...)`, que já existe, com escala 1,12).
- Lista sem foco: `setAlpha(0.45f)`, com animação de 150 ms. A lista focada fica com alpha 1.
- Tipografia: Inter (já embarcada). Relógio 30, nome no destaque 46, título da lista 17, rótulo 13.
- Os dois temas continuam: as cores vêm de `Ui.applyTheme`.

### 3.5 Leveza (HY300: Allwinner H713, ~1 GB de RAM)

- Sem `RecyclerView`, sem Leanback e sem imagens da rede. São cerca de 30 a 40 views no total.
- Ícones e banners carregados uma vez em `onResume` (como hoje). `Ui.appColor` calculado uma vez por app e guardado num `HashMap`.
- Sombra (`elevation`) só no item focado. Itens parados ficam sem sombra, que custa caro na GPU Mali.
- Animações só de `scale`, `alpha` e `scrollTo`, com 150 a 160 ms.
- "Todos os apps" continua como tela separada (`AppsActivity`), para não carregar dezenas de ícones na tela inicial.

## 4. Passos sugeridos (cada um vira um commit testável no projetor)

1. `Row.java` + `RowNav.java` + teste de navegação.
2. Nova `build()` da `HomeActivity`: barra de status, destaque, listas Favoritos e Projetor. Dock e coluna do relógio saem.
3. Listas Conexões e Sistema, que movem as opções de `settingsMenu()` para cartões. `settingsMenu()` fica para o botão MENU do controle.
4. Recentes com foco e brilho na cor do app.
5. Opções ao segurar OK (mover/remover) + `Prefs.moveFav` + teste.
6. Teste no aparelho: controle físico, controle pelo celular (setas, OK, Voltar, "Selecionado: …"), tema claro e escuro, sem Wi‑Fi, sem favoritos, aparelho sem Keystone/HDMI.

## 5. Critérios de aceite

- Todos os itens podem ser alcançados só com ▲ ▼ ◀ ▶ OK, no controle físico e no celular.
- ▲ ▼ sempre voltam ao último item usado em cada lista, e o foco nunca some nem sai da tela.
- O celular mostra "Selecionado: <item>" a cada movimento.
- Toda função que existe hoje (Dock + Ajustes) tem um cartão na nova tela.
- Tela inicial abre em menos de 1 s depois de Home, e o uso de memória fica igual ao de hoje (±10%).
