# Assinaturas por pelada + painel do admin da plataforma

Spec aprovado em 2026-11-07. Regras de negócio de origem: `docs/melhorias-escritas-a-mao.md` (Monetização, Fase de validação). Fase atual: CPF, Pix na chave do dono, cobrança manual pelo painel; sem gateway, sem webhook.

## Escopo

Entra:
1. Ciclo da assinatura por pelada (Trial, Ativa, Em atraso, Somente leitura, Pendente, Isenta), `requireWritable` no backend, banner e botões desabilitados no front.
2. Painel do admin da plataforma: peladas (estado, validade, ações), registro de pagamentos (total por período para o carnê-leão), usuários e métricas.
3. Área "Assinatura" do criador: planos, Pix (QR + copia e cola) e "Já fiz o Pix".
4. Teto de 30 membros no trial (e em Pendente).
5. Avisos ao criador (vencendo, em atraso) e ao admin (Pix informado).

Fora (anotado para depois): tetos de uploads e e-mails, Asaas/webhook/Pix Automático, cupom, export CSV, tela para conceder admin da plataforma, impersonar usuário.

## 1. Modelo de dados e estados

### Migration V19 (aditiva)

| Mudança | Detalhe |
|---|---|
| `users.platform_admin` | `BOOLEAN NOT NULL DEFAULT false`. Ligado só por SQL; nenhuma rota escreve nela |
| `users.trial_used_at` | `TIMESTAMPTZ NULL`. Um trial por conta criadora; transferir a pelada não reinicia |
| `pelada_subscriptions` | `pelada_id` (FK único, `ON DELETE CASCADE`), `kind` (CHECK `TRIAL`, `PENDING`, `PAID`, `EXEMPT`, `CANCELED`), `valid_until TIMESTAMPTZ NULL`, `plan` (CHECK `MONTHLY`, `ANNUAL`, null), `founder BOOLEAN DEFAULT false`, `pix_claimed_at TIMESTAMPTZ NULL`, `claimed_plan`, `note`, `created_at`, `updated_at`. Índice em `(kind, valid_until)` |
| `subscription_payments` | `pelada_id` (FK `ON DELETE SET NULL`, indexado), `pelada_name` e `payer_id`/`payer_name` (snapshot), `amount NUMERIC(10,2)`, `paid_on DATE`, `plan`, `period_start`, `period_end`, `recorded_by_id`, `note`, `voided_at`, `void_reason`, `created_at`. Índice em `paid_on` |
| `subscription_events` | `pelada_id` (FK `ON DELETE CASCADE`), `actor_id` (null = sistema), `type` (CHECK), `kind_before`/`kind_after`, `valid_until_before`/`valid_until_after`, `note`, `created_at`. Índice `(pelada_id, created_at DESC)` |
| Backfill | Cada pelada existente recebe `TRIAL` com `valid_until` = instante da migration + 30 dias; `trial_used_at` dos criadores = agora |

Depois do deploy, por SQL (o id não fica no código): `UPDATE pelada_subscriptions SET kind = 'EXEMPT' WHERE pelada_id = <minha>` e `UPDATE users SET platform_admin = true WHERE id = <meu>`.

### Estado efetivo

Calculado por uma função pura `SubscriptionState.of(subscription, now)`. Nenhum job muda estado.

| `kind` | Condição | Estado (`SubscriptionStatus`) | Escreve |
|---|---|---|---|
| `TRIAL` | `now < valid_until` | `TRIAL` | sim |
| `TRIAL` | venceu | `READ_ONLY` (trial não tem carência) | não |
| `PAID` | `now < valid_until` | `ACTIVE` | sim |
| `PAID` | `now < valid_until + 7 dias` | `PAST_DUE` | sim |
| `PAID` | depois | `READ_ONLY` | não |
| `PENDING` | — | `PENDING` | não |
| `CANCELED` | — | `READ_ONLY` | não |
| `EXEMPT` | — | `EXEMPT` | sim |

### Regras

- **Pelada nova** (`PeladaService.createPelada`): se `trial_used_at` do criador é null, nasce `TRIAL` por 30 dias e marca `trial_used_at`. Senão, nasce `PENDING`. A mesma regra vale para o seed de dev.
- **Registrar pagamento:** `valid_until = fim do dia (America/Sao_Paulo) de max(now, valid_until) + 1 mês | 1 ano`, `kind = PAID`, `plan` gravado, `pix_claimed_at` limpo, evento gravado. Pagar adiantado nunca faz perder dias.
- **Ações avulsas:** definir validade (vira `PAID`), estender dias (mantém o `kind`; serve para alongar trial), isentar, cancelar, marcar/desmarcar fundadora. Todas com nota e evento.
- **Estorno:** `voided_at` + motivo; não mexe na validade (o admin ajusta à parte se quiser). Totais ignoram estornados.
- **Preços:** constantes no backend (`SubscriptionPlans`): mensal R$19,90, anual R$199,00, faixa grande (> 40 membros) R$29,90 mensal / R$299,00 anual. O valor efetivamente cobrado fica no pagamento, e é assim que o preço travado de fundadora é registrado.
- **Teto do trial:** com estado `TRIAL` ou `PENDING`, aceitar convite ou entrar por link com a pelada já em 30 membros dá 409 "Esta pelada está no período de teste, que vai até 30 membros. Peça ao criador para assinar." A checagem roda sob a trava da pelada que o fluxo de convite já pega. Convidar continua permitido.
- **Transferência:** a assinatura é da pelada e acompanha a transferência; o novo criador vê a área Assinatura.
- **Excluir pelada:** permitido em qualquer estado. Os pagamentos ficam (FK `SET NULL` + snapshot).

## 2. Backend

### Trava

`SubscriptionGuard.requireWritable(Pelada)` em `shared/helper`, chamado logo depois do check de permissão. A resposta é **402** "Esta pelada está em somente leitura. Renove a assinatura para voltar a usar." (um código próprio para o front oferecer "Assinar").

Travado:
- `DailyService`: criar, mudar status, apagar, importar da mensagem (`populate`), foto do campeão.
- `DailyAttendanceService`: confirmar/desconfirmar (próprio, pelo admin, todos).
- `DailyTeamManagementService`: sortear, trocar, salvar, nome, cor.
- `DailyResultsService`: resultados, finalizar.
- Financeiro: uma chamada no contexto de `FinanceTransactions` (todas as escritas, inclusive o "informar pagamento" do jogador e os uploads de QR/comprovante).
- Jobs: `DailySchedulerService` pula peladas não-escrevíveis; `ScheduledNotificationJob` também.

Liberado: leituras, chat, configurações e foto da pelada, membros (admin, estrelas, mensalista, remover), convites, transferir, excluir, `bumps/seen`, notificações, conta.

Regra nova em "Rules for new code" do BACKEND.md: todo comando que escreve dado esportivo ou financeiro de uma pelada chama `requireWritable`, e ganha um teste de 402.

### Admin da plataforma

- Domínio novo `domain/platform` (controller, serviços, DTOs). Assinatura em `domain/subscription` (entidades, `SubscriptionState`, `SubscriptionService`, `SubscriptionGuard`, job de avisos).
- `PlatformAccess.requirePlatformAdmin(User)` no início de cada método de serviço. Quem não é admin recebe **404** (não revela que o painel existe).
- Ações sobre uma pelada pegam `PeladaRepository.findByIdForUpdate`, a mesma trava das outras escritas.
- `UserResponseDTO` e `ProfileDTO` (próprio) ganham `isPlatformAdmin`. Só aparece no próprio usuário.

| Método | Rota (`/api/v1/platform`) | Corpo / resposta |
|---|---|---|
| GET | `/overview` | `{counts: {TRIAL, ACTIVE, PAST_DUE, READ_ONLY, PENDING, EXEMPT}, trialsEndingSoon, pixClaimed, revenueThisMonth, revenueByMonth: [{month, total}] (12), recurringEstimate, users: {total, last30Days}}`. Agregações em SQL, número constante de queries |
| GET | `/peladas?state=&q=&page=&size=` | Página `{items: [{id, name, creator: PublicUserDTO, memberCount, status, validUntil, founder, pixClaimedAt, lastSessionDate}], total}`; `state` filtra pelo estado efetivo, traduzido em condição SQL sobre `kind` + `valid_until` |
| GET | `/peladas/{id}` | `{pelada, subscription, payments, events}` |
| POST | `/peladas/{id}/payments` | `{plan, amount, paidOn, note}` → detalhe |
| POST | `/peladas/{id}/subscription` | `{action: SET_VALID_UNTIL\|EXTEND_DAYS\|EXEMPT\|CANCEL\|SET_FOUNDER, validUntil?, days?, founder?, note}` → detalhe; `note` obrigatória exceto em `SET_FOUNDER` |
| POST | `/payments/{id}/void` | `{reason}` → pagamento |
| GET | `/payments?from=&to=&page=` | `{items, total, sum}`; datas ISO inclusivas sobre `paid_on` |
| GET | `/users?q=&page=` | `{items: [{id, username, email, createdAt, peladasCreated: [{id, name}], memberOf, trialUsedAt, isDeleted}], total}` |

Ver e-mail no painel é tratamento pelo operador: adicionar uma linha na política de privacidade (`components/legal/content.ts`, `LEGAL_VERSION` sobe).

### Lado do criador e dos membros

- `PeladaDetailResponseDTO`, `PeladaResponseDTO` (`/peladas/my`) e `DailyDetailDTO` ganham `subscription: {status, validUntil, writable}`. Lido em lote no `/peladas/my` (uma query a mais, constante).
- `GET /peladas/{id}/subscription` (criador; outros 403): `{status, validUntil, plan, founder, pixClaimedAt, claimedPlan, plans: [{plan, amount}] (faixa conforme membros), pix: {copyPaste, recipient} | null, payments: [{paidOn, amount, plan, periodEnd}]}`.
- O Pix é um BR Code estático (EMV + CRC16, gerado no backend) com valor e txid `FUT{peladaId}`, um por plano. Configuração em env `BILLING_PIX_KEY`, `BILLING_PIX_NAME`, `BILLING_PIX_CITY` (secrets/variables no deploy, `core/.env.example`). Sem a chave, `pix` vem null e a tela mostra "Fale com o suporte".
  - Os valores ficam só nos secrets e no `core/.env`, nunca em arquivo versionado.
  - Formato exigido pelo BR Code:
    - Chave de telefone em E.164 (`+55` + DDD + número). O backend normaliza: aceita a chave só com dígitos e acrescenta o `+55`.
    - Nome do recebedor com no máximo 25 caracteres e cidade com no máximo 15, sem acento e em maiúsculas. O backend remove os acentos, e a inicialização falha se o nome ou a cidade passar do limite, em vez de truncar sem avisar.
  - A tela mostra o nome completo do recebedor (`BILLING_PIX_NAME`). O nome abreviado vai só dentro do código Pix (`BILLING_PIX_MERCHANT_NAME`, opcional; sem ele, usa `BILLING_PIX_NAME`).
- `POST /peladas/{id}/subscription/pix-claim {plan}` (criador): grava `pix_claimed_at`/`claimed_plan`, evento, notifica os admins da plataforma. Idempotente: um segundo clique só atualiza o plano e não notifica de novo.

### Notificações

- Categoria nova `BILLING` ("Assinatura"), e-mail e push ligados por padrão; CHECK atualizado numa V21 (PR 8).
- Tipos novos:
  - `SUBSCRIPTION_ENDING` (criador, 5 dias antes do fim do trial ou do pago).
  - `SUBSCRIPTION_PAST_DUE` (criador, ao entrar na carência).
  - `SUBSCRIPTION_PIX_CLAIMED` (admins da plataforma, por evento).
- Todos com texto em `NotificationContent` e link (`/pelada/{id}/assinatura` ou `/admin/peladas/{id}`).
- `SubscriptionNoticeJob`, cron diário 09:00 America/Sao_Paulo. Seleciona por `kind` + `valid_until` e usa guard `NOT EXISTS` (mesmo tipo, mesma pelada, criada depois de `valid_until − 6 dias`), então uma execução repetida ou atrasada não duplica. Cada pelada roda no próprio `TransactionTemplate`.

## 3. Telas

### Painel (`/admin/*`)

Rotas privadas filhas do layout. O item "Admin" fica no `UserMenu` só com `user.isPlatformAdmin`; um 404 da API mostra a página "Bola fora". Navegação entre as áreas por abas underline com links.

- **`/admin`, Visão geral:**
  - KPIs: pagantes, trial, em atraso, somente leitura, receita do mês, usuários.
  - "Precisa de você": Pix informados, trials vencendo em 7 dias, em atraso, cada um com link.
  - Barras de receita dos últimos 12 meses (`ui/chart`, `--chart-1`).
- **`/admin/peladas`:**
  - Busca (300 ms), chips por estado.
  - Tabela TanStack a partir de md, linhas abaixo: pelada, criador, membros, estado (badge por `Record<SubscriptionStatus, …>`), validade, última sessão.
  - `ListPagination`.
- **`/admin/peladas/:id`:**
  - Cartão de estado.
  - **Registrar pagamento** (Sheet, react-hook-form + zod: plano, valor pré-preenchido, data, nota).
  - Estender dias, Definir validade, Isentar, Cancelar (Dialog com nota obrigatória). Fundadora (`Switch`).
  - Pagamentos com "Estornar" (AlertDialog com motivo).
  - Histórico de eventos ("Leal · Pagamento anual registrado · validade 07/12/2026 → 07/12/2027").
- **`/admin/pagamentos`:** `DateRangeFilter`, total do período ("R$ 398,00 em 2 pagamentos") e a lista.
- **`/admin/usuarios`:** busca por nome ou e-mail. Cadastro, peladas criadas (link para `/admin/peladas/:id`), em quantas participa, trial usado, excluído.

### Criador e membros

- **Área "Assinatura"** (`/pelada/:id/assinatura`), grupo Gestão do `PELADA_AREAS`, flag nova `creatorOnly`.
  - Estado e validade.
  - Mensal / Anual num `RadioGroup` de cartões com preço.
  - QR (`qrcode.react` em `bg-paper`) + copia e cola com Copiar e o recebedor.
  - "Já fiz o Pix" → "Pix informado em 07/11 · aguardando confirmação".
  - Pagamentos anteriores.
- **Banner** no layout da pelada (`Alert` compacto):

  | Situação | Criador | Membros |
  |---|---|---|
  | Trial com mais de 5 dias | — | — |
  | Trial com ≤ 5 dias | "Teste grátis até 07/12 · Assinar" | — |
  | Em atraso | "Pagamento pendente: a pelada trava em 14/12 · Renovar" | — |
  | Somente leitura / Pendente | "Pelada em somente leitura · Assinar" | "Pelada em somente leitura: o criador precisa renovar a assinatura" |

- **Botões desabilitados** com a dica "Pelada em somente leitura" quando `writable` é false: Nova sessão, confirmar/desconfirmar, sortear/montar times, resultados, finalizar, ações do financeiro.
  - Fonte única por tela: `usePeladaWorkspace().writable`, `daily.subscription.writable`, `nextDaily`/pelada do `/peladas/my` (Home e sidebar).
  - Um 402 que escapar mostra a mensagem do servidor no toast.

## Testes

- `SubscriptionStateTest`: cada linha da tabela de estados nas bordas exatas (`valid_until`, +7 dias), `Clock.fixed`.
- `SubscriptionGuardTest` + um teste 402 por comando travado (daily, presença, times, resultados, financeiro) e um teste "liberado" para chat, configurações, convites e exclusão.
- Jobs: o scheduler não cria sessão em pelada travada; o lembrete não sai; `SubscriptionNoticeJob` com borda dos 5 dias, idempotência e uma pelada com falha não parando as outras.
- Criação: primeiro trial, segunda pelada `PENDING`, transferência não reinicia.
- Teto: o 31º membro por convite e por link dá 409 em `TRIAL`/`PENDING`; em `ACTIVE` passa.
- Plataforma:
  - 404 para não-admin em toda rota.
  - Pagamento estende a partir de `max(now, valid_until)`.
  - Estorno sai dos totais.
  - Eventos gravados.
  - `QueryCounter` com leituras constantes em `/overview` e `/peladas` (2 vs 20 peladas).
- `PixBrCodeTest`: payload EMV e CRC16 contra um exemplo conhecido do Bacen.
- `MigrationDataTest` (V19): peladas existentes viram `TRIAL` + 30 dias, criadores marcados.
- Contrato: snapshots novos e alterados (`UserResponseDTO`, `PeladaDetailResponseDTO`, `DailyDetailDTO`, DTOs da plataforma).
- Front:
  - Testes de `utils/subscription.ts` (texto do banner por estado e papel).
  - Schemas do Sheet de pagamento.
  - Testes visuais: `/admin`, `/admin/peladas`, `/admin/peladas/:id`, a área Assinatura e os banners de somente leitura e em atraso.

## Plano de PRs

Nove PRs pequenos. Cada um é revisável sozinho, entra com os seus testes e atualiza a parte do BACKEND.md / FRONTEND.md que mudou, como pede o CLAUDE.md.

- **Branches:** integração `feat/subscriptions`, saindo de `dev`. Cada PR sai de `feat/subscriptions` (`feat/subscriptions-<nome>`) e volta contra ela.
- **Ordem de merge:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9. O 3 e o 5 só dependem do 1 e podem andar em paralelo.
- **Para o `main`:** a `feat/subscriptions` só vai para o `main` com o 2 e o 4 juntos, para nenhum botão aparecer ativo e dar 402 ao clicar. No deploy, as peladas existentes ganham 30 dias de trial; o 7 (Pix) e o 8 (avisos) precisam ir ao ar antes desse prazo vencer.

Para planejar um PR, rode o prompt dele numa sessão limpa (`/clear` antes). Ao terminar, o `/plan-task` marca o `[x]` e anota as decisões abaixo do item.

### [x] PR 1 — Modelo e estado (`feat/subscriptions-model`)

- **Entrega:**
  - Migration V19 da seção 1 (colunas em `users`, `pelada_subscriptions`, `subscription_payments`, `subscription_events`, backfill das peladas existentes).
  - Entidades e repositórios em `domain/subscription`.
  - `SubscriptionState.of(subscription, now)` + enum `SubscriptionStatus`.
  - `SubscriptionPlans` (preços).
  - Criação de pelada com `TRIAL` ou `PENDING` (inclui o seed de dev).
- **Fora:** nada trava ainda, e nada aparece na API.
- **Testes:**
  - `SubscriptionStateTest` (todas as bordas).
  - Criação: primeiro trial, segunda pelada `PENDING`, transferência não reinicia.
  - `MigrationDataTest` (V19).
  - `FlywayMigrationTest` verde.
- **Docs:** BACKEND.md (Domain model, tabela de migrations, package layout).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 1 — Modelo e estado"
```

**Decisões (PR 1):**
- `subscription_events.type` já nasce com todos os tipos previstos (`CREATED`, `PAYMENT_RECORDED`, `PAYMENT_VOIDED`, `VALID_UNTIL_SET`, `DAYS_EXTENDED`, `EXEMPTED`, `CANCELED`, `FOUNDER_CHANGED`, `PIX_CLAIMED`), para os PRs 5 e 7 não precisarem de migration.
- O backfill também grava um evento `CREATED` (sem ator) por pelada; a criação pelo app grava `CREATED` com o criador como ator.
- CHECK extra `ck_pelada_subscriptions_valid_until`: `TRIAL` e `PAID` sempre têm `valid_until`. `SubscriptionState` trata um `valid_until` nulo como vencido.
- O trial é tomado por um `UPDATE users SET trial_used_at = now WHERE id = ? AND trial_used_at IS NULL` (`UserRepository.markTrialUsed`): duas peladas criadas ao mesmo tempo pela mesma conta não ganham dois trials.
- O trial vale 30 dias exatos a partir da criação (instante, não fim do dia).
- `SubscriptionStatus.writable()` fica no enum; `SubscriptionState.of` tem uma sobrecarga `(kind, validUntil, now)` para o PR 5 reutilizar.
- `SubscriptionPayment.peladaId` e `SubscriptionEvent.peladaId` são ids simples (o pagamento sobrevive à pelada). `deletePelada` chama `SubscriptionService.deletePeladaData` (apaga assinatura e eventos e desvincula pagamentos) porque o H2 dos testes não tem as ações de FK.
- `users.platform_admin` já é mapeado (`User.platformAdmin`), mas só é lido a partir do PR 5.

### [x] PR 2 — Trava no backend (`feat/subscriptions-guard`)

- **Entrega:**
  - `SubscriptionGuard.requireWritable(Pelada)` → 402.
  - Chamada nos comandos listados em "Trava" (daily, presença, times, resultados) e uma vez no `FinanceTransactions`.
  - `DailySchedulerService` e `ScheduledNotificationJob` pulam peladas não-escrevíveis.
  - Handler do 402 no `GlobalExceptionHandler`, se o `AppException` não bastar.
- **Fora:** o front ainda não sabe do estado (é o PR 4).
- **Testes:**
  - Um teste de 402 por comando travado.
  - Um teste de "liberado" para chat, configurações, convites e exclusão.
  - Jobs pulando pelada travada.
  - Uma pelada `EXEMPT` não trava nada.
- **Docs:** BACKEND.md (permission matrix, Rules for new code, Error handling).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 2 — Trava no backend"
```

**Decisões (PR 2):**
- `SubscriptionGuard` fica em `domain/subscription` (junto do repositório e do `SubscriptionState`), não em `shared/helper`: a seção "Trava" e a seção "Admin" divergiam.
- Pelada sem linha em `pelada_subscriptions` escreve (fail-open, com `log.warn`). Em produção não existe (backfill da V19 + criação); os testes que salvam pelada direto no repositório continuam como estão.
- Sem handler novo: `AppException(HttpStatus.PAYMENT_REQUIRED, …)` já sai como `{status: 402, message, timestamp}` pelo `GlobalExceptionHandler`. A mensagem é `SubscriptionGuard.READ_ONLY_MESSAGE`.
- O guard roda logo depois do check de permissão: quem não é membro/admin continua recebendo 403, e uma pelada travada responde 402 antes de 404 de filhos (time, lista, cobrança) e de 400/409 de regra.
- Financeiro: uma chamada em `FinanceTransactions.execute`, depois do reenvio idempotente (revisão): um reenvio de comando gravado antes da trava devolve o resultado guardado em vez de 402, para o cliente não registrar de novo. Inclui `PUT /managers`. Os uploads (comprovante, QR) checam antes de decodificar a imagem (`requireWritableUpload`, membro primeiro, reenvio passa). `FinancePreservation` (remoção de membro, exclusão de conta/pelada) não passa por ali e segue liberado.
- Revisão: desconfirmar (próprio e pelo admin) e cancelar a sessão ficam liberados, ao contrário do spec: só reduzem dados e, travados, deixavam uma sessão agendada congelada com a lista errada. Uma sessão já ao vivo quando a pelada trava continua sem resultados e sem finalizar (decidido manter).
- `deleteDaily` checa depois da trava da pelada, a mesma que o PR 5 vai usar nas ações sobre a assinatura.
- Jobs: `DailySchedulerService` checa dentro da transação de cada pelada, só quando há sessão a criar; `ScheduledNotificationJob` checa só quando a pelada tem sessão devida (uma query a mais por pelada nesse caso).
- Testes: `SubscriptionGuardTest` (unitário, bordas por `kind`) e `SubscriptionReadOnlyTest` (integração: um caso parametrizado de 402 por comando travado, trial vencido, `EXEMPT` escrevendo, liberados). Os testes unitários dos serviços recebem um `SubscriptionGuard` mockado; os dos jobs, um guard real.

### [x] PR 3 — Teto do trial (`feat/subscriptions-member-cap`)

- **Entrega:** 409 ao aceitar convite ou entrar por link com a pelada em `TRIAL`/`PENDING` já com 30 membros, sob a trava da pelada que o fluxo de convite já pega.
- **Fora:** tetos de uploads e e-mails.
- **Testes:**
  - O 31º membro por convite e por link dá 409.
  - Em `ACTIVE` passa.
  - Concorrência: dois aceites no 30º lugar (`InvitationConcurrencyTest`).
- **Docs:** BACKEND.md (Membership invitations).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 3 — Teto do trial"
```

**Decisões (PR 3):**
- Além do spec: uma pelada em `READ_ONLY` (trial vencido, pago depois da carência, cancelada) não aceita ninguém novo, com qualquer número de membros. Responde **402** com a mensagem de somente leitura do `SubscriptionGuard`. Quem já é membro continua. `TRIAL`/`PENDING` com 30 ou mais membros respondem 409 com a mensagem do spec, que fala em "período de teste" também para `PENDING`.
- A regra fica em `SubscriptionGuard.requireRoomForMember` (`TRIAL_MEMBER_CAP = 30`), chamada uma vez em `PeladaInvitationService.join`, que é o ponto comum do aceite e do link. Ela roda depois do retorno idempotente de quem já é membro, então reaceitar ou reentrar numa pelada cheia continua dando 200. No link, o 403 de removido vem antes.
- A contagem é `pelada.getMembers().size()`, da coleção carregada depois do `findByIdForUpdate` (o `add` já a carregava). Não precisa de query nova, e a contagem é consistente sob a trava. `InvitationConcurrencyTest` cobre dois aceites na 30ª vaga: um entra, o outro recebe 409, e o convite do perdedor volta para `PENDING` no rollback.
- Pelada sem linha de assinatura não tem teto (fail-open, como o `isWritable`). Convidar e gerar link continuam liberados.
- Front sem mudança: o 409/402 aparece no erro inline de `useInvitationResponse` e da página do link.

### [x] PR 4 — Estado nos DTOs + front de leitura (`feat/subscriptions-readonly-ui`)

- **Entrega:**
  - `subscription {status, validUntil, writable}` em `PeladaDetailResponseDTO`, `PeladaResponseDTO` (`/peladas/my`, lido em lote) e `DailyDetailDTO`.
  - Front: tipos, `utils/subscription.ts` (texto do banner por estado e papel), banner no layout da pelada, `writable` em `usePeladaWorkspace`, e botões desabilitados com a dica (sessões, presença, times, resultados, financeiro, card da Home).
  - Link "Assinar" aponta para `/pelada/:id/assinatura`, que fica como "Em breve" até o PR 7.
- **Testes:**
  - Snapshots de contrato.
  - Contagem de queries constante no `/peladas/my`.
  - Vitest do `utils/subscription.ts`.
  - Testes visuais dos banners (somente leitura, em atraso).
- **Docs:** BACKEND.md (API reference), FRONTEND.md (types, hooks, Routing).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 4 — Estado nos DTOs + front de leitura"
```

**Decisões (PR 4):**
- `SubscriptionSummaryDTO {status, validUntil, writable}` em `domain/subscription/dto`. A leitura fica no `SubscriptionGuard` (`summary` / `summaries`), junto do `isWritable`, para o banner e a trava usarem a mesma regra. O `/peladas/my` (e `/users/{id}/peladas`) faz uma query a mais em lote (`findByPeladaIdIn`). `SubscriptionReadModelTest` compara `statements` com 2 e 20 peladas, porque `reads` também conta cada pelada hidratada pela lista.
- Pelada sem linha de assinatura: `subscription: null`, e o front lê como escrevível (`isWritable`), igual ao fail-open do guard. As respostas de criar, editar, foto e transferir (`PeladaResponseDTO.forAdmin`) também trazem `null`. O `detailFields` do front copia campo a campo, então o estado do detalhe não se perde.
- `DailyDetailDTO` ganhou `creatorId`, para a página da sessão mostrar o banner por papel. Ela fica fora do layout da pelada.
- "Assinar" / "Renovar" no banner são botões desabilitados com "Em breve", sem rota nova. O PR 7 troca pelo link de `/pelada/:id/assinatura`.
- Dica: o banner tem `id="subscription-notice"`, e os botões travados apontam para ele com `aria-describedby`. O botão de confirmar presença troca o texto para "Pelada em somente leitura", como já fazia com "Sessão lotada". O comando "Nova sessão" do ⌘K some em vez de ficar desabilitado.
- Ficam ativos, como no backend do PR 2: desistir/desconfirmar (próprio e pelo admin), remover da lista e cancelar a sessão. Em uma pelada travada, nome e cor do time voltam a ser texto simples.
- Financeiro: `useFinance(..., writable)` expõe `writable` e `locked = pending || !writable`. Todo controle de escrita usa um dos dois. Leitura, filtros, páginas e a mensagem do WhatsApp continuam liberados. `SettingsCardFooter` ganhou `locked`, que desabilita só o Salvar.
- O estado vem calculado na resposta, sem polling. O banner do fim do trial aparece na hora certa por `useNowUntil`. Se a tela estiver aberta quando a pelada travar, ela só muda ao recarregar, e o 402 aparece no toast.

### [x] PR 5 — API do admin da plataforma (`feat/subscriptions-platform-api`)

- **Entrega:**
  - `isPlatformAdmin` em `UserResponseDTO` e `ProfileDTO` (próprio).
  - `PlatformAccess.requirePlatformAdmin` (404 para quem não é admin).
  - Domínio `domain/platform` com todas as rotas `/platform/**` da seção 2 (overview, peladas, detalhe, pagamentos, ações, estorno, lista de pagamentos, usuários), eventos gravados e a trava de linha da pelada.
- **Fora:** telas (PR 6) e notificação de Pix informado (PR 8).
- **Testes:**
  - 404 para não-admin em toda rota.
  - Pagamento estende a partir de `max(now, valid_until)`.
  - Estorno sai dos totais.
  - Filtro por estado efetivo.
  - `QueryCounter` constante em `/overview` e `/peladas` (2 vs 20).
  - Snapshots de contrato.
- **Docs:** BACKEND.md (API reference, permission matrix, package layout).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 5 — API do admin da plataforma"
```

**Decisões (PR 5):**
- `users` não tinha data de cadastro: a V20 cria `created_at` e `updated_at` (nullable). O backfill copia `terms_accepted_at` (o cadastro desde a V16), e as contas mais antigas ficam null. As duas colunas são preenchidas pelo Hibernate (`@CreationTimestamp` / `@UpdateTimestamp`); statements em lote como `markTrialUsed` não mexem no `updatedAt`. **O PR 8 passa a usar a V21.**
- Overview: `trialsEndingSoon` (7 dias) e `pixClaimed` são listas `{id, name, date}` de até 20 itens, para o "Precisa de você" linkar cada item a `/admin/peladas/:id`. "Em atraso" fica em `counts.PAST_DUE`, com link para `/admin/peladas?state=PAST_DUE`. `counts` traz todos os estados, inclusive os zerados.
- O estado efetivo em SQL fica num só lugar, `SubscriptionStatusFilter` (uma `Specification` sobre `kind` + `valid_until`). O filtro do `/peladas` e as contagens do overview usam esse filtro, e o teste confere cada estado contra `SubscriptionState.of` nas bordas exatas. As peladas sem linha de assinatura não aparecem na lista (em produção não existem).
- Os comandos ficam em `SubscriptionService` (que grava o evento com antes/depois, ator e nota). `PlatformCommandService` faz o acesso, a trava da linha da pelada e as validações por ação (400 no campo). Pagamento e extensão partem de `validUntil` só enquanto um trial ou um pago está correndo; senão, de agora. O "fim do dia" é 23:59:59 em São Paulo (`BILLING_ZONE`): meia-noite do dia seguinte mostraria a data errada, e `LocalTime.MAX` arredondaria para o dia seguinte no Postgres.
- Isentar e cancelar zeram `valid_until` (o evento guarda o anterior); isentar também limpa um Pix informado. Estender só vale para `TRIAL`/`PAID` (409 nos outros). `SET_VALID_UNTIL` aceita data no passado, o que trava a pelada.
- O estorno trava a linha do pagamento (não a da pelada, que pode já ter sido excluída) e grava `PAYMENT_VOIDED` enquanto a pelada existe. Um segundo estorno dá 409.
- Dinheiro como string de duas casas, como no financeiro (`"19.90"` na entrada e na saída). `recurringEstimate` = pagamentos não estornados cujo período cobre agora, de peladas ainda `PAID`, com o anual dividido por 12. Ele usa o valor cobrado de fato (o preço de fundadora entra certo), e uma renovação adiantada não conta duas vezes. `lastSessionDate` = última sessão `FINISHED`.
- O detalhe traz `prices` (preço de tabela por plano para o número de membros), para o Sheet do PR 6 já vir com o valor preenchido. Pagamentos e eventos vêm limitados aos 50 mais recentes.
- 404 para quem não é admin, mas um corpo ou id malformado ainda responde 400 antes (`@Valid`). As rotas não são segredo, porque o bundle do front do PR 6 vai nomeá-las; o que fica protegido são os dados.
- Contrato: `isPlatformAdmin` em `UserResponseDTO` e só no próprio `ProfileDTO` (o tipo do front ficou opcional, porque uma sessão guardada antes não traz o campo). O `ContractFixtures` passou a montar `Map` com chave enum. Os request bodies da plataforma entram no snapshot de requests no PR 6, junto de `api/platform.ts`.

### [x] PR 6 — Painel do admin, front (`feat/subscriptions-platform-ui`)

- **Entrega:**
  - `api/platform.ts`, tipos e schemas.
  - Item "Admin" no `UserMenu`.
  - Rotas `/admin`, `/admin/peladas`, `/admin/peladas/:id`, `/admin/pagamentos`, `/admin/usuarios` como na seção 3 (Sheet de pagamento com react-hook-form + zod, dialogs de ação com nota, estorno, gráfico de receita).
- **Testes:**
  - Vitest dos schemas e helpers.
  - Testes visuais das cinco telas (fixtures novas), 375 px e claro/escuro.
- **Docs:** FRONTEND.md (Routing, API modules, hooks, folder structure).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 6 — Painel do admin, front"
```

**Decisões (PR 6):**
- Um 404 da API do painel faz a página renderizar `<Navigate to="/404" replace>`. `/404` não tem rota própria, cai no `*` e mostra o "Bola fora" em tela cheia, sem a sidebar (escolha do dono). A URL vira `/404`.
- O item "Admin" do `UserMenu` lê `isPlatformAdmin` da sessão guardada. Quem vira admin por SQL entra de novo uma vez; `/admin` digitado já funciona, porque a API é quem decide.
- Não há layout de rota para `/admin`: cada página tem `PlatformPageHeader` (breadcrumb, título e as áreas como links sublinhados num `nav`, não um tablist, como o `PeladaPageHeader`).
- A tabela de peladas é um `Table` do shadcn sem TanStack: a lista vem ordenada e paginada pelo servidor, sem ordenar nem esconder colunas, e o TanStack acrescentaria um segundo aviso `incompatible-library` no lint.
- O filtro de estado fica na URL (`?estado=PAST_DUE`), para o "Em atraso" da visão geral linkar direto. A página de cada lista pertence ao filtro (`usePageOf`). As buscas esperam 300 ms (`useDebouncedValue`).
- Uma leitura só para o painel inteiro (`usePlatformRequest`, chaveada pelo fetcher estável). Os comandos devolvem o detalhe e ele substitui o da tela. O estorno devolve só o pagamento: ele entra na hora e o detalhe é lido de novo por causa do evento `PAYMENT_VOIDED`.
- Fundadora é um `Switch` salvo na hora (`SET_FOUNDER`, `note: null`). As outras ações usam um `Dialog` só, com o campo de cada uma e nota obrigatória. "Estender dias" fica desabilitado fora de `TRIAL`/`PAID` (o servidor dá 409), "Isentar" numa pelada já isenta e "Cancelar" numa já cancelada.
- O Sheet de pagamento começa no plano que o criador informou no Pix (senão no plano atual, senão mensal), com o preço de tabela (`prices`) até o valor ser digitado. O valor aceita `19,90` / `1.234,56` (`normalizeFinanceAmount`) e vai como `"19.90"`. A data é hoje em São Paulo e não pode ser no futuro.
- O evento não traz o plano, então a linha do histórico diz "Pagamento registrado", sem "anual". A validade aparece só quando mudou: "validade A → B", "validade até B" (sem anterior) ou "sem validade".
- `ChartTooltipContent` ganhou `formatValue` opcional (compatível), para o tooltip da receita mostrar a string exata em reais.
- Os campos dos formulários reaproveitam `FinanceInput` / `FinanceSelect` / `FinanceFormError`, que são genéricos apesar do nome.
- Os request bodies da plataforma entraram no snapshot de contrato (`PlatformRequests.RecordPayment`, `.ChangeSubscription`, `.VoidPayment`).

### [x] PR 7 — Pix e área Assinatura do criador (`feat/subscriptions-pix`)

- **Entrega:**
  - `PixBrCode` (EMV + CRC16, normalização da chave e do nome, falha na inicialização se passar do limite).
  - Envs `BILLING_PIX_*` no `deploy.yml`, `docker-compose.*.yml` e `core/.env.example`. Os secrets já existem no GitHub.
  - `GET /peladas/{id}/subscription` (criador) e `POST /peladas/{id}/subscription/pix-claim`.
  - Front: área "Assinatura" (`creatorOnly` em `PELADA_AREAS`) com planos, QR, copia e cola, "Já fiz o Pix" e pagamentos anteriores.
- **Fora:** a notificação ao admin do Pix informado (PR 8). Por enquanto, o Pix informado aparece no painel.
- **Testes:**
  - `PixBrCodeTest` contra um exemplo do Bacen.
  - 403 para quem não é criador.
  - Claim idempotente.
  - Teste visual da área.
- **Docs:** BACKEND.md (Configuration, CI/CD, API reference), FRONTEND.md (Routing, Pelada navigation).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 7 — Pix e área Assinatura do criador"
```

**Decisões (PR 7):**
- Os quatro `BILLING_PIX_*` (`KEY`, `NAME`, `MERCHANT_NAME`, `CITY`) já eram secrets no GitHub (conferido com `gh secret list`); o `deploy.yml` lê os quatro de `secrets.*` e só avisa quando falta a chave.
- Chave aceita: telefone (só dígitos ganham `+55`; `55…` com 12–13 dígitos ganha o `+`), e-mail (minúsculas) e chave aleatória. CPF/CNPJ não: 11 dígitos são lidos como telefone. Formato inválido, nome no código > 25 ou cidade > 15 (depois de tirar acento e pôr em maiúsculas) derrubam a inicialização (`BillingPixConfig`), como o VAPID.
- `PixBrCode` só codifica (EMV + CRC-16/CCITT-FALSE), sem normalizar, para o teste reproduzir byte a byte o exemplo do manual do Bacen.
- **Desvio do contrato do spec:** em vez de `pix: {copyPaste, recipient}`, cada plano traz o próprio `pixCode` (o BR Code leva o valor, então são dois códigos) e `pixRecipient` fica no topo; os dois vêm null sem Pix configurado. Os pagamentos mostrados ao criador são os 20 últimos não estornados.
- Rotas em `SubscriptionController` (`domain/subscription`), não no `PeladaController`. Pelada inexistente 404; qualquer um que não seja o criador (admin incluído) 403. O claim trava a linha da pelada, como os comandos do painel.
- Claim: o primeiro grava `pixClaimedAt` + `claimedPlan` e um evento `PIX_CLAIMED`; outro plano só troca o plano (a data fica, para a fila do painel continuar do mais antigo) e grava evento; o mesmo plano de novo não muda nada. `claimPix` devolve se foi o primeiro, para o PR 8 notificar só nesse caso. Pelada `EXEMPT` → 409; sem Pix configurado → 409; pelada travada pode informar (é como destrava).
- `money()` saiu de `PlatformReadService` para `SubscriptionPlans.money`, usado pelo painel e pela área.
- Front: `adminOnly` virou `access: "member" | "admin" | "creator"` em `PELADA_AREAS` (o `creatorOnly` do spec), com `PeladaRole` / `peladaRole` / `canSeeArea`; o workspace ganhou `isCreator` e `role`. O "Assinar"/"Renovar" do banner virou link para a área (também na página da sessão); o banner some na própria área, e o chat também.
- A tela usa react-hook-form + `pixClaimSchema` (o plano escolhido decide qual QR aparece). "Já fiz o Pix" fica desabilitado quando o plano escolhido já é o informado. Pelada isenta não mostra planos nem Pix. `SubscriptionStatusBadge` do painel é reaproveitado.
- O app do banco não foi testado com um QR real: vale escanear um em dev antes do merge.

### [x] PR 8 — Avisos de assinatura (`feat/subscriptions-notices`)

- **Entrega:**
  - Migration V21 (CHECK de `type` e `category` das notificações; a V20 foi usada pelo PR 5).
  - Categoria `BILLING` ("Assinatura", e-mail e push ligados).
  - Tipos `SUBSCRIPTION_ENDING`, `SUBSCRIPTION_PAST_DUE` e `SUBSCRIPTION_PIX_CLAIMED` em `NotificationContent`.
  - `SubscriptionNoticeJob` (09:00 America/Sao_Paulo, guard `NOT EXISTS`).
  - Evento do pix-claim.
  - Front: `Record`s de notificação e a linha nova em `/settings#notificacoes`.
- **Testes:**
  - Borda dos 5 dias.
  - Entrada na carência.
  - Idempotência.
  - Uma pelada com falha não para as outras.
  - Destinatários (criador / admins da plataforma).
  - Matriz e-mail e push.
  - `MigrationDataTest` (V21).
- **Docs:** BACKEND.md (Notifications, migrations), FRONTEND.md (notification types, preferências).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 8 — Avisos de assinatura"
```

**Decisões (PR 8):**
- A data do aviso fica numa coluna nova, `notifications.valid_until` (snapshot do `valid_until` da assinatura quando o aviso saiu), exposta como `validUntil` no `NotificationDTO` (snapshot de contrato). O texto mostra a data exata mesmo se a assinatura for renovada depois. A data da trava do `PAST_DUE` é derivada (`validUntil` + 7 dias: `SubscriptionState.GRACE` no backend, `GRACE_DAYS` no front). Datas em São Paulo.
- Texto único para trial e pago: "O plano da X vence em 07/12. Assine para a pelada não travar."
- **Desvio do guard do spec:** em vez de "criada depois de `valid_until − 6 dias`", a chave de idempotência é (pelada, tipo, `valid_until`), com `NOT EXISTS` nas duas queries do job. Mesmo efeito (repetida ou atrasada não duplica; renovar gera aviso novo) sem aritmética de intervalo em JPQL. `uq_notifications_subscription (pelada_id, type, valid_until)` parcial é o backstop em produção.
- Janelas: `ENDING` para `TRIAL`/`PAID` com `validUntil` em `(now, now + 5 dias]`; `PAST_DUE` para `PAID` com `validUntil` em `(now − 7 dias, now]`. O destinatário é o criador atual (uma transferência leva o aviso junto). O job fica em `domain/subscription` e chama `NotificationService.notifySubscription`.
- Pix informado: `PixClaimedEvent` só no primeiro claim (troca de plano e repetição não notificam; depois de um pagamento registrado, o claim volta a ser novo). Vai para todo admin da plataforma não excluído (`findByPlatformAdminTrueAndDeletedAtIsNull`); o criador é o ator e fica de fora se também for admin.
- `BILLING` aparece para todo usuário em `/settings` (e-mail e push ligados), embora só criadores e admins da plataforma recebam. `PUT /notifications/preferences` aceita até 5 itens.
- Nenhum dos três mostra toast; no sino, `ENDING`/`PAST_DUE` levam a `/pelada/:id/assinatura` e o Pix a `/admin/peladas/:id`.

### [ ] PR 9 — Política de privacidade (`feat/subscriptions-privacy`)

- **Entrega:**
  - Linha em `components/legal/content.ts` sobre o operador ver nome, e-mail e dados de assinatura no painel, e sobre os pagamentos guardados.
  - `LEGAL_VERSION` e `LegalTerms.CURRENT_VERSION` sobem juntos.
- **Testes:** `bun run test` e `./mvnw test` verdes (o cadastro exige a versão nova).
- **Docs:** FRONTEND.md (`/termos`, `/privacidade`).

```
/plan-task docs/plans/2026-11-07-subscriptions-platform-admin-spec.md "PR 9 — Política de privacidade"
```

## Rollout

1. Configurar `BILLING_PIX_KEY`/`NAME`/`CITY` nos secrets antes do deploy do PR 2.
2. Deploy (backup do banco já roda no `deploy.sh`; a V19 é aditiva).
3. SQL manual: `platform_admin = true` para mim e `EXEMPT` para a minha pelada.
4. Avisar os criadores atuais que o teste grátis vai até deploy + 30 dias e oferecer as condições de fundadora.

Rollback: voltar o binário mantém as tabelas novas sem efeito (o código antigo não lê). Não apagar as tabelas: elas guardam os pagamentos.
