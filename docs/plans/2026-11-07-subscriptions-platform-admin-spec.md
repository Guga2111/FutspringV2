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
- `POST /peladas/{id}/subscription/pix-claim {plan}` (criador): grava `pix_claimed_at`/`claimed_plan`, evento, notifica os admins da plataforma. Idempotente: um segundo clique só atualiza o plano e não notifica de novo.

### Notificações

- Categoria nova `BILLING` ("Assinatura"), e-mail e push ligados por padrão; CHECK atualizado na V19.
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

Branch de integração `feat/subscriptions`, saindo de `dev`.

1. **Núcleo backend:** V19, entidades, `SubscriptionState`, `SubscriptionGuard` nos serviços e jobs, criação/trial, teto de membros, `subscription` nos DTOs de pelada/daily, `isPlatformAdmin`.
2. **API da plataforma + área do criador:** `/platform/**`, `GET /peladas/{id}/subscription`, BR Code, `pix-claim`, notificações e `SubscriptionNoticeJob`, env de Pix no deploy.
3. **Front do criador e membros:** tipos/api, banner, `writable` nos botões, área Assinatura, categoria nova nas preferências.
4. **Front do painel:** `/admin/*`.
5. **Docs e política de privacidade:** FRONTEND.md, BACKEND.md, `content.ts` + `LEGAL_VERSION`.

## Rollout

1. Configurar `BILLING_PIX_KEY`/`NAME`/`CITY` nos secrets antes do deploy do PR 2.
2. Deploy (backup do banco já roda no `deploy.sh`; a V19 é aditiva).
3. SQL manual: `platform_admin = true` para mim e `EXEMPT` para a minha pelada.
4. Avisar os criadores atuais que o teste grátis vai até deploy + 30 dias e oferecer as condições de fundadora.

Rollback: voltar o binário mantém as tabelas novas sem efeito (o código antigo não lê). Não apagar as tabelas: elas guardam os pagamentos.
