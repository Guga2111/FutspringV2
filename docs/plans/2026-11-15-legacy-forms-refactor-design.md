# Refatoração dos formulários legados — design

Data: 2026-11-15 · Status: aprovado

## Objetivo

Migrar os três formulários que ainda usam `useState` por campo para **react-hook-form + zod + shadcn `Field`**, conforme a seção [Forms](../../client/docs/forms.md#forms-mandatory) do client/docs/README.md, e tirar a chamada a `src/api` de dentro dos componentes. Fecha os antipatterns 5 e 8 nesses arquivos:

- `client/src/components/CreatePeladaModal.tsx` (Sheet, 259 linhas)
- `client/src/components/daily/live/FinalizeModal.tsx` (Dialog, 119 linhas)
- `client/src/components/daily/ImportFromMessageModal.tsx` (Dialog com 2 etapas, 290 linhas)

Nenhum contrato de API muda: os schemas espelham os DTOs que já existem (`CreatePeladaRequestDTO`, `FinalizeDailyRequestDTO`, `PopulateDailyRequestDTO`). Não há mudança no backend.

## Escopo

- Refatoração para o padrão de Forms.
- **Correção de bug:** em `CreatePeladaModal`, quando a pelada é criada mas o upload da foto falha, a tela mostra "Não foi possível criar a pelada". O usuário tenta de novo e cria uma pelada duplicada.
- Textos pt-BR corrigidos ("Dia da semana", "Número de times", "Referência (opcional)") e erros mostrados por campo. O layout continua o mesmo.
- Fora do escopo: `TimeZonePicker` na criação, limite de mensalistas na criação, redesign dos diálogos.

## Decisões comuns aos três

- O form mora num hook da feature (`components/<feature>/hooks/`), e o componente só renderiza. A chamada a `src/api` sai do componente.
- Submit com `<form onSubmit={form.handleSubmit(...)} noValidate>` e `Button type="submit" disabled={formState.isSubmitting}`, que substitui o `loading` em `useState`. Os diálogos continuam sem fechar durante o envio (`!isSubmitting` no `onOpenChange`).
- Erros do servidor via `applyServerErrors(form, error, fallback)`: os erros de campo vão para os campos e o resto para o `root`, num `Alert variant="destructive"` (no lugar do `toast.error`). O `toast.success` continua.
- Schemas em `client/src/schemas/<domínio>.ts`, com um comentário dizendo qual DTO espelham. Mensagens em pt-BR. Testes Vitest ao lado (`*.test.ts`) para schemas e conversões puras.
- Cada PR acrescenta um teste visual do diálogo aberto em `e2e/visual.spec.ts`, nos quatro projetos, com as PNGs geradas no Docker.
- Cada PR atualiza o `client/docs/README.md`: a lista "Still on useState" da seção Forms, a coluna "Found in" dos antipatterns 5 e 8, a árvore de pastas e a lista de hooks.

## PR 1 — FinalizeModal

- `finalizeDailySchema` em `schemas/daily.ts`, espelhando `FinalizeDailyRequestDTO`: `{ puskasWinnerIds: number[], wiltballWinnerIds: number[] }` (ids inteiros positivos; listas vazias permitidas, porque o backend aceita sessão sem premiados). Só aparecem como opção os jogadores dos times da sessão. O backend continua validando isso.
- `useFinalizeForm(daily, onSuccess)` em `components/daily/hooks/`: `useForm` + submit (`finalizeDaily`).
- `PlayerCheckboxList` passa a receber `value`/`onChange` de um `Controller` sobre o array.
- `usePlayerSelection` não tem outro uso e é apagado. O client/docs/README.md dizia que o modal de resultados também o usava, o que não é verdade: a frase é corrigida.

## PR 2 — CreatePeladaModal → `components/pelada/CreatePeladaSheet.tsx`

- O componente vai para a feature `pelada` porque o hook precisa de uma pasta `hooks/`. A raiz de `components/` é para componentes compartilhados. Vira export nomeado `CreatePeladaSheet`. `AppLayout` e o pattern 5 da doc passam a apontar para ele.
- `createPeladaSchema` em `schemas/pelada.ts`, espelhando `CreatePeladaRequestDTO` e montado a partir dos schemas existentes:
  - `peladaInfoSchema` (nome)
  - `peladaScheduleSchema` sem `timeZone` (dia, horário, duração)
  - `peladaLocationSchema` (endereço, referência)
  - os campos `numberOfTeams` / `playersPerTeam` passam a ser um objeto compartilhado com `peladaSlotsSchema`, porque no zod 4 `.extend` não funciona num schema com `superRefine`
  - `autoCreateDailyEnabled: boolean`
- O `timeZone` continua vindo de `Intl.DateTimeFormat().resolvedOptions().timeZone` na hora do envio, fora do schema.
- A imagem fica **fora do form**, como no `InfoCard`: validada com `validateImageFile` (`schemas/upload.ts`), com o erro embaixo do campo. Isso substitui a validação de tamanho e tipo copiada à mão.
- `useCreatePeladaForm(onCreated)` em `components/pelada/hooks/`:
  1. `createPelada`. Se falhar, o erro vai para o form (campos ou `root`).
  2. Se houver imagem, `uploadPeladaImage`. **Se falhar, a pelada já existe:** mostra `toast.warning("Pelada criada, mas a foto não foi enviada. Envie de novo nas configurações.")` e segue o fluxo normal, sem nunca mostrar o erro de criação.
  3. `onCreated()` e navega para `/pelada/:id`.
- Campos numéricos com `field.onChange(e.target.valueAsNumber)`, como em `ScheduleCard` / `SlotsCard`.

## PR 3 — ImportFromMessageModal (dois forms no mesmo hook)

Opções consideradas: um único `useForm` para as duas etapas, dois forms, ou manter o `useState` e só extrair o hook. Escolhemos **dois forms**: cada etapa tem um schema próprio e testável, a etapa 2 nasce com `defaultValues` vindos do parse, e "Voltar" mantém o texto colado.

- Em `schemas/daily.ts`:
  - `importMessageSchema`: `{ message }` com trim e mínimo 1. Se o parse (`parseSessionMessage`) não achar nenhum time, o hook faz `setError("message", "Nenhum time encontrado. Verifique o formato da mensagem.")`.
  - `importResolutionSchema`: `{ teams: [{ players: [{ rawName, totalGoals, totalAssists, matchedUserId: number | null, skipped: boolean }] }] }`. Cada jogador precisa ter `matchedUserId` ou estar `skipped`, e o mesmo `userId` não pode aparecer duas vezes. Hoje a tela só esconde da lista quem já foi escolhido; agora o schema garante isso.
  - `toPopulateInput(parsed, values)`: função pura, espelhando `PopulateDailyRequestDTO` (cor e hex dos times do parse, jogadores vinculados e não ignorados, partidas do parse), com testes.
- `useImportFromMessage(daily, onSuccess)` em `components/daily/hooks/` controla a etapa (1 | 2), os dois forms, o resultado do parse (times e partidas, que não são editáveis) e o submit (`populateFromMessage`).
- O componente só renderiza. As linhas da etapa 2 usam `Controller` por jogador. O `Select`, "Ignorar jogador" e "Desfazer" continuam como estão.

## Ordem e branches

`feat/legacy-forms` sai de `dev`. Os sub-PRs vão para ela e, no fim, ela vai para `dev`. A ordem é Finalize → CreatePelada → Import, do menor para o maior.

## Verificação

`bun run lint && bun run build && bun run test` em `client/` a cada PR, mais `bun run test:visual:docker` para o novo teste visual. Os três diálogos mudam de visual só pelo `Alert` de erro e pelos textos corrigidos. Não há mudança no backend.
