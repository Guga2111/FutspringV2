# Design System do Futspring — "Sport-Tech Minimalism" (Phase 1: Discovery + SPEC)

- **Status:** **aprovada** em 2026-11-01, com as decisões registradas na [§17](#17-decisões-aprovadas). Nada foi implementado ainda.
- **Data:** 2026-11-01
- **Escopo:** apenas `client/`. Nenhuma mudança de contrato de API, backend ou regra de negócio.
- **Fora do escopo:** a Landing Page (`pages/LandingPage.tsx` e as classes `.landing-*`), que será refatorada em outra feature.
- **Phase 2 (implementação):** começa por solicitação explícita, seguindo o plano da [§13](#13-plano-de-implementação-phase-2--não-executado).

Convenções: **[Fato]** = observado no repositório (com o arquivo citado), **[Risco]** = consequência possível, **[Rec]** = recomendação. Caminhos são relativos a `client/`, exceto os que começam com `docs/` ou `.github/`. Os contrastes foram calculados com a fórmula de luminância relativa do WCAG 2.x; o script vira um teste na Phase 2 ([§15](#15-estratégia-de-testes-e-validação)). Identificadores de código e nomes de tokens ficam em inglês.

---

## 1. Resumo executivo

O client do Futspring já tem uma boa base de design system: shadcn/ui sobre Radix, Tailwind 3 com **tokens semânticos em HSL** em `src/index.css` (`:root` + `.dark`), variantes `cva` em `components/ui/*-variants.ts`, dark mode por classe e regras documentadas contra cores cruas (antipattern #3 do FRONTEND.md). Como a maioria das telas consome tokens, **boa parte da nova identidade chega reapontando tokens**, sem editar componentes.

O que impede uma troca limpa para a nova identidade:

1. **Nenhuma fonte é carregada.** O app usa a stack do sistema do Tailwind; não há `fontFamily` em `tailwind.config.js` nem arquivo ou `<link>` de fonte. Barlow Condensed e Inter são totalmente novas.
2. **A marca é verde e está presa a `text-white`.** O `--brand` (`#149440`) é consumido por `.bg-gradient-primary` / `Button variant="gradient"` (33 arquivos) com `text-white`. Branco sobre Electric Blue `#0799D5` dá **3,22:1 (reprova no AA para texto normal)**, então cada ponto de "branco sobre a marca" precisa trocar a cor do texto, não só a do fundo.
3. **Conflito de nome:** o `--accent` do shadcn é uma *superfície neutra de hover* (botões `ghost`/`outline`, menus, toggles, selects). O Cantaloupe não pode reaproveitar esse token: ganha um novo, `--highlight` (ver Q5 na §17).
4. **Ilhas de cor fixa** que os tokens não alcançam: `ComparePlayersDialog` (sempre escuro, `zinc-*`), `ComparablePlayerStatsCard`, `StatsOverTimeChart` / `ProfilePieChart` (hex), ícones de KPI do `ProfilePage`, `MatchHistoryTable`, `CreatePeladaModal` (anéis de foco verdes), gradientes de pelada/avatar em `lib/utils.ts` e `rgba` verdes no `index.css`.
5. **Lacunas no bootstrap do tema:** o tema salvo é aplicado num module script (`main.tsx`) depois do CSS (possível flash claro para quem usa dark), não há `color-scheme` nem fallback para a preferência do sistema, o `localStorage` é lido sem try/catch na inicialização, o `<Toaster>` do Sonner não recebe `theme` e o `<html lang="en">` não corresponde à interface em pt-BR.
6. **Dívida de acessibilidade nos tokens atuais:** `--faint-foreground` reprova no AA nos dois temas (2,56:1 no claro, 3,19:1 no escuro), as bordas de input ficam em ~1,2–1,3:1 (WCAG 1.4.11) e não existe tratamento de `prefers-reduced-motion`.

**Arquitetura aprovada (Opção A, §8.1):** manter o mecanismo e os nomes atuais dos tokens; adicionar por baixo uma **camada fina de primitivas** (triplets HSL, independentes de tema e não expostas ao Tailwind); reapontar os tokens semânticos (`--background`, `--card`, `--brand`, `--ring`…) para as primitivas; criar `--highlight*` para o Cantaloupe e `--brand-foreground`/`--brand-text` para o uso acessível da marca; criar `font-sans` (Inter) e `font-display` (Barlow Condensed) com uma escala tipográfica nomeada. Fontes self-hosted via `@fontsource`. Regressão visual com Playwright. Migração em 8 passos incrementais e entregáveis isoladamente (§13).

---

## 2. Achados do Discovery

### 2.1 Stack e ferramentas [Fato]

| Item | Valor | Fonte |
|---|---|---|
| Framework | React 19.2, TypeScript ~5.9 (strict), Vite 8 | `package.json` |
| Estilo | Tailwind CSS **3.4.19** (`tailwind.config.js`, `postcss.config.js`), `tailwindcss-animate`, `darkMode: ["class"]` | `tailwind.config.js:3` |
| Componentes | shadcn/ui, style `default`, base color `slate`, CSS variables | `components.json` |
| Primitivos | Radix (`@radix-ui/react-*`), `vaul` (Drawer), `cmdk` (Command), `sonner` (toasts), `react-day-picker` | `package.json` |
| Variantes | `class-variance-authority`, `tailwind-merge` via `cn()` | `src/lib/utils.ts`, `components/ui/*-variants.ts` |
| Gráficos / tabelas | recharts 3 via `ui/chart.tsx` do shadcn; TanStack Table 8 | `package.json` |
| Ícones | só lucide-react | FRONTEND.md |
| Testes | Vitest 2 (`environment: 'node'`, include padrão), 28 arquivos `*.test.ts` de lógica pura (utils, schemas, api); nenhum teste de componente ou visual | `vitest.config.ts`, `find src -name '*.test.*'` |
| CI | `.github/workflows/ci-frontend.yml`: um job `build` com `bun install --frozen-lockfile`, lint, build e test | arquivo do workflow |
| Lint | ESLint 9 flat config em `**/*.{ts,tsx}`, ignora só `dist` | `eslint.config.js` |

### 2.2 Arquivos globais de estilo [Fato]

- **Uma única folha global:** `src/index.css` (366 linhas). Contém as layers do Tailwind; os tokens de `:root` e `.dark` (~80 variáveis cada); regras base (cor de borda, scrollbars finas, `body { bg-background text-foreground }`); classes compostas (`.checkbox-gradient`, `.bg-gradient-primary` com hover `brightness(1.12) scale(1.03)`, `.bg-brand-translucent`, `.text-on-brand-muted`, `.text-gradient-primary`, `.bg-gradient-date`, `.bg-gradient-live`, `.bg-gradient-leader`, overlays de foto, `.scrollbar-none`, `.page-enter`); keyframes e classes da landing (`.landing-*`).
- **Nenhum outro CSS** (sem CSS modules, sem styled-components).
- `index.html`: `<html lang="en">`, favicon `/gerrard.png`, sem links de fonte, sem script inline de tema.

### 2.3 Configuração do Tailwind [Fato] (`tailwind.config.js`)

- `colors`: todo token mapeado como `hsl(var(--x))` (convenção de triplet puro), por isso os modificadores de opacidade (`bg-brand/20`) funcionam.
- Grupos customizados já existentes: `card-elevated`, `row-hover`, `subtle-foreground`, `faint-foreground`, `team-dot-border`, `star-off`, `avatar.{1..6,fallback}`, `gold.{DEFAULT,muted,foreground}`, `silver`, `bronze`, `brand` (string), `success.*`, `warning`, `info.*`, `status.{scheduled,live}.*`, `live.*`, `chart.{1,2}`, `sidebar.*`.
- `borderRadius`: `lg = var(--radius)` (0,5rem), `md`, `sm`, mais os fixos `tile: 14px` e `hero: 18px`.
- `boxShadow`: `menu`, `panel`, `dialog`, `fab`, em **rgb preto puro ajustado para o dark** e aplicados também no light.
- **Sem extensão de `fontFamily` ou `fontSize`.**

### 2.4 Mecanismo de tema [Fato]

- `src/main.tsx:9-15`: antes do `createRoot`, lê `localStorage.getItem('theme')` e adiciona `.dark` só se o valor for `"dark"`. O padrão é **claro**, a preferência do sistema nunca é lida e a leitura **não** está em try/catch.
- `src/hooks/useTheme.ts`: `useSyncExternalStore` sobre um `MutationObserver` da classe do `<html>`; `toggleTheme` alterna `.dark` e grava `localStorage.theme` dentro de try/catch. O toggle fica em `components/layout/UserMenu.tsx`.
- `src/main.tsx:21`: `<Toaster position="top-center" />` sem `theme`, então o Sonner mostra o tema padrão (claro) também no dark.
- Não há `color-scheme` em lugar nenhum, então controles nativos e scrollbars ficam claros no dark.

### 2.5 Tipografia atual [Fato]

- Nenhuma fonte carregada, ou seja, stack do sistema (`ui-sans-serif, system-ui…`), confirmada pelo handoff do redesign (`docs/design/design_handoff_futspring_redesign/README.md:54`).
- Os tamanhos são em sua maioria **valores arbitrários**: `text-[13px]` ×47, `text-[15px]` ×23, `text-[11px]` ×18, `text-[10px]` ×9, `text-[28px]` ×5, `text-[22px]` ×4, `text-[17px]` ×4, `text-[0.8rem]` ×4 e usos únicos de 9/26/32/38/56 px.
- Pesos: `font-semibold` ×152, `font-medium` ×65, `font-bold` ×50, `font-normal` ×39, `font-extrabold` ×5, `font-black` ×2.
- `tabular-nums` aparece 47 vezes (placares, tabelas, contadores), uma boa base para números.
- `font-mono` em `ui/chart.tsx:118`, `ComparePlayersDialog.tsx:120` ("VS"), `DeleteDailyDialog.tsx:92` e `ImportFromMessageModal.tsx:151`.

### 2.6 Cores fixas [Fato]

Classes cruas da paleta do Tailwind por arquivo (`grep` em `src/**/*.{ts,tsx}`):

| Arquivo | Qtd. | Natureza |
|---|---|---|
| `pages/LandingPage.tsx` | 51 | paleta verde/neutra de marketing — **fora do escopo** (outra feature) |
| `components/pelada/ComparePlayersDialog.tsx` | 36 | dark forçado (`bg-zinc-950`, `text-white`, `border-zinc-800`) |
| `components/pelada/ComparablePlayerStatsCard.tsx` | 19 | mesma família da anterior |
| `lib/utils.ts` | 16 | `getPeladaGradient` (8 `from-*-500 to-*-700`), `getAvatarColor` |
| `pages/ProfilePage.tsx` | 15 | cores dos ícones de KPI: `text-red-400`, `text-orange-400`, `text-green-500`, `text-purple-400` |
| `components/CreatePeladaModal.tsx` | 12 | `focus-visible:ring-green-500`, `data-[state=checked]:bg-green-600` |
| `components/profile/MatchHistoryTable.tsx` | 7 | destaque `bg-green-100 text-green-800 dark:…` |
| `pelada/PeladaBanner.tsx`, `daily/ChampionHero.tsx`, `home/HomeBanner.tsx`, `home/MyPeladaCard.tsx`, `PlayerAvatar.tsx`, `PeladaAvatar.tsx` | 1–7 | `text-white`/`bg-black/x` **sobre fotos ou avatares coloridos** — legítimo (superfícies independentes de tema) |
| `components/ui/{dialog,alert-dialog,sheet,drawer,sidebar}.tsx` | 1 cada | overlay `bg-black/60–80` (padrão shadcn) |
| `ui/button-variants.ts:14`, `ui/calendar.tsx:46`, `pages/AuthPage.tsx:171`, `pelada/ChatPanel.tsx:101`, `pelada/NextSessionCard.tsx:50` | 1 cada | `text-white` **sobre a marca** — reprova no contraste sobre o azul |

Literais hex / rgb:

| Arquivo | Natureza | Ação |
|---|---|---|
| `profile/StatsOverTimeChart.tsx` (12), `profile/ProfilePieChart.tsx` (3) | séries `#32CD32`, `#99CB0E`, `#f97316`, `#888` | mover para `--chart-*` (já é o antipattern #27) |
| `index.css` | `.bg-brand-translucent` `rgb(21 128 61 / .35)`, `.text-on-brand-muted` `#bbf7d0` | reapontar para tokens |
| `index.css` (`.landing-*`) | verdes da landing | fora do escopo |
| `pages/AuthPage.tsx:318-330` | cores do logo do Google | manter (marca de terceiro) |
| `utils/parseSessionMessage.ts:26-31`, `daily/TeamsSection.tsx:127` | **cores dos times** (dado do usuário/backend) | manter — é dado, não tema |

### 2.7 Consumidores da marca [Fato]

- `Button variant="gradient"`: 33 arquivos (auth, pelada, daily, finance, settings, profile…).
- `.bg-gradient-primary` direto: `ui/calendar.tsx`, `AuthPage.tsx`, `ChatPanel.tsx`, `EditProfileModal.tsx:93` e indicadores de Progress em `UpcomingSessionCard`, `NextSessionCard`, `AttendanceSummaryCard` e `AttendanceRankingDialog`.
- `.text-gradient-primary` (wordmark): `AppSidebar.tsx:38`, `MobileTopBar.tsx:14`, `auth/AuthLayout.tsx:12`.
- `bg-brand` / `text-brand` / `border-brand`: `NotificationItem.tsx:57` (ponto de não lida), `ResetLinkSent.tsx:16`, `NotificationUnsubscribePage.tsx:68-70`, `FinanceCreateForm.tsx:29`, `ui/tabs-variants.ts:22` (aba underline ativa).
- `.checkbox-gradient`: `ui/checkbox.tsx`. `.bg-gradient-date` + `.text-on-brand-muted`: `NextSessionCard.tsx:50-53`. `.bg-brand-translucent`: `HomeBanner.tsx:26`.
- `bg-primary`/`text-primary` (o primary **neutro** invertido, não a marca): `MembersGrid`, `MemberCard`, `AttendanceRankingDialog`, `TeamsSection`, `MatchHistoryTable`, `ErrorBoundary`, `AuthPage`, além do estado ativo do toggle `chip` e do `Button` default.

### 2.8 Acessibilidade atual [Fato]

- Foco: os primitivos shadcn usam `focus-visible:ring-2 ring-ring ring-offset-2` (12 arquivos). O `--ring` atual é quase preto (claro) / `0 0% 68%` (escuro).
- 77 `aria-label`, 16 `sr-only`, 3 `aria-live`, 2 `role="status"`.
- **Nenhum tratamento de `prefers-reduced-motion`** (nenhuma ocorrência, nem utilitários `motion-safe/reduce`). Fora da landing, há transform em `.bg-gradient-primary:hover` (`scale(1.03)`), `.page-enter` e as animações `animate-in` do `tailwindcss-animate`.
- `<html lang="en">` (`index.html:2`) com todos os textos em pt-BR.
- Responsivo: mobile-first, breakpoint `md:` (768 px) compartilhado com `useIsMobile`; as telas precisam funcionar em 375 px (CLAUDE.md).

### 2.9 Componentes de domínio relevantes para a identidade esportiva [Fato]

| Área | Componentes |
|---|---|
| Placares / partidas | `daily/ScoreStepper.tsx` (valor 28 px bold tabular), `ResultsMatchCard.tsx`, `SavedMatchesList.tsx`, `ResultsDialog.tsx`, `LiveSessionCard.tsx` |
| Tabelas / ranking | `daily/LiveLeagueTable.tsx`, `daily/FinishedSessionTabs.tsx`, `pelada/RankingTable.tsx` (medalhas no top 3), `pelada/AttendanceRankingDialog.tsx` |
| Prêmios / campeão | `daily/ChampionHero.tsx`, `daily/DailyAwardsGrid.tsx`, `pelada/AwardsTab.tsx` (`.bg-gradient-leader`) |
| Jogadores / stats | `PlayerAvatar.tsx`, `StarRow.tsx`, `pelada/MemberCard.tsx`, `pelada/PlayerHistoryDialog.tsx` (gráfico de referência), `pelada/ComparePlayersDialog.tsx`, `profile/KpiCard.tsx`, `profile/StatsOverTimeChart.tsx`, `profile/ProfilePieChart.tsx`, `profile/MatchHistoryTable.tsx` |
| Sessão / status | `daily/DailyStatusBadge.tsx` + `types/daily.ts` (`dailyStatusStyle`), `pelada/NextSessionCard.tsx`, `home/UpcomingSessionCard.tsx`, `MembershipBadge.tsx`, `SelfConfirmButton.tsx` |
| Shell | `layout/AppSidebar.tsx`, `MobileTopBar.tsx`, `UserMenu.tsx`, `SidebarPeladaItem.tsx`, `pelada/PeladaBanner.tsx`, `home/HomeBanner.tsx` |

---

## 3. Arquitetura atual

```
index.html ──► main.tsx (classe do tema a partir do localStorage) ──► App.tsx (páginas lazy)
                     │
                     └─ import './index.css'
                              │  :root / .dark  →  --token: H S% L%
                              ▼
tailwind.config.js  colors.x = hsl(var(--x))   radius = var(--radius)
                              ▼
components/ui/*  (shadcn, variantes cva em *-variants.ts)  ──►  componentes de feature (bg-card, text-muted-foreground…)
```

- **Uma fonte de verdade por valor** (a variável CSS), consumida pelas classes semânticas do Tailwind. Trocar o tema = alternar `.dark` no `<html>`; não há contexto React.
- Os efeitos visuais compostos são classes comuns em `index.css` (não plugins do Tailwind).
- As variantes ficam em arquivos `*-variants.ts` separados por causa do fast refresh.
- Os gráficos leem `hsl(var(--chart-n))` via `ChartConfig` (referência: `PlayerHistoryDialog.tsx`).

**Avaliação:** a arquitetura serve para a mudança. Já existe camada semântica, os dois temas, suporte a opacidade do Tailwind e um contrato documentado. **Não é preciso um novo motor de temas.**

---

## 4. Auditoria do design system atual

| Dimensão | Estado | Veredito |
|---|---|---|
| Tokens de cor | Conjunto semântico completo + extensões do redesign; claro = slate padrão do shadcn, escuro = cinzas neutros (`0 0% x%`, sem matiz) | Estrutura boa, valores a trocar |
| Marca | Um único `--brand` verde; classes chamadas `gradient`, mas sólidas desde o redesign (FRONTEND.md) | Dívida de nome; texto fixo `text-white` |
| Accent | `--accent` = superfície neutra de hover (semântica shadcn) | Não pode ser reaproveitado |
| Status / feedback | `success`, `warning`, `info`, `destructive*`, `status-*`, `live-*`, `gold/silver/bronze` | Bom; o `info` (azul `200 98% 39%`) vai se confundir com a nova marca |
| Tipografia | Sem famílias, tamanhos arbitrários em px, sem tokens de escala | Maior lacuna |
| Radius | `--radius: .5rem`, `tile 14px`, `hero 18px`, botões `rounded-full` | Coerente, manter |
| Sombras | Pretas, ajustadas para o dark, usadas também no claro | Aceitável; ajuste por tema opcional |
| Movimento | `tailwindcss-animate`, `.page-enter`, keyframes da landing; sem reduced-motion | Lacuna |
| Componentes | 33 primitivos shadcn instalados; variantes via cva | Bom |
| Documentação | Seção "Styling & design system" do FRONTEND.md + tabela de antipatterns | Boa; precisa ser atualizada junto |

Contraste dos tokens **atuais** (seleção):

| Par | Razão | AA texto normal (4,5) |
|---|---|---|
| claro `--muted-foreground` `#64748B` sobre card `#FFF` | 4,76 | aprova |
| claro `--faint-foreground` `#94A3B8` sobre card | **2,56** | **reprova** |
| escuro `--subtle-foreground` `#8A8A8A` sobre card `#262626` | 4,38 | **reprova** (no limite) |
| escuro `--faint-foreground` `#737373` sobre card | **3,19** | **reprova** |
| branco sobre o `--brand` atual `#149440` | 3,93 | **reprova** (texto normal) |
| claro `--input` `#E2E8F0` vs card (não-texto 3:1) | 1,23 | **reprova 1.4.11** |
| escuro `--input` `#383838` vs card | 1,29 | **reprova 1.4.11** |

---

## 5. Problemas e dívida técnica

| # | Problema | Evidência | Impacto nesta migração |
|---|---|---|---|
| P1 | Nenhuma estratégia de carregamento de fonte | `index.html`, `tailwind.config.js` | Novo: fontes, preload, métricas de fallback |
| P2 | `text-white` fixo sobre a marca | `button-variants.ts:14`, `calendar.tsx:46`, `AuthPage.tsx:171`, `ChatPanel.tsx:101`, `NextSessionCard.tsx:50` | Reprova no AA sobre Electric Blue (3,22:1) |
| P3 | Verde fixo fora dos tokens | `index.css` `.bg-brand-translucent`, `.text-on-brand-muted`; `CreatePeladaModal.tsx` | Continuaria verde depois da troca |
| P4 | Componente preso a um tema | `ComparePlayersDialog.tsx`, `ComparablePlayerStatsCard.tsx` | Quebra o claro e a nova paleta |
| P5 | Cores hex nos gráficos | `StatsOverTimeChart.tsx`, `ProfilePieChart.tsx` | Fora da paleta; antipattern #27 |
| P6 | Paleta crua nos gradientes de pelada/avatar | `lib/utils.ts:16-23`, `getAvatarColor` | Fora da paleta ao lado da nova identidade |
| P7 | Texto terciário e bordas de input reprovam no WCAG | tabela da §4 | Corrigir ao reapontar |
| P8 | Tema aplicado depois do CSS, `localStorage` sem proteção, sem `color-scheme`, sem padrão do sistema | `main.tsx:9-15` | Risco de flash, risco de quebra se o storage lançar erro, controles nativos claros no dark |
| P9 | Toaster ignora o tema | `main.tsx:21` | Toasts claros sobre Midnight Black |
| P10 | `lang="en"` | `index.html:2` | Leitores de tela com fonética em inglês (WCAG 3.1.1) |
| P11 | Sem reduced-motion | `index.css` | WCAG 2.2.2 / 2.3.3 |
| P12 | Tamanhos tipográficos arbitrários | §2.5 | Não há escala para mapear os papéis de Barlow/Inter |
| P13 | Nomes de classe enganosos (`gradient` que é sólido) | FRONTEND.md, `index.css` | Renomear junto com a marca |
| P14 | `--info` ≈ matiz da nova marca | `index.css` `--info: 200 98% 39.4%` | Badge de Mensalista e marca indistinguíveis |
| P15 | Nenhuma verificação visual automatizada | §2.1 | Mudança global de tokens sem rede de segurança |

---

## 6. Objetivos e não-objetivos

**Objetivos**

1. Aplicar a identidade Sport-Tech Minimalism (paleta, Barlow Condensed + Inter) no app inteiro, nos temas claro e escuro.
2. Manter a arquitetura de tokens, as classes consumidas pelas features, os componentes shadcn e o mecanismo `.dark`.
3. Todo par texto/fundo usado pelos tokens atende ao WCAG 2.2 AA; bordas de componentes de UI e indicadores de foco atingem 3:1.
4. Remover as ilhas de cor fixa da §5 (P2–P6), fechando as entradas dos antipatterns #3 e #27 do FRONTEND.md (exceto a Landing).
5. Nenhum flash de tema no carregamento; sem escolha salva, seguir o sistema operacional; a escolha explícita continua em `localStorage.theme`; o Toaster segue o tema.
6. Fontes self-hosted via `@fontsource`, subset latino, `font-display: swap` e fallbacks com métricas compatíveis; nenhuma regressão de layout em 375 px.
7. Regressão visual automatizada com Playwright cobrindo as telas principais nos dois temas e em 375/1280 px.

**Não-objetivos**

- Migrar para Tailwind 4, adotar biblioteca de tema (ex.: `next-themes`) ou CSS-in-JS.
- Mudar layouts, arquitetura de informação, rotas, textos ou regras de negócio.
- Redesenhar telas além do que os tokens/tipografia implicam (sem componentes novos).
- Mudar as cores dos times (dado do usuário) ou logos de terceiros.
- Redesenhar logo/favicon (o wordmark só muda de cor via token).
- **Refatorar a Landing Page** (outra feature). Ela recebe só os efeitos globais inevitáveis (tokens que já consome, regra de reduced-motion e fonte padrão).

---

## 7. Identidade visual proposta

### 7.1 Papéis

| Primitiva | Hex | Papel no sistema |
|---|---|---|
| Midnight Black | `#07070D` | Fundo de página no escuro; **texto sobre os preenchimentos de marca e de highlight (nos dois temas)** |
| Dark Surface | `#141820` | Cards/popovers no escuro |
| Electric Blue | `#0799D5` | Preenchimento da marca (CTA, progress, checkbox, aba ativa, ponto de não lida), anel de foco no escuro |
| Cantaloupe Orange | `#FF7356` | Highlight: momentos de conquista (campeão, prêmios da sessão, número em destaque nos KPIs); nunca com sentido destrutivo (ver Q5) |
| Off-White | `#F6F7F9` | Fundo de página no claro; texto no escuro |
| Muted | `#8793A5` | Texto secundário no escuro (aprova AA nas superfícies escuras) |

Cores complementares derivadas por acessibilidade (todas calculadas):

| Nome | Hex | Motivo |
|---|---|---|
| Blue Strong | `#0A6E99` | Azul *como texto/anel* no claro (`#0799D5` dá só 3,00:1 sobre Off-White) |
| Blue Bright | `#3BB4E8` | Hover do preenchimento azul / azul como texto em superfícies escuras elevadas (7,52:1 sobre `#141820`) |
| Orange Strong | `#B4442B` | Laranja *como texto* no claro (`#FF7356` dá 2,68:1 sobre branco) |
| Ink | `#0B0D12` | Texto do tema claro (18,13:1 sobre Off-White) |
| Slate 700 | `#5B6676` | Texto secundário no claro (5,43:1 sobre Off-White) |
| Midnight 900 | `#0E1117` | Sidebar no escuro (um degrau abaixo da página) |
| Surface 700 | `#1C212B` | Superfície elevada no escuro (cabeçalhos de tabela, card dentro de card) |
| Surface 600 | `#262C38` | Divisores no escuro |
| Line 200 | `#DDE1E8` | Divisores no claro |
| Indigo 600 / 300 | `#4F46C8` / `#A5A6F6` | Novo `--info` (claro / escuro), distinto da marca (ver Q4) |

### 7.2 Validação de contraste (pares propostos)

| Texto/elemento | Fundo | Razão | Uso | Resultado |
|---|---|---|---|---|
| `#F6F7F9` | `#07070D` | 18,74 | texto no escuro | AAA |
| `#F6F7F9` | `#141820` | 16,58 | texto de card no escuro | AAA |
| `#8793A5` | `#141820` / `#1C212B` | 5,71 / 5,18 | texto secundário no escuro | AA |
| `#808A9C` | `#141820` / `#1C212B` | 5,11 / 4,63 | texto terciário no escuro | AA |
| `#07070D` | `#0799D5` | 6,24 | texto sobre a marca | AA |
| `#FFFFFF` | `#0799D5` | **3,22** | — | **reprova texto normal** (AA só para texto grande) |
| `#0799D5` | `#141820` | 5,52 | marca como texto/anel no escuro | AA |
| `#0799D5` | `#F6F7F9` | **3,00** | — | **reprova como texto**; só serve como não-texto (no limite de 3:1) |
| `#0A6E99` | `#F6F7F9` / `#FFF` / `#EDEFF3` | 5,29 / 5,67 / 4,93 | marca como texto, link e anel no claro | AA |
| `#07070D` | `#FF7356` | 7,50 | texto sobre highlight | AAA |
| `#FFFFFF` | `#FF7356` | **2,68** | — | **reprova** |
| `#FF7356` | `#141820` | 6,64 | highlight como texto no escuro | AA |
| `#B4442B` | `#FFF` / `#F6F7F9` / `#EDEFF3` | 5,52 / 5,15 / 4,80 | highlight como texto no claro | AA |
| `#0B0D12` | `#F6F7F9` | 18,13 | texto no claro | AAA |
| `#5B6676` | `#FFF` / `#F6F7F9` / `#EDEFF3` | 5,82 / 5,43 / 5,06 | texto secundário no claro | AA |
| `#626D7E` | `#FFF` / `#F6F7F9` / `#EDEFF3` | 5,24 / 4,89 / 4,55 | texto terciário no claro | AA |
| `#646F82` | `#141820` / `#1C212B` | 3,50 / 3,18 | borda de input no escuro (não-texto) | ≥3:1 |
| `#7A8699` | `#FFF` / `#F6F7F9` | 3,69 / 3,44 | borda de input no claro (não-texto) | ≥3:1 |
| `#B4442B` | `#FFF1EE` | 5,01 | badge de highlight no claro | AA |
| `#FF7356` | `#2A1612` | 6,41 | badge de highlight no escuro | AA |
| `#0A6E99` | `#E6F5FC` | 5,09 | badge brand-muted no claro | AA |
| `#3BB4E8` | `#0B2633` | 6,63 | badge brand-muted no escuro | AA |
| `#4F46C8` | `#EEEEFC` / `#FFF` / `#F6F7F9` | 6,01 / 6,90 / 6,44 | info (Mensalista) no claro | AA |
| `#A5A6F6` | `#1E1D3D` / `#141820` / `#07070D` | 7,23 / 7,95 / 8,98 | info (Mensalista) no escuro | AAA |
| `#F87171` / `#DC2626` | card escuro / branco | 6,43 / 4,83 | texto destrutivo | AA |
| `#4ADE80` / `#15803D` | card escuro / branco | 10,20 / 5,02 | texto de sucesso | AA |
| `#FACC15` / `#A16207` | card escuro / branco | 11,61 / 4,92 | texto dourado | AA |

**Regra derivada da tabela (Q9):** os *preenchimentos* de marca e de highlight sempre levam texto Midnight (`--brand-foreground`, `--highlight-foreground`), nos dois temas. A marca/highlight *como texto* usa os tokens por tema `--brand-text` / `--highlight-text`.

---

## 8. Arquitetura de design tokens

### 8.1 Opções consideradas

| Opção | Descrição | Prós | Contras |
|---|---|---|---|
| **A. Reapontar os tokens semânticos atuais + camada de primitivas (aprovada)** | Primitivas independentes de tema (`--p-*`, triplets) em `:root`; os tokens semânticos em `:root`/`.dark` referenciam as primitivas; classes das features inalteradas | Mínimo de mudança em componentes; mantém compatibilidade com shadcn, modificadores de opacidade e docs; as primitivas documentam a paleta | Uma indireção a mais no `index.css` |
| B. Reapontar os tokens com valores literais (sem primitivas) | Só trocar os triplets | Menor diff | Paleta repetida nos dois temas; mais difícil auditar/testar contraste |
| C. Novo sistema de tokens (`@theme` do Tailwind 4, pacote JS / Style Dictionary) | Reconstruir | Pipeline moderno | Upgrade do Tailwind 4 + regeneração do shadcn; risco alto; fora do escopo |

### 8.2 Camadas

```
Primitiva (independente de tema, só em :root, NÃO entra no tailwind.config)
   --p-midnight-950  --p-blue-500  --p-orange-400 …
        ▼ referenciada por
Semântica (por tema, :root e .dark; mapeada no tailwind.config como hoje)
   --background  --card  --brand  --brand-foreground  --highlight  --ring …
        ▼ consumida por
Variantes de componente (cva) e classes das features
   bg-card  text-muted-foreground  Button variant="brand"
```

As primitivas **não ficam expostas** ao Tailwind de propósito, para que nenhum componente pule a camada semântica (reforça o antipattern #3). Variáveis CSS são substituídas antes de o `hsl()` ser avaliado, então `--brand: var(--p-blue-500)` mantém `hsl(var(--brand) / 0.2)` funcionando.

Exemplo (apenas documentação — não aplicado):

```css
:root {
  /* primitivas */
  --p-midnight-950: 240 30% 3.9%;   /* #07070D */
  --p-blue-500:     197 94% 43.1%;  /* #0799D5 */
  --p-blue-700:     198 88% 32%;    /* #0A6E99 */
  --p-orange-400:   10 100% 66.9%;  /* #FF7356 */
  /* semântica, claro */
  --brand: var(--p-blue-500);
  --brand-foreground: var(--p-midnight-950);
  --brand-text: var(--p-blue-700);
  --ring: var(--p-blue-700);
}
.dark {
  --brand-text: var(--p-blue-500);
  --ring: var(--p-blue-500);
}
```

### 8.3 Tokens primitivos

| Token | Hex | Triplet HSL |
|---|---|---|
| `--p-midnight-950` | `#07070D` | `240 30% 3.9%` |
| `--p-midnight-900` | `#0E1117` | `220 24% 7.3%` |
| `--p-surface-800` | `#141820` | `220 23% 10.2%` |
| `--p-surface-700` | `#1C212B` | `220 21% 13.9%` |
| `--p-surface-600` | `#262C38` | `220 19% 18.4%` |
| `--p-slate-550` | `#646F82` | `218 13% 45.1%` |
| `--p-slate-500` | `#8793A5` | `216 14% 58.8%` |
| `--p-slate-520` | `#808A9C` | `219 12% 55.7%` |
| `--p-slate-450` | `#7A8699` | `217 13% 53.9%` |
| `--p-slate-650` | `#626D7E` | `216 12% 43.9%` |
| `--p-slate-700` | `#5B6676` | `216 13% 41%` |
| `--p-line-200` | `#DDE1E8` | `218 19% 88.8%` |
| `--p-mist-100` | `#EDEFF3` | `220 20% 94.1%` |
| `--p-offwhite-50` | `#F6F7F9` | `220 20% 97.1%` |
| `--p-white` | `#FFFFFF` | `0 0% 100%` |
| `--p-ink-950` | `#0B0D12` | `223 24% 5.7%` |
| `--p-blue-300` | `#3BB4E8` | `198 79% 57.1%` |
| `--p-blue-500` | `#0799D5` | `197 94% 43.1%` |
| `--p-blue-700` | `#0A6E99` | `198 88% 32%` |
| `--p-blue-50` | `#E6F5FC` | `199 79% 94.5%` |
| `--p-blue-950` | `#0B2633` | `200 65% 12.2%` |
| `--p-orange-400` | `#FF7356` | `10 100% 66.9%` |
| `--p-orange-700` | `#B4442B` | `11 61% 43.7%` |
| `--p-orange-50` | `#FFF1EE` | `11 100% 96.7%` |
| `--p-orange-950` | `#2A1612` | `10 40% 11.8%` |
| `--p-indigo-600` | `#4F46C8` | `244 54% 52.9%` |
| `--p-indigo-300` | `#A5A6F6` | `239 82% 80.6%` |
| `--p-indigo-50` | `#EEEEFC` | `240 70% 96.1%` |
| `--p-indigo-950` | `#1E1D3D` | `242 36% 17.6%` |

A numeração é uma proposta de nome; a Phase 2 pode renomear desde que esta tabela e o teste de contraste continuem em sincronia.

### 8.4 Tokens semânticos (nomes atuais mantidos)

| Token | Claro | Escuro | Notas |
|---|---|---|---|
| `--background` | offwhite-50 | midnight-950 | |
| `--foreground` | ink-950 | offwhite-50 | |
| `--card`, `--popover` | white | surface-800 | |
| `--card-foreground`, `--popover-foreground` | ink-950 | offwhite-50 | |
| `--card-elevated` | mist-100 | surface-700 | |
| `--row-hover` | `220 20% 95.5%` | `220 22% 12%` | entre card e elevated |
| `--primary` | ink-950 | offwhite-50 | **continua neutro** (Q2): Button default, chips ativos |
| `--primary-foreground` | offwhite-50 | midnight-950 | |
| `--secondary`, `--muted`, `--accent` | mist-100 | surface-700 | `--accent` continua a superfície de hover do shadcn |
| `--secondary-foreground`, `--accent-foreground` | ink-950 | offwhite-50 | |
| `--muted-foreground` | slate-700 | slate-500 | 5,43 / 5,71 |
| `--subtle-foreground` | slate-700 | slate-500 | unificado com o muted (hoje diferem em menos de 1%) |
| `--faint-foreground` | slate-650 | slate-520 | corrigido para AA (hoje reprova) |
| `--border` | line-200 | surface-600 | divisores decorativos (sem exigência de 3:1) |
| `--input` | slate-450 | slate-550 | borda de controle de formulário, ≥3:1 |
| `--ring` | blue-700 | blue-500 | indicador de foco ≥3:1 contra as cores vizinhas |
| `--brand` | blue-500 | blue-500 | preenchimento |
| `--brand-foreground` **(novo)** | midnight-950 | midnight-950 | texto/ícone sobre a marca |
| `--brand-hover` **(novo)** | blue-300 | blue-300 | substitui o hover com `filter: brightness()`; 8,50:1 com Midnight |
| `--brand-text` **(novo)** | blue-700 | blue-500 | marca como texto/ícone sobre superfícies |
| `--brand-muted` **(novo)** | blue-50 | blue-950 | pílulas translúcidas da marca (substitui `.bg-brand-translucent`) |
| `--highlight` **(novo)** | orange-400 | orange-400 | preenchimento Cantaloupe |
| `--highlight-foreground` **(novo)** | midnight-950 | midnight-950 | |
| `--highlight-text` **(novo)** | orange-700 | orange-400 | |
| `--highlight-muted` **(novo)** | orange-50 | orange-950 | badges/tiles |
| `--chart-1` | blue-700 | blue-500 | série principal (gols) |
| `--chart-2` | orange-700 | orange-400 | segunda série (assistências) |
| `--chart-3` **(novo)** | slate-700 | slate-500 | terceira série (partidas) |
| `--info` / `--info-muted` | indigo-600 / indigo-50 | indigo-300 / indigo-950 | novo matiz, distinto da marca (Q4) |
| `--success*`, `--warning`, `--destructive*`, `--gold*`, `--silver`, `--bronze`, `--status-*`, `--live-*` | manter os matizes; reajustar a luminosidade para as novas superfícies; validar cada par no teste de contraste | | `--live-*` continua verde ("ao vivo") |
| `--avatar-1…6`, `--avatar-fallback`, `--star-off`, `--team-dot-border` | manter; revalidar iniciais brancas ≥4,5 | | passam a servir também às peladas (Q10) |
| `--sidebar-*` | sidebar = offwhite-50 / midnight-900; primary = brand-text; ring = ring | | |
| `--overlay` **(novo, opcional)** | midnight-950 a 60–80% | igual | substitui `bg-black/60–80` nos overlays `ui/*` |

### 8.5 Radius, espaçamento, sombras e estados

- **Radius:** manter `--radius: 0.5rem`, `tile 14px`, `hero 18px` e botões em pílula. O minimalismo esportivo vem de superfícies planas e bordas precisas, não de mudar o raio.
- **Espaçamento:** nenhum token novo; a escala do Tailwind já é usada em tudo.
- **Sombras:** manter os nomes; no claro, reduzir a opacidade via variável de tema (`--shadow-color`) — opcional, prioridade baixa.
- **Estados de interação** (documentados aqui, implementados nas variantes):

| Estado | Regra |
|---|---|
| Hover (marca) | `bg-brand-hover`; sem `scale()` (sensível a movimento e faz o layout "tremer") |
| Hover (neutro) | os atuais `hover:bg-accent` / `bg-row-hover` |
| Ativo/pressionado | `bg-brand/90`; opcional |
| Focus-visible | `ring-2 ring-ring ring-offset-2 ring-offset-background` (mesmo utilitário; cor nova) |
| Selecionado (aba underline, chips, checkbox) | preenchimento da marca ou `border-brand` + check em `brand-foreground` |
| Desabilitado | os atuais `opacity-50` + `pointer-events-none`; texto desabilitado é isento de contraste |
| Erro | os estilos atuais de `destructive` / `aria-invalid` |

### 8.6 Tokens específicos de componente

Só os justificados: `--brand-*` (Button, Checkbox, Progress, aba underline, dia selecionado do Calendar), `--highlight-*` (momentos de conquista, Q5) e `--overlay`. Nenhum arquivo de tokens por componente.

### 8.7 Renomeações de classes (compatibilidade)

| Hoje | Proposta | Compatibilidade |
|---|---|---|
| `Button variant="gradient"` | `variant="brand"` | `gradient` fica como alias até o passo de limpeza; então codemod nos 33 arquivos |
| `.bg-gradient-primary` | Tailwind `bg-brand` + `hover:bg-brand-hover` | classe mantida temporariamente, apontando para os novos tokens |
| `.text-gradient-primary` | `text-brand-text` | idem |
| `.checkbox-gradient` | `border-brand data-[state=checked]:bg-brand` em `ui/checkbox.tsx` | remover a classe depois da migração |
| `.bg-gradient-date` | `bg-brand text-brand-foreground` | |
| `.bg-brand-translucent` / `.text-on-brand-muted` | `bg-brand-muted text-brand-text border-brand/30` | |

---

## 9. Sistema tipográfico

### 9.1 Famílias

| Token | Stack | Uso |
|---|---|---|
| `font-sans` (substitui o padrão do Tailwind) | `"Inter Variable", Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | tudo, por padrão |
| `font-display` **(novo)** | `"Barlow Condensed", "Arial Narrow", "Roboto Condensed", ui-sans-serif, sans-serif` | placares, posições do ranking, números grandes, títulos de página/hero, blocos de data |
| `font-mono` | inalterado | texto tipo código (`DeleteDailyDialog`, textarea de importação) |

Regras:

- Barlow Condensed **nunca abaixo de 16 px**, e nunca em parágrafos, labels, texto de formulário, corpo de tabela ou botões.
- Texto display em caixa alta usa `tracking-wide` (≈0,02–0,04em); labels em caixa alta com Inter mantêm `0.06em`.
- Números alinhados (tabelas, placares, contadores) usam `tabular-nums`. **A Phase 2 precisa verificar** se os arquivos da Barlow Condensed suportam `tnum`; se não, colunas alinhadas continuam em Inter e a Barlow fica só em números isolados.
- O "VS" do `ComparePlayersDialog` passa de `font-mono` para `font-display`.

### 9.2 Escala (tokens `fontSize` nomeados no Tailwind)

Mapeada a partir dos tamanhos que já existem (§2.5), então a migração é renomear, não redesenhar.

| Token | Tamanho / altura de linha | Família · peso | Tracking | Substitui | Exemplo |
|---|---|---|---|---|---|
| `text-overline` | 11 / 16 | Inter 600, caixa alta | 0.06em | labels `text-[11px]`, `text-[10px]` | "MINHAS PELADAS" |
| `text-caption` | 12 / 16 | Inter 400–500 | 0 | `text-xs` | linhas de metadados |
| `text-body-sm` | 13 / 18 | Inter 400–500 | 0 | `text-[13px]` | chips, tabelas, texto secundário |
| `text-body` | 14 / 20 | Inter 400 | 0 | `text-sm` | texto padrão da UI |
| `text-body-lg` | 15 / 22 | Inter 400–500 | 0 | `text-[15px]` | linhas de destaque dos cards |
| `text-title` | 17 / 24 | Inter 600 | -0.005em | `text-[17px]` | h2 de seção, títulos de card |
| `text-heading` | 22 → 28 (md) / 1.15 | Barlow Condensed 700 | 0.01em | h1 `text-[22px]`, `text-[28px]` | h1 da página (`PeladaPageHeader`), data "Qui, 08 out" |
| `text-display` | 32 → 44 (md) / 1.05 | Barlow Condensed 800 | 0.01em | `text-[32px]`, `text-[38px]` | saudação da Home, nome do campeão |
| `text-score` | 28 → 36 (md) / 1 | Barlow Condensed 700, tabular | 0 | `ScoreStepper` lg `text-[28px]` | placares, valores de KPI, números do ranking |
| `text-hero` | 48 → 56 / 1 | Barlow Condensed 800 | 0 | `text-[56px]` | iniciais do banner da pelada |

Responsividade: os degraus usam o breakpoint `md:` existente (sem `clamp()`, comportamento previsível em 375 px). O corpo nunca fica abaixo de 13 px; o conteúdo em `text-[9px]`/`text-[10px]` (badges) é revisto na Phase 2 — o WCAG não define tamanho mínimo, mas 11 px é o piso proposto.

Pesos entregues: Inter variable (400–700 num único arquivo); Barlow Condensed estática só **600, 700 e 800** (≈3 arquivos).

### 9.3 Carregamento (Q3: `@fontsource`)

- Dependências: `@fontsource-variable/inter` e `@fontsource/barlow-condensed` (pesos 600/700/800), instaladas com `bun add`.
- Import só dos subsets latinos (`.../latin.css` ou equivalente do pacote) — os diacríticos do pt-BR estão em Basic Latin + Latin-1. Os arquivos são empacotados e versionados por hash pelo Vite, servidos da mesma origem (sem requisição a terceiros, compatível com LGPD e com o nginx do VPS).
- `font-display: swap` (padrão do fontsource).
- `<link rel="preload" as="font" type="font/woff2" crossorigin>` só para a **Inter** (a mais visível no primeiro paint). Como o nome do arquivo é hasheado, o preload é gerado no build (import `?url` do woff2 + injeção no `index.html` via plugin `transformIndexHtml`, ou omitido se a medição mostrar ganho desprezível).
- `@font-face` de fallback com `size-adjust`/`ascent-override` (Inter → Arial) para limitar CLS; valores medidos na Phase 2.
- Orçamento: ≤ 150 KB de woff2 no total.

---

## 10. Estratégia de tema claro/escuro

Mantém-se: classe `.dark` no `<html>`, `darkMode: ["class"]`, `localStorage.theme` (`"dark" | "light"`) e o `useTheme` (API inalterada).

Mudanças (todas pequenas, em arquivos existentes):

1. **Bootstrap antes do primeiro paint:** mover a lógica de `main.tsx:9-15` para um `<script>` inline curto no `<head>` do `index.html`, com try/catch:
   ```html
   <script>
     try {
       var t = localStorage.getItem("theme");
       var mq = matchMedia("(prefers-color-scheme: dark)");
       document.documentElement.classList.toggle("dark", t ? t === "dark" : mq.matches);
       if (!t) mq.addEventListener("change", function (e) {
         if (!localStorage.getItem("theme")) document.documentElement.classList.toggle("dark", e.matches);
       });
     } catch (e) {}
   </script>
   ```
   O `main.tsx` deixa de mexer na classe. O `useTheme` já observa a classe, então a mudança do sistema chega sozinha à UI. Não há CSP neste repositório; a config do nginx do VPS não é versionada, então a Phase 2 precisa conferir. Se existir CSP, liberar o script por hash.
2. **Padrão sem escolha salva (Q1):** seguir o sistema operacional (`prefers-color-scheme`), acompanhando mudanças enquanto o usuário não escolher. A escolha explícita pelo toggle sempre vence e para de acompanhar o sistema.
3. **`color-scheme`:** `:root { color-scheme: light } .dark { color-scheme: dark }` no `index.css`, para scrollbars, inputs de data e autofill seguirem o tema.
4. **`<meta name="theme-color">`** para os dois esquemas (cor da barra do navegador mobile = token de fundo).
5. **Sonner:** passar `theme={isDark ? "dark" : "light"}` ao `<Toaster>` (um `ThemedToaster` pequeno que lê `useTheme`) e estilizar os toasts com tokens (`toastOptions.classNames`).
6. **Sem transição na troca de tema** (não animar todas as cores); classe `disable-transitions` temporária no toggle só se aparecer cintilação.
7. Overlays de foto (`.bg-*-overlay`) e texto branco sobre foto continuam independentes de tema (já documentado).

---

## 11. Matriz de migração de componentes

Legenda — **Automático**: muda só pelos tokens, sem editar o arquivo. **Explícito**: o arquivo precisa ser editado.

### 11.1 Automáticos (só tokens)

| Componente | Arquivos | Mudança | Prioridade | Risco |
|---|---|---|---|---|
| Superfícies e texto shadcn | `ui/card, dialog, alert-dialog, sheet, drawer, popover, dropdown-menu, command, select, table, tabs (default/pill), tooltip, alert, skeleton, separator, breadcrumb, field, label, input, textarea, switch, toggle, toggle-group, avatar, progress (trilho), sidebar` | Novos neutros, bordas, borda de input, anel | P0 | Baixo — conferir que a borda de input fica mais marcada (intencional, 3:1) |
| Aba underline ativa | `ui/tabs-variants.ts:22` (`border-brand`) | Sublinhado azul | P0 | Baixo |
| Badges de status/feedback | `daily/DailyStatusBadge.tsx`, `types/daily.ts`, `MembershipBadge.tsx`, `ui/badge-variants.ts` | Tokens de status reajustados; Mensalista em indigo | P1 | Baixo |
| Listas/tabelas de domínio | `LiveLeagueTable`, `RankingTable`, `FinishedSessionTabs`, `AttendanceList`, `SessionHistoryList`, `MembersGrid`, `MemberCard`, componentes de finance, notificações | Superfícies/texto | P1 | Baixo |
| Gráficos com tokens | `pelada/PlayerHistoryDialog.tsx`, `daily/ImportFromMessageModal.tsx` | `--chart-1/2` viram azul/laranja | P1 | Baixo |
| Líder dos prêmios / card ao vivo | `AwardsTab.tsx` (`.bg-gradient-leader`), `LiveSessionCard.tsx` (`.bg-gradient-live`) | Pelos tokens | P2 | Baixo |
| Indicadores de progresso | `UpcomingSessionCard`, `NextSessionCard`, `AttendanceSummaryCard`, `AttendanceRankingDialog` (`bg-gradient-primary`) | Azul pelo remapeamento da classe | P1 | Baixo |
| Wordmark | `AppSidebar.tsx:38`, `MobileTopBar.tsx:14`, `auth/AuthLayout.tsx:12` (`.text-gradient-primary`) | → `--brand-text` | P1 | Baixo |
| Ícones/pontos da marca | `NotificationItem.tsx:57`, `ResetLinkSent.tsx:16`, `NotificationUnsubscribePage.tsx:68-70`, `FinanceCreateForm.tsx:29` | `text-brand` deve virar `text-brand-text` (renomeação explícita recomendada) | P1 | Baixo — `#0799D5` como texto sobre Off-White dá 3,00:1 |

### 11.2 Edições explícitas

| Componente | Arquivos | Mudança | Prioridade | Risco |
|---|---|---|---|---|
| Tokens e base | `src/index.css` | Camada de primitivas, reapontar semânticos, tokens novos, `color-scheme`, bloco de reduced-motion, remapear/renomear classes da marca (as `.landing-*` ficam como estão) | P0 | Alto — afeta todas as telas; mitigado pelo teste de contraste + Playwright |
| Config do Tailwind | `tailwind.config.js` | `fontFamily` (sans, display), escala `fontSize`, cores novas (`brand.{DEFAULT,foreground,hover,text,muted}`, `highlight.*`, `chart.3`, `overlay`) | P0 | Médio — `brand` passa de string para objeto (manter `DEFAULT`) |
| Shell do documento | `index.html`, `src/main.tsx` | `lang="pt-BR"`, script inline de tema, preload de fonte, `theme-color`; tirar a lógica de classe do `main.tsx`; Toaster com tema | P0 | Médio — regressão de FOUC; testar hard reload nos dois temas |
| Button | `ui/button-variants.ts` | variante `brand` (`bg-brand text-brand-foreground hover:bg-brand-hover`), alias `gradient`; variante opcional `highlight` | P0 | Médio — 33 consumidores; o alias evita um diff gigante |
| Checkbox | `ui/checkbox.tsx` + `.checkbox-gradient` | Borda/preenchimento da marca, check em Midnight | P1 | Baixo |
| Dia selecionado do Calendar | `ui/calendar.tsx:46` | `bg-brand text-brand-foreground` | P1 | Baixo |
| Overlays | `ui/dialog, alert-dialog, sheet, drawer, sidebar` (`bg-black/60–80`) | `bg-overlay` opcional (tom Midnight) | P3 | Baixo — código shadcn próprio, manter compatível |
| Botão de login | `pages/AuthPage.tsx:171` | `variant="brand"` em vez de sobrescrever classes | P1 | Baixo |
| Bolha própria do chat | `pelada/ChatPanel.tsx:101` | `bg-brand text-brand-foreground` | P1 | Baixo |
| Bloco de data da próxima sessão | `pelada/NextSessionCard.tsx:50-53` | `bg-brand text-brand-foreground`, labels internos `text-brand-foreground/80` (validar ≥4,5) e Barlow no dia | P1 | Baixo |
| Pílula de data do banner da Home | `home/HomeBanner.tsx:26` | `bg-brand-muted`…; fica sobre o overlay da foto: validar contra o overlay, talvez precise de pílula Midnight sólida | P2 | Médio |
| Cabeçalho do editar perfil | `profile/EditProfileModal.tsx:93` | `bg-brand` (decorativo) | P3 | Baixo |
| Comparar jogadores | `pelada/ComparePlayersDialog.tsx`, `pelada/ComparablePlayerStatsCard.tsx` | Trocar `zinc-*`/`white` por tokens; passa a funcionar no claro | P1 | Médio — 55 classes, revisão visual |
| KPIs do perfil | `pages/ProfilePage.tsx:107-115`, `profile/KpiCard.tsx` | Ícones → `brand-text`/`highlight-text`/`success`/`muted-foreground`; valor em `text-score font-display` | P2 | Baixo |
| Gráficos do perfil | `profile/StatsOverTimeChart.tsx`, `profile/ProfilePieChart.tsx` | Hex → `hsl(var(--chart-1/2/3))` via `ChartConfig` | P1 | Baixo — fecha o antipattern #27 |
| Destaque do histórico de partidas | `profile/MatchHistoryTable.tsx:46-48` | `bg-success-muted text-success` / `bg-gold-muted text-gold` | P2 | Baixo |
| Anéis de foco do criar pelada | `CreatePeladaModal.tsx` (12) | Remover as sobrescritas `ring-green-*` (o anel padrão já está certo) e a do checkbox | P2 | Baixo |
| Gradientes de pelada/avatar (Q10) | `lib/utils.ts` `getPeladaGradient`, `getAvatarColor` | Mapear para os 6 tokens `bg-avatar-1…6` (determinístico por nome/id, como hoje); revalidar iniciais brancas ≥4,5 | P2 | Médio — muda a cor de identificação de cada pelada |
| Papéis tipográficos | `ScoreStepper`, `ResultsMatchCard`, `SavedMatchesList`, `LiveLeagueTable`, `RankingTable`, `ChampionHero`, `DailyAwardsGrid`, `HomeBanner`, `PeladaBanner`, `PeladaPageHeader`, `DailyHeader`, `NextSessionCard`, `UpcomingSessionCard`, `KpiCard` | Aplicar `font-display` + tokens de escala em placares/posições/títulos | P1 | Médio — a largura condensada muda truncamentos; conferir em 375 px |
| Tamanhos arbitrários restantes | ~120 ocorrências (`text-[13px]` etc.) | Trocar pelos tokens da escala oportunamente (mesmos px) | P3 | Baixo (idêntico ao pixel) |
| Highlight (Q5) | `daily/ChampionHero.tsx`, `daily/DailyAwardsGrid.tsx`, `pages/ProfilePage.tsx` (KPI de Gols) | Aplicar `highlight-*` (ver §17, Q5) | P2 | Médio — sobreposição com gold |
| Landing | `pages/LandingPage.tsx`, `.landing-*` | **Fora do escopo** — outra feature | — | — |
| Logo / favicon | `public/gerrard.png` | Fora do escopo | — | — |

---

## 12. Acessibilidade e responsividade

Meta: **WCAG 2.2 AA**.

| Critério | Exigência para esta mudança | Como verificar |
|---|---|---|
| 1.4.3 Contraste (texto) | Todo par semântico texto/fundo ≥ 4,5:1 (≥ 3:1 para ≥ 24 px ou ≥ 18,66 px bold) | teste automatizado de contraste (§15) sobre os pares da §7.2 |
| 1.4.11 Contraste não-textual | `--input` vs superfícies ≥ 3:1; `--ring` vs fundo ≥ 3:1; bordas de checkbox/switch ≥ 3:1; ícones com significado ≥ 3:1 | mesmo teste |
| 1.4.1 Uso de cor | Status nunca só por cor (badges mantêm texto; medalhas mantêm o número; ponto de não lida mantém `aria-label`) | revisão |
| 2.4.7 / 2.4.11 / 2.4.13 Foco | Anel `focus-visible` visível, 2 px, com offset, ≥3:1; não escondido pela `MemberSelectionBar` sticky, pelo botão do chat ou pela `MobileTopBar` (`scroll-margin` quando preciso) | passada de teclado |
| 2.1.1 Teclado | Comportamento do Radix inalterado; as variantes novas mantêm o foco | passada de teclado |
| 2.5.8 Tamanho do alvo (mín. 24 px) | Controles atuais de 32–40 px inalterados; conferir chips `h-8` e o "+" de 26 px da sidebar | revisão |
| 2.2.2 / 2.3.3 Movimento | `@media (prefers-reduced-motion: reduce)` global que zera as animações infinitas (inclusive as da landing, sem refatorá-la), os hovers com `scale` e as durações `animate-in` (≈0,01 ms) | ajuste do sistema + Playwright com `reducedMotion: 'reduce'` |
| 3.1.1 Idioma | `<html lang="pt-BR">` | checagem no DOM / Playwright |
| 4.1.3 Mensagens de status | Toasts mantêm a live region do Sonner, agora com tema | revisão |
| Tipografia | Corpo ≥ 13 px; Barlow ≥ 16 px; alturas de linha da §9.2; zoom de texto 200% sem perda | zoom do navegador |
| Responsivo | Toda tela tocada em 375, 768 e 1280 px nos dois temas; a fonte condensada não pode criar scroll horizontal | Playwright (375/1280) + matriz manual (768) |

---

## 13. Plano de implementação (Phase 2 — não executado)

Cada passo é um PR separado, entregável sozinho, com `bun run lint && bun run build && bun run test` verde e o FRONTEND.md atualizado no mesmo PR. Ordem de dependência: `1 → 2 → 3 → {4, 5, 6} → 7 → 8`.

### Passo 1 — Base: shell do documento e bootstrap do tema
- **Escopo:** `lang="pt-BR"`, script inline de tema (padrão do sistema + listener enquanto não houver escolha), `color-scheme`, `theme-color`, storage protegido, Toaster com tema, regra base de reduced-motion.
- **Arquivos:** `index.html`, `src/main.tsx`, `src/index.css` (só `color-scheme` + reduced-motion), talvez `src/App.tsx`.
- **Dependências:** nenhuma.
- **Riscos:** FOUC/regressão na persistência do tema.
- **Validação:** hard reload com `theme=dark`, `theme=light` e sem valor (sistema claro/escuro); trocar o tema do sistema com a aba aberta; storage bloqueado.
- **Aceite:** nenhum quadro branco no reload em dark (filmstrip do DevTools); toasts escuros no dark; o toggle continua igual e, depois de usado, deixa de seguir o sistema.

### Passo 2 — Regressão visual com Playwright (baseline antes de mudar cores) (Q8)
- **Escopo:** infraestrutura e baseline do visual **atual**, para que os passos seguintes mostrem diffs revisáveis.
- **Arquivos:** `package.json`/`bun.lock` (`@playwright/test` como devDependency), `playwright.config.ts`, `e2e/visual/*.spec.ts`, `e2e/fixtures/*.json`, `e2e/tsconfig.json`, `vitest.config.ts` (adicionar `exclude: ['e2e/**', …]` — o include padrão do Vitest pegaria `*.spec.ts`), `.github/workflows/ci-frontend.yml` (job novo `visual`), `.gitignore` (`test-results/`, `playwright-report/`).
- **Desenho:**
  - Roda contra `vite preview` (build de produção) via `webServer` do Playwright; script `bun run test:visual` (e `test:visual:update`).
  - **Sem backend:** `page.route('**/api/v1/**')` responde com fixtures JSON (usuário, pelada, sessões agendada/ao vivo/finalizada, ranking, perfil, notificações); `/ws` é abortado, e a tela tolera o estado de reconexão. Login simulado gravando `futspring_token`/`futspring_user` via `addInitScript`.
  - Determinismo: `page.clock.setFixedTime(...)`, `animations: 'disabled'`, `reducedMotion: 'reduce'`, espera por `document.fonts.ready`, máscara para avatares/imagens remotas.
  - Matriz: Chromium; `colorScheme` light/dark (e `localStorage.theme` vazio para validar o padrão do sistema); viewports 375×812 e 1280×800.
  - Rotas: `/auth`, `/home`, `/pelada/:id` (visão geral, membros, sessões, ranking, prêmios, financeiro), `/pelada/:id/settings`, `/daily/:id` (agendada, ao vivo, finalizada), `/profile/:id`, `/settings/notifications` e os diálogos de resultados, comparar jogadores e ranking de presença.
  - Asserções extras baratas: `lang="pt-BR"`, ausência de scroll horizontal (`scrollWidth <= innerWidth`) em 375 px.
  - Renderização de fonte varia por SO: os snapshots são gerados e comparados **sempre na imagem Docker oficial do Playwright** (mesma versão do pacote), localmente e no CI; `maxDiffPixelRatio` pequeno (ex.: 0,01).
  - CI: job `visual` separado (instala o Chromium, roda os testes, publica o relatório como artifact quando falha). Começa **não bloqueante** neste PR e passa a bloquear o deploy depois que a baseline se mostrar estável (o deploy de `main` já espera o CI frontend).
- **Dependências:** Passo 1 (para a baseline já ter `lang`, tema e reduced-motion corretos).
- **Riscos:** flakiness (datas, fontes, rede); manutenção das fixtures quando os DTOs mudarem.
- **Aceite:** `bun run test:visual` verde duas vezes seguidas sem mudanças; baseline commitada; job no CI rodando.

### Passo 3 — Tokens: primitivas + reapontar semânticos (só cor)
- **Escopo:** camada de primitivas, novos valores dos tokens existentes, tokens novos (`brand-*`, `highlight-*`, `chart-3`, `overlay`, `info` indigo), mapeamentos de cor no Tailwind, teste de contraste.
- **Arquivos:** `src/index.css`, `tailwind.config.js`, novos `src/lib/contrast.ts` + `src/lib/contrast.test.ts`.
- **Dependências:** Passo 2.
- **Riscos:** mudança visual no app inteiro; mudança do mapeamento `brand` no Tailwind quebrar `bg-brand`.
- **Validação:** teste de contraste; snapshots do Playwright atualizados e o diff revisado tela a tela no PR.
- **Aceite:** todos os pares da §7.2 passam no teste; nenhum valor cru fora da camada de primitivas em `:root`/`.dark` (exceto overlays de foto e `.landing-*`); build verde.

### Passo 4 — Consumidores da marca e cores de texto
- **Escopo:** variante `brand` do Button (+ alias `gradient`), checkbox, calendar, AuthPage, ChatPanel, NextSessionCard, pílula do HomeBanner, EditProfileModal, `text-brand` → `text-brand-text`, renomear/remapear `.bg-gradient-*`/`.text-gradient-primary`/`.checkbox-gradient`/`.bg-brand-translucent`/`.text-on-brand-muted`.
- **Arquivos:** linhas "marca" da §11.2.
- **Dependências:** Passo 3.
- **Riscos:** sobrar `text-white` sobre a marca.
- **Validação:** `grep -rn "text-white" src` revisado linha a linha — só sobram contextos de foto/avatar; o teste de contraste cobre `brand/brand-foreground`; diff do Playwright.
- **Aceite:** nenhum `text-white` sobre preenchimento da marca; nenhum verde em `index.css` fora dos tokens de success/live e das `.landing-*`.

### Passo 5 — Tipografia
- **Escopo:** `@fontsource-variable/inter` e `@fontsource/barlow-condensed`, imports dos subsets latinos, preload, métricas de fallback, tokens `fontFamily`/`fontSize`, `font-display` nos componentes da linha "Papéis tipográficos" da §11.2.
- **Arquivos:** `package.json`/`bun.lock`, `src/main.tsx` (imports), `index.html`/`vite.config.ts` (preload), `tailwind.config.js`, os componentes de domínio listados.
- **Dependências:** Passo 3 (independe do 4).
- **Riscos:** CLS, truncamento com a largura condensada, tamanho do bundle, suporte a `tnum`.
- **Validação:** Lighthouse CLS < 0,1 em `/home`; o painel de rede mostra só woff2 da própria origem, ≤ 150 KB; checagem de scroll horizontal do Playwright em 375 px.
- **Aceite:** a fonte computada do `body` é Inter; Barlow só onde a §9.1 permite (conferido em "Rendered fonts" do DevTools).

### Passo 6 — Ilhas de cor fixa
- **Escopo:** ComparePlayersDialog + ComparablePlayerStatsCard, KPIs do ProfilePage, gráficos do perfil, MatchHistoryTable, CreatePeladaModal, gradientes/avatares de `lib/utils.ts` → 6 tokens de avatar (Q10).
- **Dependências:** Passo 3.
- **Riscos:** usuários estranharem a nova cor da pelada.
- **Validação:** grep de classes cruas (só permitidos Landing, overlays de foto, logo do Google e cores de time); diff do Playwright nos dois temas.
- **Aceite:** as entradas "Found in" dos antipatterns #3 e #27 no FRONTEND.md removidas para esses arquivos.

### Passo 7 — Highlight (Cantaloupe) (Q5)
- **Escopo:** aplicar `highlight-*` nos pontos definidos na Q5.
- **Dependências:** Passos 3 e 5.
- **Riscos:** confusão com gold/destructive.
- **Aceite:** highlight usado só na lista aprovada; nunca em UI destrutiva ou de erro.

### Passo 8 — Limpeza
- **Escopo:** remover os aliases (variante `gradient` depois do codemod nos 33 arquivos, nomes antigos de classe), `bg-overlay` opcional nos overlays do shadcn, tokens de escala no lugar dos tamanhos arbitrários restantes, Playwright passa a bloquear o deploy, documentação.
- **Dependências:** todos os anteriores.
- **Aceite:** o grep pela variante/classes `gradient` não retorna nada fora das `.landing-*`; a seção "Styling & design system" do FRONTEND.md descreve o novo sistema por completo.

---

## 14. Riscos e mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| Sobrar texto branco sobre azul/laranja (3,22 / 2,68) | Alta | Falha de acessibilidade | Tokens `brand-foreground`/`highlight-foreground`; auditoria por grep no Passo 4; teste de contraste |
| Mudança global de tokens com regressões não vistas | Alta | Bugs visuais | Baseline do Playwright no Passo 2, antes de qualquer cor mudar |
| Flash de tema depois de mover o bootstrap | Média | Glitch visual | Script inline no head; filmstrip |
| Fonte condensada quebrar truncamento/alinhamento em 375 px | Média | Bugs de layout | Barlow só nos papéis listados; checagem de scroll horizontal no Playwright |
| CLS de fonte / crescimento do bundle | Média | Performance | Subset latino, 3 pesos de Barlow, Inter variable, preload de um arquivo, métricas de fallback |
| Barlow sem algarismos tabulares | Desconhecida | Placares desalinhados | Verificar no Passo 5; Inter nas colunas alinhadas |
| Flakiness do Playwright (fontes por SO, datas, rede) | Média | CI instável | Imagem Docker oficial, relógio fixo, rotas mockadas, job não bloqueante no início |
| Fixtures do Playwright desatualizadas quando DTOs mudarem | Média | Manutenção | Fixtures tipadas com os tipos de `src/types/*` (falha no `tsc`) |
| Diff gigante nos 33 arquivos `variant="gradient"` | Média | Carga de revisão | Alias primeiro, codemod no fim (Passo 8) |
| Bordas de input mais pesadas mudarem o visual | Média | Percepção de ruído | Exigido pelo 1.4.11; restrito ao `--input` (divisores continuam leves) |
| Landing (fora do escopo) mudar de forma não planejada pelos tokens globais | Média | Visual inconsistente na landing | Ela só consome tokens em poucos lugares; registrar no PR do Passo 3 o que mudou e avisar a feature da landing |
| Alterações locais não commitadas (finance/MemberCard/tabs-variants) conflitarem com a Phase 2 | Média | Conflitos de merge | Commitar ou guardar as alterações atuais antes do Passo 3 |
| CLI do shadcn sobrescrever `ui/*` customizados | Baixa | Regressão | Regra atual: `--dry-run`, responder "não" |
| Sombras do dark pesadas no claro | Baixa | Acabamento | `--shadow-color` opcional por tema |

---

## 15. Estratégia de testes e validação

1. **Teste automatizado de contraste (Vitest, lógica pura — segue a convenção do projeto):** um helper `contrast(fg, bg)` e um teste que lê `src/index.css` (`:root`, `.dark`), resolve as referências `var(--p-*)` e verifica a lista de pares da §7.2 (`foreground/background`, `card-foreground/card`, `muted-foreground/{card,card-elevated,background}`, `faint-foreground/*`, `brand-foreground/brand`, `brand-text/{background,card,card-elevated}`, `highlight-*`, `info/info-muted`, `input/{card,background}` ≥ 3, `ring/background` ≥ 3, pares de status/badge, `primary-foreground/primary`). Uma regressão quebra o build.
2. **Regressão visual (Playwright, Passo 2):** snapshots por rota × tema × viewport; asserções de `lang` e de ausência de scroll horizontal; cada PR dos passos 3–8 atualiza a baseline e o diff é revisado no PR.
3. **Gates atuais:** `bun run lint && bun run build && bun run test` em todo PR (o CI já exige).
4. **Gates de grep por passo:** classes cruas da paleta, `text-white`, hex em `src/components`, nomes `gradient` (allow-list documentada no PR).
5. **Matriz manual complementar:** 768 px nos dois temas, telas com estados difíceis de mockar e os toasts.
6. **Passada de teclado e reduced-motion** nas mesmas rotas (ordem de Tab, anel visível, Esc, reduce-motion do sistema).
7. **Performance:** Lighthouse (mobile) em `/home` e `/daily/:id`: CLS < 0,1 e nenhuma regressão > 10% no LCP; fontes ≤ 150 KB.

---

## 16. Critérios de aceite

A Phase 2 está concluída quando tudo abaixo puder ser verificado:

1. `src/index.css` define a camada de primitivas e todos os tokens semânticos da §8.4 em `:root` e `.dark`; não restam literais hex/rgb no `index.css` fora das primitivas, dos overlays de foto e das `.landing-*`.
2. O teste de contraste passa para todos os pares da §15.1.
3. O `grep` não encontra classes cruas da paleta em `src/`, exceto: overlays de foto/branco sobre foto (`PeladaBanner`, `ChampionHero`, `HomeBanner`, `MyPeladaCard`), iniciais dos avatares coloridos, logo do Google, cores dos times e a `LandingPage`.
4. Nenhum `text-white` sobre preenchimento de marca ou highlight.
5. A fonte computada do `body` é Inter; `font-display` só nos papéis da §9.1; as fontes vêm da origem do app (`@fontsource`).
6. Reload em dark sem quadro claro; sem preferência salva, o tema segue o sistema operacional (inclusive ao mudar com a aba aberta); o Toaster segue o tema; `<html lang="pt-BR">`.
7. Com `prefers-reduced-motion: reduce`, nenhuma animação infinita roda e nenhum hover faz `scale`.
8. As rotas da suíte do Playwright passam em 375 e 1280 px nos dois temas, sem scroll horizontal; o job `visual` está no CI e bloqueia o deploy.
9. Lint, build e testes verdes; CLS < 0,1 em `/home`.
10. FRONTEND.md atualizado: "Styling & design system", tabela de tokens, comandos (`test:visual`), as entradas "Found in" dos antipatterns e a descrição das pastas (`e2e/`); a nota do handoff sobre a stack de fonte do sistema marcada como substituída.

---

## 17. Decisões aprovadas

Respondidas pelo responsável do produto em 2026-11-01. A SPEC foi aprovada como um todo.

| # | Questão | Decisão | Consequência na SPEC |
|---|---|---|---|
| Q1 | Tema padrão sem escolha salva (hoje: claro) | **Seguir o sistema operacional** | §10 item 1–2: script inline com `prefers-color-scheme` e listener enquanto não houver escolha |
| Q2 | `--primary` vira Electric Blue? | **Não, continua neutro** | §8.4: `--primary` neutro; a marca fica em `--brand` / `variant="brand"` |
| Q3 | Entrega das fontes | **`@fontsource`** | §9.3, Passo 5 |
| Q4 | `--info` com o mesmo matiz da marca | **Decidido: novo matiz indigo** (`#4F46C8` claro, `#A5A6F6` escuro; fundos `#EEEEFC` / `#1E1D3D`) | §7.1, §8.3, §8.4; contraste validado na §7.2. O badge de Mensalista mantém a variante e só muda de cor |
| Q5 | Onde o Cantaloupe aparece / "seria o `--accent`?" | **Decidido: token `--highlight`, não `--accent`.** No shadcn, `--accent` é a cor de hover neutra de `ghost`/`outline`, menus, selects e toggles; se ele virasse laranja, todo hover do app ficaria laranja. Uso aprovado: **momentos de conquista** — `ChampionHero` (selo/rótulo do campeão), `DailyAwardsGrid` (destaque dos premiados da sessão) e o valor de Gols nos KPIs do perfil. Gold continua nas medalhas/estrelas/troféus; verde continua no "ao vivo" e no sucesso; laranja nunca em erro ou ação destrutiva | §7.1, §8.4, §11.2, Passo 7 |
| Q6 | Logo/favicon | Fora do escopo; o wordmark só muda de cor via `--brand-text` | §6 |
| Q7 | Radius | Manter 8/14/18 + botões em pílula | §8.5 |
| Q8 | Regressão visual com Playwright | **Adicionar** | Passo 2 (baseline antes das cores), §15.2, critério 8 |
| Q9 | Texto sobre a marca | **Midnight nos dois temas** (6,24:1) | §7.2, `--brand-foreground` |
| Q10 | Gradientes de pelada/avatar | **Mapear para os 6 tokens de avatar** | §11.2, Passo 6 |
| Q11 | Landing Page | **Fora do escopo** (outra feature) | §6, §11.2, §16 |
| Q12 | Idioma da SPEC | **Português** (público principal) | este documento; identificadores e tokens continuam em inglês |

Nenhuma decisão pendente.

---

### Apêndice A — Arquivos inspecionados

`client/package.json`, `tailwind.config.js`, `components.json`, `index.html`, `vitest.config.ts`, `eslint.config.js`, `.github/workflows/ci-frontend.yml`, `src/index.css`, `src/main.tsx`, `src/hooks/useTheme.ts`, `src/components/ui/{button,badge,tabs,toggle}-variants.ts`, `ui/{card,checkbox,calendar,dialog,alert-dialog,sheet,drawer,sidebar,chart}.tsx`, `src/lib/utils.ts`, os componentes citados nas §2.6, §2.7 e §2.9 (inspeção por grep), `docs/design/design_handoff_futspring_redesign/README.md` e `client/docs/FRONTEND.md`.

### Apêndice B — Working tree no momento do discovery

Havia alterações não commitadas (não feitas por esta tarefa) em `client/docs/FRONTEND.md`, `components/finance/*`, `components/pelada/MemberCard.tsx` e `components/ui/tabs-variants.ts`. A Phase 2 deve começar depois que elas forem commitadas.
