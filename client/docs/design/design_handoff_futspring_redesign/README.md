# Handoff: Redesign FutSpring (shell com sidebar, Home, Pelada, Diária)

## Overview
Redesign das telas principais do client FutSpring (`client/`):
1. **App shell**: o `NavBar` sai e entra uma **sidebar** fixa com as peladas do usuário e o menu do usuário. O conteúdo ganha padding.
2. **Home** (`HomePage`): painel do que acontece agora (próximas sessões com confirmação rápida, números do usuário, lista de peladas).
3. **Pelada** (`PeladaDetailPage`): banner em card, abas Membros / Sessões / Ranking / Prêmios redesenhadas, chat em botão flutuante.
4. **Diária** (`DailyDetailPage`): três estados (Agendada, Em andamento, Finalizada) e o dialog "Lançar resultados".

## About the Design Files
Os arquivos `*.dc.html` deste pacote são **referências de design feitas em HTML**: protótipos que mostram o visual e o comportamento esperados. **Não são código de produção para copiar.** A tarefa é **recriar essas telas no código existente** (React 19 + TypeScript + Tailwind 3 + shadcn/ui + lucide-react), seguindo `client/docs/FRONTEND.md` e `core/docs/BACKEND.md`.

Para abrir: sirva a pasta com um servidor estático (`bunx serve .`) e abra cada `.dc.html`. O `support.js` precisa estar na mesma pasta. Em cada tela há props ("tweaks") para trocar estados: `status`, `viewport: mobile`, `isAdmin`, `hasNextSession`.

## Fidelity
**Alta fidelidade.** Cores, tipografia, espaçamentos, raios e interações são finais. Recrie pixel-perfect **usando os componentes shadcn e os tokens do projeto**. Os hex abaixo existem para conferência: mapeie cada um para um token semântico (`bg-card`, `border-border`, `text-muted-foreground`…) e crie tokens novos em `index.css` (`:root` **e** `.dark`) quando faltar. Não espalhe hex pelos componentes (antipattern #3 do FRONTEND.md). O design foi feito em dark mode; o light mode precisa funcionar com os mesmos tokens.

Os dados das telas são de exemplo. Use sempre a API.

---

## Design Tokens (dark)

| Uso | Hex no design | Token sugerido |
|---|---|---|
| Fundo da página | `#1a1a1a` | `--background` |
| Fundo da sidebar / barra mobile | `#151515` | novo `--sidebar` (ou o token do shadcn `sidebar`) |
| Card / painel | `#1e1e1e` | `--card` |
| Cabeçalho de tabela / card elevado | `#212121` | `--muted` (ou novo `--card-elevated`) |
| Hover de linha | `#202020` | `hover:bg-muted/50` |
| Item ativo / chip / pílula de contagem | `#262626` / `#2a2a2a` | `--accent` / `--secondary` |
| Avatar com iniciais | `#2e2e2e` | `--secondary` |
| Borda padrão | `#2a2a2a` | `--border` |
| Borda de input / botão outline | `#2e2e2e` | `--input` |
| Divisor de linha | `#242424` | `border-border/60` |
| Texto | `#fafafa` | `--foreground` |
| Texto secundário | `#a3a3a3` | `--muted-foreground` |
| Texto terciário / labels | `#8a8a8a`, `#737373` | novo `--subtle-foreground` |
| Brand (gradiente) | `#15803d → #16a34a` | `.bg-gradient-primary` / `Button variant="gradient"` (já existe) |
| Sucesso (texto/indicador) | `#4ade80`, `#22c55e` | novo `--success` |
| Fundo sucesso | `#14301d` | novo `--success-muted` |
| Ouro (líder, estrelas, 1º) | `#eab308` | `--gold` (já existe) |
| Prata / bronze | `#a3a3a3` / `#c2410c` | novos `--silver`, `--bronze` |
| Perigo | `#ef4444`, `#f87171` | `--destructive` |
| Status Agendada | bg `#1e293b`, texto `#93c5fd`, dot `#3b82f6` | novo `--status-scheduled*` |
| Status Em andamento | bg `#33290a`, texto `#fcd34d`, dot `#f59e0b` | novo `--status-live*` |
| Status Finalizada | bg `#2a2a2a`, texto `#d4d4d4`, dot `#a3a3a3` | `--secondary` |
| Status Confirmada | bg `#14301d`, texto `#86efac`, dot `#22c55e` | `--success*` |

Os mapas de status ficam em `types/daily.ts` como `Record<DailyStatus, …>` (padrão do FRONTEND.md).

**Cores dos times** vêm do backend (`team.color`). Os dots de cor têm sempre `border: 1px solid #4a4a4a`, para que Branco e Preto apareçam nos dois temas.

**Tipografia:** stack do sistema (`ui-sans-serif, system-ui, -apple-system, "Segoe UI"`), a mesma do app. Escala: 11 (labels uppercase, `letter-spacing .06em`, peso 600), 12, 13, 14 (corpo), 15–16 (títulos de card, 600), 17 (h2 de seção), 22–28 (h1 de página, 700, `letter-spacing -.01em`), 38 (saudação da Home, 800). Números usam `font-variant-numeric: tabular-nums`.

**Raios:** 999px (botões, pílulas, badges, tabs: o padrão `rounded-full` do projeto), 14px (cards grandes, banner, hero), 12px (cards e tabelas), 10px (itens da sidebar, menus), 8–9px (avatares quadrados de pelada), 16–18px (dialogs e bottom sheet).

**Espaçamento:** gaps de 4, 6, 8, 10, 12, 14, 16, 20, 24, 28 e 36px. Padding do conteúdo principal: **24–32px no desktop, 16px no mobile**. Largura máxima do conteúdo: **1120px** (Home e Diária).

**Sombras:** só em overlays. Menus: `0 10px 30px rgba(0,0,0,.45)`. Dialogs: `0 24px 60px rgba(0,0,0,.6)`. Botão flutuante: `0 8px 24px rgba(0,0,0,.45)`.

**Breakpoint:** mobile abaixo de 768px (`md`). O chat ficava fixo na lateral só com largura útil ≥ 1000px, mas por padrão ele **sempre abre pelo botão flutuante**.

---

## 1. App shell (todas as telas privadas)

Substitui o `NavBar`. Recomendação: `bunx shadcn@latest add sidebar` (rode antes com `--dry-run` e adapte ao Tailwind 3). Crie um `AppLayout` com `<Outlet/>` em `App.tsx` envolvendo as rotas privadas. Hoje não existe layout route, então essa é uma mudança estrutural: documente no FRONTEND.md.

**Sidebar desktop** (`width 264px`; recolhida `64px`; `sticky top:0; height:100vh`; `border-right 1px #2a2a2a`; fundo `#151515`; transição de largura .2s)
- **Topo** (altura 60px, padding 16/14): logo redondo de 30px e "FutSpring" (17px/700, gradiente brand no texto). À direita, botão de recolher (ícone `PanelLeft`, 28px). Recolhida, o logo some e o botão fica centralizado.
- **Nav:** "Início" (ícone `Home` 16px, 14px/500, padding 8/10, radius 8). Ativo: bg `#262626` e texto `#fafafa`; inativo: `#a3a3a3` com hover bg `#222`.
- **Seção "MINHAS PELADAS":** 11px/600 uppercase `#737373`. À direita, botão "+" redondo de 26px com borda (alinhado à direita, `margin-right:4px`; centralizado quando recolhida). Abre o `CreatePeladaModal`.
- **Item de pelada:** padding 8 (4 recolhida), radius 10. Avatar 34×34 com radius 9: imagem da pelada (`cover`, posição ~18%) ou gradiente com iniciais 12px/700. Ao lado, nome 14px/500 com ellipsis e linha secundária 12px `#8a8a8a` com a próxima sessão ("Qui, 08/10 · 21:00" ou "Sem sessão agendada"). Ativo: bg `#262626`. Dados: `getMyPeladas()` (`nextDailyDate`).
- **Rodapé (usuário):** botão inteiro (padding 8, radius 10) com avatar quadrado de 32px (radius 8), nome 14/600, email 12 `#a3a3a3` e ícone `ChevronsUpDown`. Abre um `DropdownMenu` **à direita da sidebar** (`side="right" align="end"`, largura 240) com:
  - cabeçalho com avatar, nome e email;
  - separador, Perfil (`User`), Notificações (`Bell`), Tema claro/escuro (`Sun`/`Moon`, troca o tema do NavBar atual);
  - separador e Sair (`LogOut`, texto `#ef4444`).

**Mobile (<768px)**
- Barra superior `sticky` de 56px (bg `#151515`, borda inferior) com logo, "FutSpring" e hambúrguer de 40px à direita.
- A sidebar vira um `Sheet side="left"` de 264px, com overlay `rgba(0,0,0,.6)`. Ela fecha ao escolher uma pelada.
- No mobile o menu do usuário abre **acima** do botão (`side="top"`).

---

## 2. Home (`Home.dc.html` → `pages/HomePage.tsx`)

Conteúdo com `max-width 1120`, `gap 36px` entre seções e padding 32 (16 no mobile).

**Banner/saudação** (substitui o hero de marketing): `radius 18`, altura mínima 240 (220 no mobile).
- **Fundo:** foto `public/ronaldo.jpg` com `grayscale(1) contrast(1.05)`, opacidade .55 e posição `right 30%`. Por cima, gradiente `90deg rgba(14,14,14,.95) 0% → .75 45% → .25 100%`.
- **Padding:** 32/36 (22/20 no mobile). Conteúdo com `flex-wrap`, alinhado embaixo e espaçado entre as pontas.
- **Selo da data:** "Domingo, 4 de outubro", pílula 12/600 com bg `rgba(21,128,61,.35)`, borda `rgba(74,222,128,.35)` e texto `#bbf7d0`.
- **H1:** "Boa noite, {username}" em 38px/800 (28 no mobile). A saudação muda pelo horário: bom dia / boa tarde / boa noite.
- **Linha dinâmica** (15px `#d4d4d4`): "Você tem N sessões esperando sua confirmação." ou "Presença confirmada em todas as próximas sessões."
- **Botão "Nova pelada"** (gradient, 42px de altura, ícone `Plus`).

**Próximas sessões:** h2 17/600 e a contagem 13 `#8a8a8a`.
- **Desktop:** grade `repeat(auto-fill, minmax(300px,1fr))` com gap 12.
- **Mobile:** **carrossel horizontal**: `grid-auto-flow: column`, coluna `calc((100% - 44px)/1.3)` (aparece ~30% do próximo card), `scroll-snap-type: x mandatory`, `scroll-snap-align: start`, scrollbar escondida. O carrossel sangra até a borda (`margin 0 -16px; padding 0 16px`).
- **Card de sessão** (radius 14, padding 14, gap 14, borda `#2a2a2a`; com o usuário confirmado a borda vira `#1f4d2e`):
  1. Linha: avatar da pelada (24px, radius 7), nome 14/600 com ellipsis e badge de status 11px à direita.
  2. "Qui, 08 out" 22/700 com o horário 15/500 `#a3a3a3` ao lado. Abaixo, o local com ícone `MapPin` 12px, 12px `#8a8a8a`, em uma linha com ellipsis.
  3. Barra de 6px (trilho `#2a2a2a`, preenchimento gradiente brand) e "**12** de 20 confirmados" em 12px.
  4. Não confirmado: botão "Confirmar presença" de largura total (gradient, 38px). Confirmado: pílula de 38px com bg `#14301d`, "✓ Você vai" (13/600 `#4ade80`) e o botão ghost "Desistir" (12px `#86efac`).
  5. Link "Ver sessão →" centralizado, 13px `#a3a3a3` → `/daily/:id`.

**Seus números:** h2 e "em todas as peladas". À direita, "Ver perfil →". Grade `repeat(auto-fit, minmax(150px,1fr))` com 4 KPIs (radius 12, padding 14/16): label 12 `#a3a3a3`, valor 26/700 e sub 12 `#737373`.
- Gols ("0,40 por partida")
- Assistências
- Partidas ("em N sessões")
- Vitórias ("53% de aproveitamento")

Fonte: `GET /users/{id}/stats`.

**Minhas peladas:** grade `minmax(240px,1fr)`; no mobile, o mesmo carrossel. Card (radius 14):
- capa de 110px (imagem ou gradiente com iniciais);
- corpo com padding 14: nome 15/600, "Quinta · 21:00" 13 `#a3a3a3`, pílula "52 membros", pílula "Admin" (bg `#33290a`, texto `#fcd34d`) quando admin, e "Próxima: …" 12px.

O último item é um card tracejado "Criar nova pelada" (min-height 200, círculo de 40px com `Plus`).

**Backend necessário:** os cards de próximas sessões precisam do id da sessão, do status, de `confirmedCount`, da capacidade (`numberOfTeams × playersPerTeam`) e de `isConfirmed` do usuário. Hoje `/peladas/my` só devolve `nextDailyDate`. Sugestão: estender `PeladaResponseDTO` com `nextDaily: {id, date, time, status, confirmedCount, capacity, isConfirmed}` ou criar `GET /users/me/upcoming-dailies`. Siga o BACKEND.md: DTO, teste, migration se houver e atualização da tabela de API.

---

## 3. Pelada (`Pelada Detail.dc.html` → `pages/PeladaDetailPage.tsx`)

**Banner:** vira card (radius 14, altura mínima 230; 190 no mobile) dentro do padding.
- **Fundo:** imagem `cover` em `center 22%`, ou gradiente com as iniciais 56px/800 `rgba(255,255,255,.55)`. Por cima, gradiente `180deg transparent 30% → rgba(0,0,0,.7)`.
- **Texto (embaixo, padding 20/22):** nome 24/700. Abaixo, uma linha de metadados 13px `#e5e5e5` com `Calendar` (dia), `Clock` (hora · duração), `MapPin` (endereço) e `Bookmark` (referência).
- **Admin:** no canto superior direito, botões redondos de 34px (bg `rgba(0,0,0,.35)`): Editar (`Pencil`) e Excluir (`Trash2`, hover vermelho).

**Tabs:** pílula com bg `#262626` e padding 4; tab ativa com bg `#1a1a1a`. No mobile a barra ocupa a largura toda: cada tab com `flex: 1 1 0`, fonte 13px, padding 7/6 e ellipsis, **sem scroll lateral**.

**Membros**
- **Barra:** busca (`Input` pill de 38px com ícone `Search` e placeholder "Buscar membro") e chips de posição com contagem (Todos 20, Goleiro 1, Zagueiro 1, Meio 8, Atacante 10). Chip ativo com bg `#fafafa` e texto `#171717`. À direita, o botão gradient "Adicionar jogador".
- **No mobile:** o botão vira um **círculo de 40px com `Plus`**, na mesma linha do resumo "20 de 20 membros · 1 admin" (13px `#8a8a8a`).
- **Grade:** `repeat(auto-fill, minmax(230px,1fr))`.
- **Card de membro** (radius 12, padding 14, gap 12, hover borda `#3a3a3a`):
  - avatar de 42px (cor por hash: `oklch(0.58 0.14 h)` → crie tokens `--avatar-1..6`);
  - nome 15/600, com coroa (`Crown`, ouro) quando admin;
  - badge de posição (11/600 uppercase) e estrelas (★ ouro / `#3a3a3a`);
  - menu `⋯` (`DropdownMenu`: Ver perfil, Tornar/remover admin, Remover);
  - rodapé com borda superior e 3 colunas: Jogos, Gols, Assist. (valor 16/600, label 11).
- Os números vêm do ranking que a página já carrega (`usePeladaDetail`); não faça requests novos.
- Filtro e busca são client-side com `useMemo`. Sem resultados: "Nenhum membro encontrado." em card tracejado.

**Sessões**
- **Card "Próxima sessão"** (radius 14, padding 18):
  - bloco da data 72×80 (radius 12, gradiente `#15803d → #14532d`, "DOM" 11/700 `#bbf7d0`, "04" 28/800, "OUT");
  - label "PRÓXIMA SESSÃO" e badge Agendada;
  - título 18/600 ("Hoje, domingo · 08:00");
  - barra de confirmados (máx. 260px) com "**14** de 20 confirmados";
  - botões "Ver sessão" (outline) e "Confirmar presença" (gradient).
- **Sem sessão agendada:** card tracejado (`#333`) com ícone de calendário com X, "Nenhuma sessão agendada" e a dica para admin: "Crie a próxima sessão ou ative a criação automática nas configurações da pelada." (para membro: "Quando um admin agendar a próxima sessão, ela aparece aqui."). **Sem botão.**
- **Histórico:** "Histórico · 7 sessões finalizadas" e, para admin, o botão outline "Nova sessão" (abre `CreateSessionDialog`).
- **Agrupamento por mês:** label 12/600 uppercase `#737373` ("SETEMBRO 2026") seguido de um card-lista.
- **Linha da lista** (padding 12/14, hover `#202020`):
  - dia 20/700 com o dia da semana abreviado 11/600 embaixo;
  - título "Domingo, 27 de setembro" 14/500 e meta "08:00 · 4 times · 6 partidas" 12px;
  - badge de status, ícone `Users` com o número de jogadores;
  - miniatura de 40px da foto do campeão (sem foto: quadrado tracejado) e `ChevronRight`.
- No mobile, a contagem de jogadores e a miniatura somem.

**Ranking**
- Tabela em card com cabeçalho bg `#1e1e1e`.
- **Colunas desktop:** `44px 1fr 40px 64px×4` (#, Jogador, histórico, J, V, Gols, Assist.).
- **Mobile:** `26px 1fr 28px 30px 26px 40px 44px`, com padding 10/6. **Todas as colunas visíveis, sem scroll.**
- **Top 3:** "1º / 2º / 3º" em ouro, prata e bronze, 700.

**Prêmios**
- Linha "4 categorias · N prêmios distribuídos", somada dos cards.
- **Grade:** `auto-fit, minmax(300px,1fr)`. Mantém o card atual (ícone, título e descrição; linhas com troféu, avatar, nome e pílula "Nx"; troféu de fundo com opacidade .07; "Ver todos (N)" expansível).
- **Destaque do líder:** a primeira linha é um bloco com bg `linear-gradient(90deg, rgba(234,179,8,.14), rgba(234,179,8,.03))`, borda `rgba(234,179,8,.22)` e radius 10. Dentro:
  - troféu dourado de 18px;
  - avatar de 38px com anel de 2px `#eab308`;
  - nome 16/700 e "LÍDER" 11/600 ouro;
  - pílula da contagem com bg `#eab308` e texto `#1a1a1a`.
- As demais posições usam troféu prata e bronze; da 4ª em diante, `#525252`.

**Chat**
- **Botão flutuante:** fixo em `right/bottom 16px`, 54px, gradiente brand e ícone `MessageCircle`.
- **Desktop:** abre um painel/dialog flutuante (`right 24px; top 72px; bottom 86px; width 360px`, radius 14). Tem cabeçalho com "Chat", "{pelada} · N membros" e X, a área de mensagens e um input com botão "Enviar". O botão flutuante alterna abrir e fechar.
- **Mobile:** `Drawer` de baixo com 78% de altura, alça, cabeçalho com X e input de 44px com fonte 16px (evita o zoom do iOS).
- Reaproveite `usePeladaChat`.

---

## 4. Diária (`Daily Detail.dc.html` → `pages/DailyDetailPage.tsx`)

**Cabeçalho comum**
- link "← {pelada}" 13px;
- H1 com a data por extenso ("Quinta-feira, 1 de outubro"), 28/700 (22 no mobile);
- badge de status;
- metadados 13px com `Clock` e a hora, `Users` e "20 jogadores · 4 times", `Volleyball`/bola e "17 partidas" (só na finalizada).

As ações de admin saem da **barra fixa inferior** e vão para o cabeçalho, à direita, com `flex-wrap`. O menu `⋯` é um `DropdownMenu` alinhado à direita.

### 4a. Agendada (`SCHEDULED`)
- **Ações de admin:** "Importar mensagem" (outline, `FileText`) e "Confirmar diária" (gradient, `CircleCheck`). Menu `⋯`: Cancelar diária (`CircleX`), separador e Excluir sessão (vermelho).
- **Card de presença** (radius 14, padding 20):
  - "PRESENÇA";
  - número 32/700 e "de 20 confirmados";
  - barra de 8px (máx. 420px, transição de largura .3s);
  - dica: "Faltam N para fechar 4 times de 5." ou "Lista completa. Os times já podem ser sorteados.";
  - à direita, "Você ainda não confirmou" com o botão gradient "Confirmar presença" (42px, `CalendarCheck`). Depois de confirmar: "✓ Presença confirmada" (verde) e o botão outline "Não vou mais".
- **"Lista de presença" recolhível** (`Collapsible`):
  - **Gatilho:** chevron que gira 90°, título 16/600, "X confirmados · Y pendentes" e "Recolher/Expandir" à direita.
  - **Estado inicial:** aberta. **Ela recolhe sozinha depois de sortear os times.**
  - **Conteúdo:** duas colunas (uma abaixo de 1100px).
    - **Confirmados:** dot verde; cada linha tem avatar de 30px, nome, badge de posição, estrelas e, para admin, o botão `UserMinus` (hover vermelho).
    - **Não confirmados:** dot `#525252`, texto e estrelas apagados (`#a16207`); o admin tem o botão `UserPlus` (hover verde) e, no cabeçalho, "Confirmar todos".
  - **Vazios:** "Ninguém confirmou ainda." / "Todos os membros confirmaram."
- **Times**
  - **Sem times:** card tracejado com ícone `Shuffle`, "Sem times ainda" e a dica "É preciso ter exatamente 20 confirmados (4 × 5) para sortear. Hoje são N." O botão "Sortear times" fica desativado (bg `#262626`, texto `#737373`, `not-allowed`) até a lista fechar.
  - **Com times:** grade `minmax(260px,1fr)`. Cada card tem dot, nome, média de estrelas em ouro, linhas com avatar, nome, estrelas e o botão de troca (`ArrowLeftRight`, 26px, só admin). No cabeçalho, "Sortear de novo".
- **APIs:** `confirmAttendance`, `disconfirmAttendance`, `adminConfirm`, `adminDisconfirm`, `sortTeams`, `swapPlayers`, `updateStatus`, `populate` e `delete`. Use `daily.isAdmin`.

### 4b. Em andamento (`IN_COURSE`)
- **Cabeçalho:** badge âmbar "Em andamento" e o indicador "● Ao vivo" (dot `#22c55e` com anel `0 0 0 4px rgba(34,197,94,.18)`, texto `#4ade80` 13/600). Ação: "Lançar resultados" (gradient, `ClipboardList`).
- **Card ao vivo:** borda `#1f4d2e`, fundo `linear-gradient(180deg, #15241a, #1a1f1b)`. Dentro:
  - label "SESSÃO AO VIVO";
  - título "N partidas lançadas" 22/700;
  - texto 13px `#a3c9b0`: "Lance os placares conforme os jogos acabam. No fim, finalize a sessão para calcular prêmios, ranking e estatísticas.";
  - botões "Finalizar sessão" (outline, desativado até a primeira partida; abre o `FinalizeModal`) e "Lançar resultados".
- **Duas colunas:**
  - **Classificação ao vivo:** colunas `28px 1fr 30 30 30 30 36 40` (#, Time, J, V, E, D, SG, Pts). SG positivo em `#4ade80`, negativo em `#f87171`. Ordenação: pontos, saldo, gols pró.
  - **Partidas lançadas:** as mais recentes primeiro. Cada card mostra "#n", os times com dot (vencedor em 700), o placar 18/700 e "Gols: …".
- **Times:** com a soma de "X G · Y A" de cada jogador.

**Dialog "Lançar resultados"** (`Dialog` no desktop, `Drawer`/sheet de baixo no mobile; substitui o `ResultsModal`/`ResultsForm`, que devem migrar para react-hook-form + zod, conforme FRONTEND.md)
- **Desktop:** `width min(720px, 100% - 24px)`, `max-height 100vh - 32px` e radius 16. **Mobile:** largura total, colado embaixo, radius `18px 18px 0 0`, `max-height 100% - 24px`.
- **Cabeçalho:** "Lançar resultados" 18/700 e a descrição "Marque os times, o placar e, se quiser, quem fez os gols e as assistências.", mais o X.
- **Corpo com scroll.** Card por partida (bg `#212121`, radius 12, padding 16; 12 no mobile):
  - "Partida N" e o botão de remover (`Trash2`, só com mais de uma partida).
  - **Seleção de times por chips** em vez de select: "Time 1" e "Time 2", cada um com os 4 times (dot e nome). Chip selecionado com bg `#333` e borda `#a3a3a3`; o time já escolhido do outro lado fica com opacidade .35 e não clica. No mobile os chips ficam numa linha com scroll horizontal (`flex-wrap: nowrap`).
  - **Placar:**
    - **Desktop:** em linha, time 1 → stepper → "x" → stepper → time 2. Botões de 36px e número 24/700.
    - **Mobile:** placar em grade de 3 colunas sobre bg `#1a1a1a` (radius 12). Cada coluna tem o nome do time em cima e o stepper embaixo, com botões de 40px e número 28/700.
  - **Gols e assistências** (recolhível, fechado por padrão):
    - **Conferência:** "Gols atribuídos: g1/s1 · g2/s2". Verde quando bate com o placar, âmbar (`#fbbf24`) quando não bate, cinza quando o placar é 0–0. **É só um aviso: não bloqueia o salvamento.**
    - **Layout:** duas colunas, uma por time (uma no mobile). Cabeçalho com dot, nome uppercase e "Gols · Assist." à direita.
    - **Linha** (inclusive no mobile): nome (até 2 linhas com `line-clamp`), stepper de gols de 24px, divisor e stepper de assistências.
  - **Sugestão do próximo confronto** ao adicionar partida, em rodízio: `[0,1],[2,3],[0,2],[1,3],[0,3],[1,2]`.
- **"+ Adicionar partida":** botão tracejado de 44px.
- **Rodapé:** "N partidas" (só desktop), "Cancelar" e "Salvar resultados" (gradient). No mobile os dois botões dividem a largura.
- **Schema zod** espelhando `MatchResultDTO`: `team1Id ≠ team2Id`, placares `int ≥ 0`, `playerStats[]` só com jogadores dos dois times. Envia com `submitResults` e faz merge da resposta.

### 4c. Finalizada (`FINISHED`)
- **Ações de admin:** "Editar resultados" (`Pencil`) e "Re-finalizar" (`Flag`). Menu `⋯`: Trocar foto do campeão e, após um separador, Excluir sessão.
- **Hero** em grade `1.6fr / minmax(300px, 1fr)` (empilha abaixo de 1100px):
  - **Foto do campeão:** radius 14, altura mínima 340 (220 no mobile), gradiente embaixo, rótulo "FOTO DO CAMPEÃO" com troféu dourado. O admin tem "Trocar foto" no canto (pílula com bg `rgba(0,0,0,.5)`).
  - **Card do time campeão:**
    - "TIME CAMPEÃO" em ouro;
    - dot de 14px e nome 24/700;
    - 3 mini-cards com bg `#262626`: pontos, V–D e aproveitamento;
    - os 5 jogadores com "G · A".
- **Prêmios:** grade `auto-fit minmax(210px,1fr)` com 4 cards. Cada um tem o ícone em quadrado de 28px com a cor da categoria (Artilheiro verde, Garçom azul `#38bdf8`, Puskás ouro, Bola Murcha vermelho), o título, um detalhe à direita ("5 gols") e o avatar de 36px com os vencedores 16/600.
- **Tabs no lugar das seções recolhíveis:**
  - **Classificação:** `44px 1fr 56×6 64`; no mobile, colunas compactas. A linha do campeão tem bg `#231f12`.
  - **Partidas:** grade de cards `minmax(300px,1fr)`. Cada card mostra "PARTIDA N", o placar central 22/700, o vencedor em 700 e o rodapé "Gols …", "Assist. …". Termina com "Mostrar todas as N partidas".
  - **Jogadores:** tabela ordenável por Gols, Assist., Partidas e Vitórias (cabeçalho ativo em `#fafafa` com "↓"). O avatar tem um dot com a cor do time.
  - **Times:** cards com a posição final ("1º lugar") e a média de estrelas, sem botão de troca (status bloqueado).

---

## State Management
- **Shell:** `sidebarCollapsed` (salvar em `localStorage`), `mobileMenuOpen`, `userMenuOpen`.
- **Pelada:** `tab`, `memberQuery`, `memberPosFilter`, `awardsExpanded: Record<category, boolean>`, `chatOpen`.
- **Diária:** `tab` (finalizada), `playerSort`, `attendanceOpen` (fecha depois de `sortTeams`), `resultsDialogOpen`.
- **Formulário de resultados:** `useFieldArray`.
- **Dados:** mantenha os hooks atuais (`usePeladaDetail`, `useDailyDetail`, `useDailyActions`…), com mutations fazendo merge do DTO retornado. O padrão é o de FRONTEND.md › Hooks.

## Interactions
- **Transições:** largura da sidebar .2s, transform do drawer .25s, chevrons .15s, barras de progresso .3s.
- **Hover:** cards `border-color #3a3a3a`, linhas `#202020`, botões ghost `#262626`, botões destrutivos `#2a1a1a` com texto `#f87171`.
- **Acessibilidade:** todo botão só com ícone tem `aria-label` em PT-BR. Dialogs e drawers usam os primitives do shadcn (foco, Esc).

## Assets
- `assets/jogasse-onde.jpg`: imagem de exemplo da pelada; no app vem de `pelada.image`.
- `assets/campeao-27-09.jpeg`: foto de campeão de exemplo; no app vem de `daily.championImage`.
- `public/ronaldo.jpg`: já está no repositório, usada no banner da Home.
- **Ícones:** lucide-react (os nomes estão citados acima). Nos protótipos, alguns ícones de prêmio são glifos de texto: no app use os ícones lucide que já existem no `AwardsTab`.

## Files
- `Home.dc.html`: Home.
- `Pelada Detail.dc.html`: shell e tela da pelada. Tweaks: `viewport`, `sidebarCollapsed`, `showChat`, `hasNextSession`, `isAdmin`.
- `Daily Detail.dc.html`: diária. Tweaks: `status` (`SCHEDULED` / `IN_COURSE` / `FINISHED`), `viewport`, `isAdmin`.
- `support.js`: runtime necessário só para abrir os protótipos no navegador.
- `PROMPT.md`: prompt pronto para colar no Claude Code.
