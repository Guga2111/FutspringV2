# Seção de preços da landing page — design

Data: 2026-11-22 · Origem: Oriens #38 "Adicionar seção de preços à landing page" (docs/BACKLOG.md, Launch and business) · Status: aprovado

## Contexto

- Os preços vêm de `SubscriptionPlans.java`: Mensal R$ 19,90 e Anual R$ 199,00. Peladas com **mais de 40 membros** (`LARGE_GROUP_MEMBERS`) pagam R$ 29,90 / R$ 299,00.
- O teste grátis é de 30 dias (`TRIAL_LENGTH`), vale para a primeira pelada da conta (um teste por conta) e aceita até 30 membros (`TRIAL_MEMBER_CAP`). A pelada `PENDING` tem o mesmo limite.
- O pagamento é só por Pix, feito pelo criador da pelada. Os membros não pagam nada ao Futspring.
- Referência visual: shadcn.io/pricing-block. São 108 blocos e o código de todos é Pro. Esta versão é autoral, inspirada na estrutura dos blocos Team Size Slider, Usage Slider e Two Plans. Nenhum código foi copiado.
- O que é publicado: o teste grátis de 30 dias, a faixa acima de 40 membros e a economia do anual. A condição de fundadora **não** aparece na landing.

## Abordagem escolhida: "Quantos jogam?"

A variável do preço é o tamanho da pelada, então a seção deixa o visitante mexer nela.

### Lugar e estrutura

- `PricingSection` nova em `components/landing/`, entre `GallerySection` (`bg-highlight`) e `FaqSection` (`bg-background`), num campo `bg-night` / `text-night-foreground`.
- `id="precos"`, `aria-labelledby="precos-title"`. O menu do hero ganha "Preços" (Como funciona · Sorteio · Preços · Perguntas).
- A pasta `landing/` passaria de 12 para 13 arquivos diretos. Por isso os fragmentos do produto (`SessionFragment`, `PhotoSlot`, `StarCount`) vão para `landing/fragments/`. Só os imports mudam.

### Texto

- Título (`h2`, `font-display text-chapter`, até 2 linhas): "Um preço / por pelada".
- Apoio: "Quem cria a pelada assina. A galera joga sem pagar nada ao Futspring."
- Fatos:
  - "A primeira pelada da sua conta tem 30 dias grátis, com até 30 membros."
  - "Pago por Pix, sem cartão."
- Ação única: "Começar teste grátis" → `/auth?tab=register` (`Button variant="brand"`).

### Parte interativa

- Pergunta: "Quantos jogam na sua pelada?"
- Um shadcn `Slider` (adicionado com `bunx shadcn add slider` e adaptado ao Tailwind 3 e aos tokens) de 10 a 60 membros, começando em 18. O valor máximo aparece como "60+".
  - `aria-label="Membros na pelada"` e `aria-valuetext="18 membros"`.
  - Funciona pelo teclado e pelo toque.
- Fileira de pontos (`aria-hidden`), um por membro: os ativos são `bg-brand` e os outros usam um tom apagado do campo. No celular a fileira quebra em grade.
- Marcas abaixo do trilho: "30 · fim do teste" e "41+ · faixa grande".
- Linha de situação, anunciada por `aria-live="polite"`:
  - até 30: "Cabe no teste grátis."
  - 31 a 40: "Para passar de 30 membros, assine."
  - 41 ou mais: "Pelada grande: preço da faixa acima de 40."
- Dois blocos de plano lado a lado (empilhados abaixo de md), sem ícones. O preço vai em Barlow, tipo placar:
  - **Mensal**: "R$ 19,90" "por mês".
  - **Anual**: "R$ 199" e "≈ R$ 16,58 por mês · R$ 39,80 a menos que 12× o mensal".
  - Acima de 40 membros: R$ 29,90 / R$ 299 (≈ R$ 24,92 · R$ 59,80 a menos).
- A economia usa o realce (`text-highlight`).

### Fonte dos preços

- `utils/subscription.ts` espelha o backend, cada constante com um comentário que aponta para a origem (como `GRACE_DAYS`):
  - `PLAN_PRICES`
  - `LARGE_GROUP_MEMBERS = 40`
  - `TRIAL_DAYS = 30`
  - `TRIAL_MEMBER_CAP = 30`
- `landingPricing(members)` é uma função pura que faz tudo em centavos inteiros, sem número decimal: preço da faixa, anual ÷ 12 arredondado e a economia (12× o mensal − o anual). A formatação usa `formatFinanceMoney`.
- Testes no Vitest para as bordas 30/31 e 40/41.
- Contrapartida aceita: mudar um preço exige mudar `SubscriptionPlans` e o espelho. O BACKEND.md registra isso junto a `SubscriptionPlans`. Não há endpoint público de preços.

### Movimento, tema e acessibilidade

- O `useReveal` / `revealAt` segue o escalonamento das outras seções. A troca de preço é feita só com opacidade e respeita `prefers-reduced-motion`.
- Só tokens semânticos. Se surgir um par novo de texto e fundo, ele entra no `contrast.test.ts`.
- Funciona em 375 px, sem rolagem horizontal, nos temas claro e escuro.

## Validação

- `bun run lint && bun run build && bun run test` em `client/`.
- `/playwright-cli` em 375 e 1440, claro e escuro: arrastar o Slider pelas bordas (10, 30, 31, 40, 41, 60), usar o teclado, conferir que não há rolagem horizontal e que o link do menu leva à seção.
- `bun run test:visual:update` (Docker) para a landing, revisando os PNGs.

## Documentação

- FRONTEND.md: a rota `/` passa a citar a seção de preços, a pasta `landing/fragments/`, o `slider` na lista de componentes instalados e as novas constantes de `utils/subscription.ts`.
- BACKEND.md: em Subscriptions, uma nota dizendo que os preços são espelhados em `client/src/utils/subscription.ts`.
