# SPEC — Melhorias de UX do Financeiro (seleção, busca, histórico, consistência)


## Contexto

O financeiro (`/pelada/:id/financeiro`, `components/finance/`) funciona, mas fica trabalhoso para quem administra:

- Numa cobrança, só dá para receber de uma pessoa por vez ou de **todos** os visíveis ("Receber todos").
- O seletor de pessoas tem busca, mas ela quase não aparece. "Trocar participante" e "Responsáveis financeiros" não têm busca nenhuma.
- O histórico de alterações fica escondido atrás de um card em Configurações, sem filtro, e mostra JSON cru.

A auditoria (feita com brainstorm + taste/app-screens) também encontrou termos inconsistentes ("Lista" × "Cobrança", "Diária" × "Sessão", "Pago" × "Paga"), status sem cor, datas no fuso do navegador e código de busca copiado em 3 lugares.

Decisões do usuário:

- O lote se limita a **Receber selecionados**.
- A busca existente será **destacada**, e também entra no Trocar participante.
- Os quatro extras entram: textos/status consistentes, histórico legível, filtro de datas no extrato e "← Cobranças" visível.

A pergunta principal de cada tela continua sendo "quem ainda deve e o que faço agora". As mudanças reduzem cliques nesse trabalho. Nenhuma regra de dinheiro muda.

## Escopo

| # | Item | Lado |
|---|------|------|
| 1 | Seleção múltipla + "Receber selecionados" | frontend (endpoint existente) |
| 2 | Busca destacada no seletor de pessoas + busca em Trocar participante | frontend |
| 3 | Busca em Responsáveis financeiros | frontend |
| 4 | Aba própria **Histórico** com filtro de intervalo de datas (dois calendários) e por tipo | backend + frontend |
| 5 | Filtro de datas no extrato do Caixa | backend + frontend |
| 6 | Histórico legível (o que mudou, em quem) | frontend |
| 7 | Textos e status consistentes | frontend |
| 8 | "← Cobranças" no topo do detalhe | frontend |
| 9 | **Ajustar valor em lote** (ex.: mensalistas pagam só o juiz) | backend + frontend |

Fora de escopo: cancelar em lote, mudanças nas regras de pagamento e novos DTOs de resposta (o §9 só acrescenta um DTO de requisição).

---

## 1. Seleção múltipla numa cobrança

**Fato:** `POST /lists/{listId}/receive-all` (`FinanceLedgerService.receiveAll`) já recebe qualquer `chargeIds` (1–200, únicos, tudo ou nada, uma revisão e uma auditoria). **Sem mudança no backend.**

**Comportamento** (gestores, cobrança `OPEN`):

- O botão "Receber todos (N)" ao lado da busca vira **"Selecionar"** (outline). Ao entrar no modo:
  - cada linha mostra um `Checkbox` e a linha inteira marca/desmarca;
  - "Detalhes", "Receber" e "Conferir" das linhas ficam ocultos, como nos cards de Membros.
- **Só é selecionável** quem pode receber agora: não cancelado, não pago, `outstanding > 0`, sem aviso pendente.
  - As outras linhas aparecem com o checkbox desabilitado e um hint curto ("Confira o pagamento antes", "Já pago").
  - A regra é a mesma de `planReceiveAll`, que vira um predicado `canReceiveCharge(charge)`.
- A seleção sobrevive a busca, filtros (chips) e troca de fila.
  - `selected` é derivado das cobranças atuais, então quem foi pago ou cancelado por outro gestor sai sozinho.
- **Barra fixa** no rodapé, com 1+ selecionado, no mesmo modelo de `MemberSelectionBar`:
  - texto "4 selecionados · R$ 180,00" (+ "· 1 fora do filtro" quando houver);
  - "Selecionar todos a receber (N)" / "Desmarcar todos" (sobre as linhas visíveis);
  - **"Receber"** (brand) e ✕.
  - Esc ou ✕ sai do modo. O chat já fica oculto nesta página.
- "Receber" abre o `FinanceReceiveAllDialog` com as cobranças selecionadas (título "Receber 4 pagamentos · R$ 180,00", até 5 nomes, "Hoje · Pix" com Alterar).
  - Sucesso: sai do modo e mostra o toast.
  - Erro: a seleção continua e o erro aparece no root do diálogo.
- O caminho rápido de antes continua: "Selecionar" → "Selecionar todos a receber" → "Receber".

**Código:**

- Generalizar `components/pelada/hooks/useMemberSelection.ts` → `src/hooks/useSelection.ts` (`useSelection<T extends { id: number }>(items, visible)`, mesma API: `active`, `selectedIds`, `selected`, `outsideFilter`, `allVisibleSelected`, `enter/exit/toggle/toggleVisible`, Esc).
  - Usado por Membros e pelo financeiro, então vai para `src/hooks/`.
  - Atualizar o import em `MembersGrid`.
  - Aceitar um `selectable?: (item) => boolean` opcional para `toggleVisible`.
- `utils/finance.ts`:
  - `canReceiveCharge(charge)`;
  - `planReceiveAll(rows)` passa a receber as cobranças selecionadas (o retorno continua `{charges, total, review}`).
  - Testes no `finance.test.ts`.
- Extrair a casca da `MemberSelectionBar` (barra fixa no rodapé, `bg-card shadow-panel`, contador, ✕) para `components/SelectionBar.tsx`, que recebe as ações como `children`. `MemberSelectionBar` passa a usá-la (snapshot de Membros inalterado) e o financeiro também. Não duplicar o markup.
- `FinanceListDetail.tsx`: modo de seleção, checkbox nas linhas, a barra e o diálogo com `selected`.

## 2. Busca de pessoas

**Seletor (`FinancePeoplePicker`)**, usado em Nova cobrança e Adicionar pessoas:

- Ordem nova: **busca** primeiro (`Input` com `Search01Icon` à esquerda, igual à busca de participantes da cobrança), depois os chips Todos/Mensalistas/Diaristas e a lista.
- "Usar participantes de uma sessão" desce para depois da lista, antes dos Convidados.
- Contador junto à legenda: "3 selecionados · mostrando 5 de 24".
- Lista mais alta (`max-h-80`), cada linha com `PlayerAvatar` (hoje não tem; o card de Responsáveis tem).
- Vazio da busca: "Nenhum jogador encontrado para "ped"".

**Componente compartilhado:** `components/finance/FinanceMemberChecklist.tsx` — busca + contador + "Selecionar/Desmarcar visíveis" + lista de `Checkbox` com avatar. Props:

- `members`, `selected`, `onChange`, `idPrefix`;
- `filters?` (os chips de tipo, só no seletor);
- `describe?` (rótulo à direita: "Mensalista"/"Diarista").

Usado pelo seletor e pelo card de Responsáveis (§3).

**Trocar participante (`FinanceReplacementForm`):**

- Trocar o `Select` por um combobox `Popover` + `Command` com busca (padrão de `TimeZonePicker`): avatar + nome, "Nenhum membro encontrado".
- **Excluir quem já participa da cobrança** (cobranças ativas da lista), não só a pessoa trocada. O backend já recusa duplicidade (uma cobrança ativa por pessoa), então a UI deixa de oferecer uma opção que falharia.
- Passar `excluded` a partir de `detail.charges`.

**Busca normalizada única:**

- `normalizeSearch(value)` em `lib/utils.ts`, sem acento, `pt-BR`, com trim.
- Substitui as cópias de `utils/finance.ts`, `FinancePeoplePicker` e `utils/attendanceRanking.ts`.
- Teste em `lib/utils.test.ts` (ou o arquivo de teste existente). Corrige o antipadrão #7.

## 3. Responsáveis financeiros com busca

- `ManagersCard` (`FinanceSettings.tsx`):
  - mantém o resumo "2 responsáveis: Ana, Pedro" e o `Collapsible`;
  - por dentro, usa `FinanceMemberChecklist` (busca + contador), em vez da lista sem filtro.
- Os responsáveis já escolhidos aparecem primeiro na lista, para facilitar desmarcar.
- O formulário continua react-hook-form + `financeManagersSchema` via `Controller`.

## 4. Aba **Histórico** com filtros

**Navegação:**

- Nova aba `Histórico` (só gestores, `value="history"`) depois de Configurações, no `TabsList variant="underline"` (a linha já rola de lado em 375 px).
- Removidos de `FinanceTab`/`FinanceSettings`:
  - o card "Histórico de alterações";
  - o `onAudit`;
  - o estado `settingsView`.
- Tour: o passo `settings` deixa de citar o histórico (não cita hoje). Adicionar um passo `history-tab` só se couber sem alongar demais; senão, nada muda (decisão: **não adicionar**).

**Filtros** (linha acima da lista):

- **Período:** botão `Calendar03Icon` + rótulo ("Todo o período", "01/10 – 31/10/2026") → `Popover` com `Calendar mode="range"`.
  - `numberOfMonths={isMobile ? 1 : 2}` (os "dois calendários" no desktop) + "Limpar" / "Pronto".
  - Igual ao da Galeria, extraído em componente (abaixo).
- **Tipo:** chips `Todos · Cobranças · Pagamentos · Caixa · Configurações`, `Record<FinanceAuditGroup, FinanceAuditOperation[]>` em `types/finance.ts`:
  - Cobranças: `LIST_SAVE, PUBLISH, CLOSE, REOPEN, CANCEL, DUPLICATE, PARTICIPANTS, CHARGE_SAVE, CHARGE_REPLACE, CHARGE_CANCEL`;
  - Pagamentos: `RECEIVE, REPORT, CONFIRM, REJECT, REFUND`;
  - Caixa: `CASH, REVERSE`;
  - Configurações: `SETTINGS, MANAGERS, MANAGERS_REVOKED, TEMPLATE, QR, DAILY_DETACHED`.
- Mudar um filtro volta para a página 1. O total do `ListPagination` é o filtrado.
- Vazio filtrado: "Nenhuma alteração neste período" + "Limpar filtros".

**Componente de período compartilhado:**

- `components/DateRangeFilter.tsx` (Popover + Calendar range + Limpar/Pronto; props `value`, `onChange`, `defaultMonth?`).
- `DateRange` (de `GalleryRange`) e `formatRangeLabel` vão para `utils/dates.ts`.
- `SessionGallery` passa a usá-lo (mesmo visual, snapshot inalterado).
- `sessionGallery.ts` mantém `inRange`, importando o tipo.

**Backend** — `GET /api/v1/peladas/{id}/finance/audit?page&size&from&to&operations`:

- `from`, `to`: `LocalDate` opcionais (ISO), inclusivos, interpretados **no fuso da pelada** (`pelada.timeZone`):
  - `createdAt >= from 00:00` e `< (to + 1 dia) 00:00`.
- `from > to` → 400 "Período inválido".
- `operations`: lista opcional de `AuditOperation` repetida (`?operations=RECEIVE&operations=REPORT`); valor desconhecido → 400 (handler existente de `MethodArgumentTypeMismatchException`); vazio = todas.
- `FinanceReadService.audit(...)`:
  - o serviço resolve limites concretos (sem parâmetros nulos no JPQL: sem `from`, usa `Instant.EPOCH`; sem `to`, usa `access.now()` + 1 dia (nenhum registro de auditoria tem data futura); sem `operations`, `EnumSet.allOf`);
  - chama um `FinanceAuditRepository.findByPeladaIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndOperationInOrderByIdDesc` (ou `@Query` em text block equivalente).
- Permissão inalterada (`ctx.requireManager()`).
- Migração **V14** `V14__finance_history_filters.sql` (não destrutiva):
  - `CREATE INDEX ix_finance_audit_pelada_created ON finance_audit(pelada_id, created_at DESC)`;
  - `CREATE INDEX ix_finance_movements_pelada_effective ON finance_movements(pelada_id, effective_date DESC)` (§5).

**Frontend API:**

- `getFinanceAudit(id, page, filter: { from?: string; to?: string; operations?: FinanceAuditOperation[] })`;
- `paramsSerializer: { indexes: null }` para repetir `operations`;
- `useFinanceHistory` ganha `filter` na chave (`key` inclui o filtro serializado) e volta para a página 0 quando o filtro muda.

## 5. Filtro de datas no extrato do Caixa

- `GET /movements?page&size&from&to`: filtra por `effectiveDate` (a data em que o dinheiro entrou/saiu), inclusive, mesma validação (`from > to` → 400).
  - `FinanceMovementRepository.findByPeladaIdAndEffectiveDateBetweenOrderByIdDesc`, com limites resolvidos no serviço (sem `from`: `LocalDate.of(1970, 1, 1)`; sem `to`: hoje no fuso da pelada + 1 dia, porque `effectiveDate` nunca é futura).
  - A visibilidade do caixa (`viewCash`) não muda.
- UI: `DateRangeFilter` ao lado do título "Extrato do caixa".
- O **saldo** no topo continua sendo o saldo total (não filtrado). Abaixo do filtro aparece uma linha "12 lançamentos no período".

## 6. Histórico legível

O backend já guarda `beforeData`/`afterData` como JSON das views (`FinanceViews.Detail`, `Settings`, `Template`, `Movement`) e `target` (`LIST:12`, `CHARGE:12:34`, `TEMPLATE:3`). **Sem mudança no backend.**

- Util puro `utils/financeAudit.ts`:
  - `auditSubject(entry)`: "Mensalidade de novembro", "Ana · Mensalidade de novembro", "Pix", "Modelo Diária", a partir do JSON após/antes e do `target`.
  - `auditChanges(entry, members)`: linhas legíveis, cada uma `{ label, before?, after? }`. JSON inválido ou forma desconhecida → `[]`.
    - Cobrança (`collection`): nome, vencimento, valor por pessoa (soma dos componentes), status, Pix, observações.
    - Participantes (`charges` por id): "+ Pedro", "− Ana (cancelada)", "Ana: Em aberto → Paga", "Ana: valor R$ 40,00 → R$ 45,00".
    - Configurações: visibilidade, chave Pix, quem recebe, QR trocado.
    - Responsáveis: nomes adicionados/removidos, via `members`.
    - Movimento: tipo, valor, data.
  - Valores com `formatFinanceMoney` / `formatShortDate`.
- Testes `financeAudit.test.ts`: uma forma por operação + JSON inválido.
- Item do histórico (`FinanceHistory` kind audit):
  - "**Ana** recebeu pagamento · Mensalidade de novembro";
  - data/hora no fuso da pelada (`formatInZone`, recebendo `timeZone`);
  - motivo em destaque quando houver;
  - até 4 linhas de mudança e "e mais N";
  - `Collapsible` "Ver dados técnicos" (o JSON atual) mantido só como recurso secundário.
- `timeZone` chega ao `FinanceTab` pelo workspace (`pelada.timeZone`). Hoje ele recebe `peladaId, userId, members, dailies`; adicionar `timeZone`.

## 7. Textos e status consistentes

`types/finance.ts`, como fonte única:

- **`financeAuditLabel`**:
  - `LIST_SAVE` "Cobrança salva", `PUBLISH` "Cobrança publicada", `CLOSE` "Cobrança encerrada", `REOPEN` "Cobrança reaberta", `CANCEL` "Cobrança cancelada", `DUPLICATE` "Cobrança duplicada";
  - `CHARGE_SAVE` "Valor de participante ajustado", `CHARGE_CANCEL` "Participante cancelado", `CHARGE_REPLACE` "Participante trocado";
  - `DAILY_DETACHED` "Sessão desvinculada".
- **Status de pagamento:**
  - Um `financeChargeStatus(charge)`: `CANCELED | PAID | PARTIAL | UNPAID`.
  - Um `Record` de rótulo: "Cancelada", "Paga", "Parcial", "Em aberto". A linha (hoje "Aguardando pagamento"/"Pago"/"Pagamento parcial") e o badge do detalhe usam o mesmo mapa.
  - Indicadores separados continuam como texto: "Pagamento para conferir", "Vencida", "Convidado".
- **Estilos de status:**
  - `financeListStatusStyle: Record<FinanceListStatus, string>`: Rascunho outline neutro, Aberta `bg-brand-muted text-brand-text`, Encerrada `secondary`, Cancelada `bg-destructive-muted text-destructive`.
  - `financeChargeStatusStyle`: Paga `success` soft/strong, Parcial `warning`, Em aberto neutro, Cancelada muted.
  - Vencida continua `destructive`.
  - Aplicados na lista de cobranças, no cabeçalho do detalhe, nas linhas e no `FinanceChargeDetails`, no padrão de `dailyStatusStyle`. Todos os tokens já existem em light/dark; conferir os pares em `lib/contrast.test.ts` se algum par de texto for novo.
- **Cancelar sem ambiguidade:**
  - No menu da cobrança: "Cancelar cobrança inteira" (diálogo "Cancelar a cobrança inteira").
  - No detalhe da pessoa: "Cancelar cobrança de Ana" (diálogo "Cancelar a cobrança de Ana").
- **Extrato:**
  - "Diária: …" → "Sessão …".
  - "Corrige lançamento #123" → "Anula: {descrição do original}" quando o original está na mesma página; senão, "Anula um lançamento anterior".
- **Datas e horas com fuso:** `new Date(...).toLocaleString("pt-BR")` em `FinanceHistory`, `FinanceChargeDetails` e `FinanceNotices` → `formatInZone(..., timeZone)`.

## 8. "← Cobranças" no detalhe

- No topo do `FinanceListDetail`: `Button variant="ghost"` com `ArrowLeft02Icon` "Cobranças" (mesmo padrão do antigo "Voltar às configurações"), chamando `finance.select(null)`.
- A seta dentro do `TabsTrigger` sai.

## 9. Ajustar valor em lote

**Problema:** cada participante já tem a própria composição (`FinanceCharge.components` + `adjustment`), editável em Detalhes → "Ajustar valor ou vencimento" (`FinanceChargeForm`). Isso resolve o caso "mensalistas pagam só o juiz" (cobrança "Diária + Juiz" de R$ 26,50, em que o mensalista já pagou o campo de R$ 21,50 na mensalidade e deve só R$ 5,00), mas é uma pessoa por vez. Com 15 mensalistas, são 15 ajustes e 15 motivos.

Receber um parcial não resolve: a cobrança ficaria com R$ 21,50 em aberto para sempre.

**Comportamento** (gestores, cobrança `DRAFT` ou `OPEN`), em cima do modo de seleção do §1:

- **Seleção mais ampla.** O §1 só deixa selecionar quem pode receber. Aqui passa a ser selecionável quem pode ser **ajustado ou recebido**: não cancelado e sem aviso pendente (`canAdjustCharge(charge)`; quem tem aviso pendente continua desabilitado com "Confira o pagamento antes"). Pagos podem ser selecionados para ajuste.
- **Cada ação conta só quem ela muda**, como em Membros (`planBulkActions`):
  - "Receber (3)" recebe só os selecionados com `canReceiveCharge`. Fica desabilitado com um hint quando nenhum pode receber, e o diálogo diz quantos ficaram de fora;
  - **"Ajustar valor (8)"** (outline) ajusta todos os selecionados.
  - O plano fica num helper puro `planBulkFinanceActions(selected)` em `utils/finance.ts`, com testes.
- **Atalho "Selecionar mensalistas (N)"** na barra (e no menu "Ações ▾" abaixo de md). Ele marca os participantes ativos que hoje são mensalistas da pelada (`members[].isMensalista` do workspace, cruzado por `userId`; convidados nunca entram). É o caso de uso principal, então não depende de buscar nome por nome.
- **"Ajustar valor"** abre o Sheet `FinanceBulkChargeForm` ("Ajustar valor de 8 participantes"):
  - lista de componentes (`useFieldArray`) **pré-preenchida com os componentes da cobrança** (os padrões da lista). Remover "Diária" e deixar "Juiz · R$ 5,00" é o caminho óbvio;
  - campo "Ajuste (R$; negativo para abatimento)", começando em 0,00;
  - resumo ao vivo: "Cada um passa a dever R$ 5,00 · total R$ 40,00";
  - aviso quando algum selecionado já tem composição diferente da lista: "2 tinham um valor próprio e passam a ter este";
  - motivo obrigatório depois da publicação (o mesmo `reason` do `command`), opcional no rascunho;
  - "Ajustar 8 participantes" (brand).
  - **O vencimento não entra:** cada cobrança mantém o seu.
  - Sucesso: sai do modo e mostra o toast "Valor de 8 participantes ajustado". Erro: a seleção continua e o erro fica no root do Sheet.
- Tudo ou nada. Se alguém já recebeu mais do que o novo valor, ninguém muda e a mensagem diz quem foi: "Ana já pagou R$ 26,50, acima do novo valor". Nesse caso a saída é estornar ou reembolsar a pessoa antes.

**Backend:**

- `POST /peladas/{peladaId}/finance/lists/{listId}/charges/adjust`, com body `FinanceRequests.BulkAdjust {command, chargeIds: [id] (1–200, únicos), components: [{name, amount}] (1–20, as mesmas regras de `Charge`), adjustment}`. Responde `FinanceViews.Result`.
- `FinanceCollectionService.adjustCharges`, com as mesmas regras de `charge(...)` para cada cobrança, numa transação, uma revisão e **uma** entrada de auditoria:
  - trava a pelada (`tx.execute`), só gestor;
  - lista não cancelada, motivo obrigatório depois da publicação;
  - cada `chargeId` da lista da rota (senão 404, IDOR), ativo (`active`) e sem aviso pendente (`noPending`; senão 409);
  - componentes não negativos, total ≥ 0 e ≥ `reads.paid(c)` para cada um (senão 409 com o nome);
  - substitui `components` e `adjustment`, recalcula `amountDue` e não toca `dueDate` nem a identidade.
- Auditoria: operação **`CHARGE_SAVE`** com alvo `LIST:{listId}` (o mesmo padrão do `receive-all`, que reusa `RECEIVE`), com antes/depois da lista inteira. **Sem migração:** `CHARGE_SAVE` já está no CHECK do V12. Idempotência e conflito de revisão vêm do `FinanceTransactions`, como nos outros comandos.
- Testes em `FinanceServiceTest`:
  - caminho feliz (3 cobranças, uma revisão, uma auditoria);
  - não gestor 403;
  - cobrança de outra lista 404;
  - cancelada ou com aviso pendente 409, sem mudar ninguém;
  - recebido acima do novo valor 409, sem mudar ninguém;
  - sem motivo numa lista publicada 400;
  - `chargeIds` vazio, repetido ou com mais de 200 400;
  - repetição com a mesma `requestKey` não executa de novo.
- Em `FinanceConcurrencyTest` (Postgres), o ajuste em lote concorrendo com um `receive` da mesma cobrança: um dos dois ganha e o outro recebe 409.
- Snapshot de contrato do novo request (`contract/requests/`).

**Frontend:**

- `api/finance.ts`: `adjustFinanceCharges(peladaId, listId, command, {chargeIds, components, adjustment})`.
- `schemas/finance.ts`: `financeBulkAdjustSchema`, espelhando o DTO e reaproveitando o schema de componentes de `financeChargeFormSchema`.
- `components/finance/FinanceBulkChargeForm.tsx`, montado pelo `FinanceTab` como os outros editores, com `finance.mutate` (revisão capturada na abertura e mesma chave nas retentativas).
- `FinanceListDetail`: as ações novas na barra de seleção e o atalho de mensalistas.
- `utils/financeAudit.ts`: um `CHARGE_SAVE` com alvo `LIST:` vira "Valor de N participantes ajustado".

---

## Arquivos

**Backend** (`core/src/main/java/com/futspring/backend/domain/finance/`):

- `FinanceController` (params `from`, `to`, `operations` em `/audit`; `from`, `to` em `/movements`)
- `FinanceReadService` (`audit`, `movements`: limites no fuso da pelada, validação)
- `FinanceAuditRepository`, `FinanceMovementRepository`
- `db/migration/V14__finance_history_filters.sql`
- Testes: `FinanceServiceTest`
  - filtros nos limites do dia em `America/Sao_Paulo` e em outro fuso;
  - `from > to` 400;
  - operação desconhecida 400;
  - não gestor 403 no audit;
  - outro pelada isolada.
- `FlywayMigrationTest` cobre o V14.

**Frontend** (`client/src/`):

- `components/finance/`:
  - `FinanceTab` (aba Histórico, `timeZone`, sem `settingsView`)
  - `FinanceListDetail` (seleção, voltar, status)
  - `components/SelectionBar.tsx` (casca compartilhada com `MemberSelectionBar`)
  - `FinanceReceiveAllDialog` (recebe a seleção)
  - `FinancePeoplePicker`, novo `FinanceMemberChecklist`
  - `FinanceReplacementForm` (combobox)
  - `FinanceSettings` (Responsáveis com busca, sem card de histórico)
  - `FinanceHistory` (filtros, item legível)
  - `FinanceChargeDetails`, `FinanceNotices` (fuso, rótulos)
  - `hooks/useFinanceHistory` (filtro)
- `hooks/useSelection.ts` (de `useMemberSelection`)
- `components/DateRangeFilter.tsx`
- `components/pelada/SessionGallery.tsx` (usa o filtro extraído)
- `api/finance.ts`, `types/finance.ts`
- `utils/finance.ts` (+ `canReceiveCharge`), `utils/financeAudit.ts` (novo), `utils/dates.ts` (`DateRange`, `formatRangeLabel`)
- `lib/utils.ts` (`normalizeSearch`), `utils/attendanceRanking.ts`
- PR 7 (§9): `FinanceBulkChargeForm` (novo), `FinanceListDetail` (ações e atalho), `FinanceTab`, `api/finance.ts`, `schemas/finance.ts`, `utils/finance.ts` (`canAdjustCharge`, `planBulkFinanceActions`), `utils/financeAudit.ts`; no backend, `FinanceController`, `FinanceCollectionService.adjustCharges`, `FinanceRequests.BulkAdjust`, `FinanceServiceTest`, `FinanceConcurrencyTest` e o snapshot de contrato.
- Testes: `finance.test.ts`, `financeAudit.test.ts`, `dates`/`sessionGallery` tests, `utils` test.
- `e2e/`:
  - fixtures/mock para `/audit` e `/movements` com query;
  - novo shot da aba Histórico (com o popover de período aberto no desktop) e do modo de seleção com a barra;
  - `bun run test:visual:update` e revisar os PNGs.

**Docs** (cada parte no PR que a muda; ver a tabela da ordem):

- `core/docs/BACKEND.md`:
  - tabela Finance REST (`/audit`, `/movements` com filtros);
  - V14 na tabela de migrações.
- `client/docs/FRONTEND.md`:
  - seção Financeiro (aba Histórico, seleção, busca, Responsáveis, status);
  - árvore de pastas (`useSelection`, `DateRangeFilter`, `financeAudit`);
  - hooks (`useSelection` no lugar de `useMemberSelection`);
  - rota `/pelada/:id/sessoes` (o componente extraído).

## Ordem de implementação (PRs pequenos, cada um verde)

Cada PR é feito numa conversa nova: **leia esta SPEC, o `CLAUDE.md` e as duas docs, e implemente só o PR N**. Um branch por PR, criado a partir de `dev` **depois** do merge do PR anterior, porque cada um depende do que veio antes. Regras para todo PR:

- **Docs no mesmo PR.** O `CLAUDE.md` exige que `FRONTEND.md`/`BACKEND.md` acompanhem a mudança, então nenhum PR deixa a doc para depois.
- **Snapshots no mesmo PR.** `e2e/visual.spec.ts` fotografa `pelada-finance`, `pelada-members` e `pelada-sessions`. Quem muda uma tela de propósito roda `bun run test:visual:update` e commita os PNGs revisados. Quem não deveria mudar nada confirma que `test:visual:docker` passa sem atualizar.
- **Antes de terminar:** `bun run lint && bun run build && bun run test` em `client/`; `./mvnw test` em `core/` quando houver backend.

| PR | Conteúdo | Depende de | Docs | Snapshots |
|----|----------|------------|------|-----------|
| 1 | **Base compartilhada:** `normalizeSearch` (§2, substitui as 3 cópias), `useSelection` (§1, Membros migrado), `components/SelectionBar` (§1, `MemberSelectionBar` migrada), `DateRangeFilter` + `DateRange`/`formatRangeLabel` em `utils/dates` (§4, Galeria migrada). Sem mudança visível | — | FRONTEND.md: árvore de pastas, hooks (`useSelection` no lugar de `useMemberSelection`) | nenhum deve mudar |
| 2 | **Textos e status (§7) + "← Cobranças" (§8).** Inclui o `timeZone` no `FinanceTab` (§6, último item) e o `formatInZone` em detalhes e avisos; os itens do histórico ficam para o PR 6 | 1 (só pela base estar em `dev`) | FRONTEND.md: seção Financeiro (status, rótulos, voltar) | `pelada-finance` |
| 3 | **Busca (§2, §3):** `FinanceMemberChecklist`, seletor de pessoas, Responsáveis, Trocar participante (combobox) | 1 (`normalizeSearch`) | FRONTEND.md: seção Financeiro (seletor, Responsáveis, troca) | `pelada-finance` se a aba de Configurações aparecer na foto; o seletor fica num Sheet e não é fotografado |
| 4 | **Seleção múltipla (§1):** `canReceiveCharge`, modo Selecionar em `FinanceListDetail`, barra e diálogo com a seleção; adiciona uma foto do modo de seleção em `visual.spec.ts` | 1 (`useSelection`, `SelectionBar`), 2 (rótulos das linhas) | FRONTEND.md: "Receber todos" vira "Receber selecionados" | novo shot + `pelada-finance` |
| 5 | **Backend dos filtros (§4, §5):** params em `/audit` e `/movements`, limites no fuso da pelada, V14, testes. Compatível: sem params, o comportamento é o de hoje | — (independente; pode ir em paralelo ao 2–4) | BACKEND.md: tabela Finance REST + V14 na tabela de migrações | nenhum |
| 6 | **Aba Histórico + extrato filtrado + histórico legível (§4 frontend, §5 frontend, §6):** remove o card e o `settingsView`, chips de tipo, `DateRangeFilter`, `utils/financeAudit.ts`; mocks de `/audit`/`/movements` aceitam query | 1, 2 (rótulos de auditoria e `timeZone`), 5 (API) | FRONTEND.md: seção Financeiro (aba Histórico, extrato) e tabela `api/finance.ts` | novo shot da aba Histórico + `pelada-finance` |
| 7 | **Ajustar valor em lote (§9):** endpoint `POST /lists/{listId}/charges/adjust` + testes (sem migração); seleção ampliada (`canAdjustCharge`, `planBulkFinanceActions`), "Ajustar valor" e "Selecionar mensalistas" na barra, `FinanceBulkChargeForm`, rótulo de auditoria em lote. **Último PR:** não altera o que os PRs 1–6 entregam além da barra de seleção do PR 4 | 4 (modo de seleção e barra), 6 (`utils/financeAudit.ts`) | BACKEND.md: tabela Finance REST (novo endpoint); FRONTEND.md: seção Financeiro (ajuste em lote, atalho de mensalistas) e tabela `api/finance.ts` | o shot do modo de seleção (PR 4) muda pela ação nova |

## Verificação

- `core/`: `./mvnw test` (inclui `FlywayMigrationTest` com Docker).
- `client/`: `bun run lint && bun run build && bun run test`; `bun run test:visual:docker` e, para as mudanças intencionais, `test:visual:update` com revisão dos PNGs.
- Manual (`docker compose -f core/docker-compose.dev.yml up` + `bun run dev`, login `leal@futspring.com`):
  - criar uma cobrança com 6 pessoas e publicar;
  - Selecionar → marcar 3 → buscar outro nome e marcar → "Receber" → conferir total e toast;
  - a cobrança com aviso pendente fica desabilitada;
  - Esc sai do modo.
  - Nova cobrança / Adicionar pessoas: buscar "ped" sem acento.
  - Trocar participante: a busca não oferece quem já participa.
  - Configurações → Responsáveis: buscar e marcar.
  - Aba Histórico:
    - intervalo de dois meses (dois calendários no desktop, um em 375 px);
    - chip "Pagamentos";
    - paginação com o total filtrado;
    - data/hora no fuso da pelada;
    - linhas "antes → depois".
  - Caixa: filtrar período; o saldo não muda.
  - Ajuste em lote (PR 7):
    - cobrança "Diária + Juiz" (21,50 + 5,00) publicada;
    - Selecionar → "Selecionar mensalistas" → "Ajustar valor" → remover "Diária" → motivo → salvar;
    - cada mensalista deve R$ 5,00 e o vencimento não mudou;
    - o Caixa não mudou;
    - o Histórico mostra uma entrada "Valor de N participantes ajustado";
    - com um mensalista que já pagou R$ 26,50, o ajuste é recusado e ninguém muda.
  - Tudo em light/dark e em 375 px, sem rolagem horizontal.