# Redesign mobile pela Lei de Jakob — spec

Data: 2026-11-16. Status: aprovada, não implementada.

## Objetivo

Abaixo de 768 px (`md`), o Futspring deixa de ser "o site encolhido" (top bar com hambúrguer e sidebar em Sheet) e passa a se comportar como os apps que o usuário já usa todo dia (WhatsApp, Instagram, TikTok): navegação por abas embaixo, criação no centro, perfil no canto, voltar arrastando da borda, puxar para atualizar. Lei de Jakob: o usuário passa a maior parte do tempo em outros apps e espera que o nosso funcione igual.

O desktop (≥ 768 px) não muda.

## Decisões

| Tema | Decisão |
|---|---|
| Abas | Início · Peladas · **+** · Avisos · Perfil |
| Botão + | Bottom sheet "Criar" com as criações que o papel do usuário permite |
| Escopo | Shell + gestos + telas-chave; as demais telas só se adaptam ao shell |
| Áreas da pelada | Faixa de abas sublinhadas com scroll horizontal, troca só por toque |
| Implementação | Shell próprio (React Router + componentes e hooks nossos), sem Ionic/Konsta, sem React Query |
| Contrato | `isFinanceManager` no resumo da pelada |

Abordagens descartadas: Ionic React / Konsta UI (segundo design system e segundo roteador, quebra shadcn + tokens); migrar para React Query antes (triplica o escopo; continua no backlog e esta spec não o bloqueia).

## 1. Navegação

### Barra inferior (`components/layout/BottomTabBar.tsx`)

Fixa na base, só abaixo de `md`, `nav aria-label="Navegação principal"`, cada aba com `aria-current="page"` quando ativa.

| Aba | Rota | Ativa em |
|---|---|---|
| Início | `/home` | `/home`, `/invitations/:id` |
| Peladas | `/peladas` (nova) | `/peladas`, `/pelada/**`, `/daily/:id` |
| **+** | abre o sheet "Criar" | — |
| Avisos | `/notificacoes` (nova) | `/notificacoes` |
| Perfil (avatar do usuário) | `/profile/{id do usuário}` | o próprio perfil, `/settings`, `/admin/**` |

- Badge de Avisos com o `unreadCount` do `NotificationsContext` (`bg-brand`, "20+" acima de 20).
- Tocar na aba ativa: se a rota não é a raiz da aba, navega para a raiz; se é, rola para o topo (Instagram, X).
- Escondida no chat (`/pelada/:id/chat`) e enquanto o teclado virtual está aberto (`visualViewport.height` bem menor que `window.innerHeight`), como no WhatsApp.
- Visual: `bg-card` + `border-t`, altura 56 px + `env(safe-area-inset-bottom)`, ícone + rótulo `text-caption`; inativa `text-muted-foreground`, ativa `text-brand-text` com o ícone preenchido; "+" em círculo `bg-brand` de 44 px com ícone `text-brand-foreground`; alvos ≥ 48 px. Claro, escuro e 375 px.
- A altura fica em `--mobile-tabbar-h` (0 a partir de `md`) para o que flutua na base se posicionar acima dela.

`tabForPath(pathname, userId)` e `isTabRoot(pathname, userId)` ficam em `utils/mobileNav.ts`.

### Menu "+" (`components/layout/CreateMenu.tsx`)

`Drawer` com título "Criar". Os itens vêm de `createActions(peladas, currentPeladaId)` (`utils/mobileNav.ts`, puro), a partir do `MyPeladasContext`:

| Item | Aparece quando | Ação |
|---|---|---|
| Nova sessão | é admin (`isAdmin`) de alguma pelada com `subscription.writable` ≠ false | `CreateSessionDialog` da pelada |
| Convidar jogador | é admin de alguma pelada | `InvitePlayerDialog` da pelada |
| Nova cobrança | `isFinanceManager` em alguma pelada com assinatura gravável | `/pelada/:id/financeiro` com a criação aberta |
| Criar pelada | sempre | `CreatePeladaSheet` (`openCreatePelada`) |

- Dentro de uma pelada (`/pelada/:id/**`, `/daily/:id`) que se qualifica, ela vem pré-selecionada. Fora disso, com uma pelada elegível, vai direto; com mais de uma, um passo "Em qual pelada?" (lista de peladas no próprio Drawer).
- Um jogador comum vê só "Criar pelada".
- O servidor continua sendo a autoridade: o menu só esconde o que o usuário não pode.
- Os diálogos de sessão e convite hoje vivem no layout da pelada; para abri-los de fora dele, o menu navega para a área correspondente (`/pelada/:id/sessoes`, `/pelada/:id/membros`) com um sinal de abertura (state do router), que o layout consome uma vez. Nova cobrança faz o mesmo no Financeiro.
- "Entrar com link" fica de fora: não há onde colar um link hoje e o convite chega pelo próprio link.

### Cabeçalho mobile (`components/layout/MobileHeader.tsx`)

Fixo no topo, `env(safe-area-inset-top)`, substitui o `MobileTopBar` (removido).

- Raiz de aba: sem ←. Em Início, o logo; nas outras raízes, o título da tela.
- Fora da raiz: ← + título compacto. Padrão "large title" do iOS: o h1 grande continua no corpo (`PageHeader` / `PeladaPageHeader`) e o título compacto aparece no cabeçalho quando o h1 sai da tela (IntersectionObserver).
- Ações à direita por tela (engrenagem no perfil próprio e na pelada para admins, chat na pelada, engrenagem em Avisos).
- ←: `history.back()`; se a tela foi a primeira da sessão de navegação (`location.key === "default"`: link do WhatsApp, push, reload), navega para `parentPath(pathname)` (`utils/mobileNav.ts`):

| De | Para |
|---|---|
| `/daily/:id` | `/pelada/{peladaId}/sessoes` (do detalhe já carregado) |
| `/pelada/:id/financeiro/:listId` | `/pelada/:id/financeiro` |
| `/pelada/:id/chat`, área ≠ visão geral | `/pelada/:id` |
| `/pelada/:id` | `/peladas` |
| `/pelada/:id/settings`, `/pelada/:id/assinatura` | `/pelada/:id` |
| `/settings`, `/admin` | `/profile/{id do usuário}` |
| `/admin/**` | `/admin` |
| `/invitations/:id`, `/profile/{outro}` | `/home` |

A página informa título, ações e pai por um contexto do shell (`useMobileHeader({ title, actions, parent })`), sem acoplar o cabeçalho a cada rota.

### Áreas da pelada

Abaixo de `md`, o `PeladaAreaSelect` dá lugar a uma faixa de abas sublinhadas (`Tabs variant="underline"` como links, ou `nav` com `aria-current`), com scroll horizontal (`.scrollbar-none`), a ativa rolada para dentro da vista. Itens de `visibleAreas(role)`; mesma regra de esconder em páginas que o usuário não pode abrir. Troca só por toque: nenhum swipe lateral disputa com o voltar arrastando. O breadcrumb some no mobile (o cabeçalho já mostra ← e o nome da pelada).

## 2. Gestos

### Voltar arrastando (`hooks/useEdgeSwipeBack.ts`)

- **Só no iOS standalone** (`detectInstallContext` = `installed` + iOS): no Safari do navegador o gesto nativo já existe, e no Android o gesto/botão do sistema já gera `popstate`.
- Começa só com toque até **24 px da borda esquerda** e intenção horizontal (|dx| > |dy| nos primeiros 10 px); não disputa com a faixa de áreas, carrosséis ou o dnd-kit, que não começam colados na borda.
- A página acompanha o dedo (`translateX` no conteúdo, véu `bg-overlay/40` atrás). Soltar com dx > **35 % da largura** ou velocidade > 0,5 px/ms: a página sai e o app faz o mesmo que o ← (incluindo `parentPath`). Abaixo disso, volta com uma mola curta.
- Desligado na raiz das abas e com overlay aberto (Drawer, Sheet, Dialog: `[role=dialog][data-state=open]`).
- Limitação aceita: atrás da página aparece o véu, não a tela anterior (seria preciso manter páginas montadas).

### Puxar para atualizar (`components/layout/PullToRefresh.tsx` + `hooks/useRefresh.ts`)

- Cada página registra `useRefresh(() => Promise<unknown>)` num `RefreshContext` do `AppLayout`. **Sem registro, sem gesto.**
- Começa com `scrollTop === 0`, dedo descendo, nenhum overlay aberto, toque fora de áreas roláveis internas (chat, listas com scroll próprio). Resistência (deslocamento ÷ 2), dispara a partir de **64 px**; spinner `brand` enquanto a promise corre; um segundo gesto é ignorado; erro → toast "Não foi possível atualizar" e a tela mantém os dados.
- `overscroll-behavior-y: none` no `html` abaixo de `md`: desliga o recarregamento nativo do Chrome Android (que recarregaria a página inteira e o boot da sessão) e o elástico do Safari.
- Registros:

| Tela | Recarrega |
|---|---|
| Início | peladas (`MyPeladasContext.reload`), convites, "Seus números" |
| Peladas | `MyPeladasContext.reload` |
| Pelada (layout, vale para todas as áreas) | pelada, sessões, ranking, prêmios (`usePeladaDetail`) |
| Sessão | `useDailyDetail.refetch` |
| Avisos | `useNotifications.refresh` |
| Perfil | `useProfile` |
| Financeiro | `useFinance` refresh |

- Pré-requisito: os recarregadores passam a **devolver a promise** da leitura (hoje `useMyPeladas.reload` e outros devolvem `void`; antipadrão #10 do FRONTEND.md).

### Acessibilidade e movimento

- Todo gesto tem alternativa de um toque (WCAG 2.5.1): o ← para o voltar; o puxar é atalho de leituras que já acontecem (`visibilitychange`, "Tentar novamente" nos erros).
- `prefers-reduced-motion`: gestos funcionam sem animação de deslocamento; spinner parado; navegação imediata.
- Lógica pura em `utils/gestures.ts` (decisão de início, limiar, resistência, velocidade), testada com Vitest.

### Suporte

`viewport-fit=cover` no `<meta name="viewport">` de `client/index.html`, para `env(safe-area-inset-*)` funcionar. Verificar que o hash do script de tema no CSP não muda (o meta de viewport não é script).

## 3. Telas-chave

### Chat estilo WhatsApp

- Rota `/pelada/:id/chat`, filha do layout da pelada: topo ← + nome da pelada + "N membros"; mensagens; campo fixo embaixo com `safe-area-inset-bottom`. Sem barra inferior, sem faixa de áreas; o voltar arrastando funciona.
- Entrada no mobile: ícone de chat no cabeçalho da pelada (ao lado da engrenagem), em todas as áreas (não cobre nenhum controle). O FAB e o Drawer de 78 % saem do mobile; desktop mantém FAB + popover.
- `areaFromPath` reconhece `chat` como sub-rota conhecida que não é área.
- Sem badge de não lidas (o backend não tem esse dado).

### Bottom sheets

- Novo `components/ui/responsive-dialog.tsx`: `Dialog` a partir de `md`, `Drawer` abaixo, mesma API (`ResponsiveDialog`, `…Content`, `…Header`, `…Title`, `…Description`, `…Footer`).
- Migram agora: `CreateSessionDialog`, `InvitePlayerDialog`, `SetPlayerStarsDialog` e `ResultsDialog` (que hoje troca à mão).
- Os demais migram quando forem tocados (regra no FRONTEND.md). `AlertDialog` continua centralizado (alerta do iOS/Android). `Sheet`s laterais de formulário ficam como estão.

### Páginas novas

- **`/peladas`** (`PeladasPage`): lista estilo conversas do WhatsApp: `PeladaAvatar`, nome, pill "Admin", "Qui, 05/11 · 21:00 · 7/10"; linha final "Criar pelada"; estados de carregando/erro/vazio. Dados do `MyPeladasContext`, sem request novo.
- **`/notificacoes`** (`NotificationsPage`): `NotificationPanel` em página inteira; engrenagem → `/settings#notificacoes`. No mobile o sino sai do topo (a aba substitui); no desktop o sino continua com o Popover.
- Ambas funcionam em qualquer largura; a navegação do desktop não aponta para elas.

### Ajustes

- **Perfil próprio:** engrenagem no cabeçalho → `/settings`.
- **Configurações:** "Painel admin" (só `user.isPlatformAdmin`) e "Sair" (`destructive-ghost`) no fim; o tema já está em Aparência. Era o que o `UserMenu` oferecia e ficaria sem acesso no mobile.
- **Barras que flutuam na base** (`MemberSelectionBar`, `SelectionBar` do Financeiro, tour): `bottom: var(--mobile-tabbar-h)`.
- **`PageContainer`:** padding inferior = barra + safe area no mobile; o `pb-24` do FAB do chat sai no mobile.
- A sidebar em Sheet deixa de ser montada abaixo de `md`.

## 4. Contrato: `isFinanceManager`

- `PeladaResponseDTO` ganha `@JsonProperty("isFinanceManager") boolean isFinanceManager`: o chamador é responsável financeiro da pelada.
- Preenchido em `/peladas/my` e `/users/{id}/peladas` com `FinanceManagerRepository.findPeladaIdsByUserId` (uma query por resposta, constante no número de peladas). Nas respostas de create / update / image / transfer, mesma regra do `isAdmin` (create: `true`, o criador nasce responsável).
- Frontend: `PeladaResponse` em `types/pelada.ts`, fixtures do e2e.
- Docs: tabela de Peladas no BACKEND.md, Types/API no FRONTEND.md.
- Aditivo, sem migration.

## 5. Testes

- **Vitest:** `utils/mobileNav.ts` (`tabForPath`, `isTabRoot`, `parentPath`, `createActions`: papéis, pelada atual, assinatura somente leitura), `utils/gestures.ts`.
- **Backend:** `isFinanceManager` em `/peladas/my` e `/users/{id}/peladas` (responsável, admin não responsável, membro); mesmo número de statements para 2 e 20 peladas; snapshot `PeladaResponseDTO.json` regenerado.
- **Playwright visual:** snapshots dos projetos mobile atualizados (o shell muda todas as telas); novas telas `/peladas`, `/notificacoes`, chat, "+" aberto (admin e jogador), faixa de áreas. Desktop sem diferenças, exceto Configurações.
- **Playwright de gestos** (`e2e/mobile-gestures.spec.ts`, toque via CDP `Input.dispatchTouchEvent`): swipe-back com iOS standalone emulado navega; no Android não liga; começo fora da borda não liga; puxar para atualizar refaz a leitura (contagem de requests no `mockApi`); página sem registro não reage.

## 6. Entrega

Branch de integração `feat/mobile-shell` (sub-PRs → ela → `dev`):

1. **Contrato e preparo:** `isFinanceManager` (back, front, docs), recarregadores devolvendo promise, `viewport-fit=cover`.
2. **Shell:** `BottomTabBar`, `MobileHeader`, faixa de áreas, menu "+", `/peladas`, `/notificacoes`, Configurações (Sair, Painel admin), remoção do `MobileTopBar` e da sidebar mobile.
3. **Gestos:** voltar arrastando, puxar para atualizar e os registros nas páginas.
4. **Telas-chave:** chat em rota, `ResponsiveDialog` + migrações, offsets das barras de seleção.

Cada PR atualiza FRONTEND.md (e BACKEND.md no 1) e roda `bun run lint && bun run build && bun run test` (+ `./mvnw test` no 1).

## Fora de escopo

Swipe entre áreas, ações por swipe em linhas, menus por toque longo, vibração, transições animadas entre páginas / páginas mantidas montadas, badge de não lidas no chat, React Query, app de loja.

## Critério de pronto

- Abaixo de 768 px não existe hambúrguer; toda tela privada é alcançável pelas abas.
- Toda tela volta pelo ←; no iPhone instalado, também arrastando da borda.
- As 7 telas registradas atualizam ao puxar; as demais não reagem.
- O "+" mostra a um jogador comum só "Criar pelada" e a um admin/responsável as criações das suas peladas.
- Desktop sem diferença visual, exceto Configurações.
