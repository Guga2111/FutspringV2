# Configurações da conta — SPEC

Data: 2026-11-06 · Roadmap: "Novembro — base para vender" (`docs/melhorias-escritas-a-mao.md`) · Base: `feat/finance-responsaveis` (precisa de `finance_managers` e V15); merge depois de `feat/legal-pages` (V16)

## Objetivo

Uma página `/settings` que reúne tudo o que é da conta: perfil (foto, nome, posição), troca de senha logado, tema, notificações por e-mail e exclusão de conta. Hoje esses pedaços estão espalhados (`EditProfileModal` no perfil, toggle de tema no `UserMenu`, `/settings/notifications`) e não existe troca de senha logado nem exclusão de conta.

Fora do escopo: troca de e-mail, exportação de dados (spec LGPD própria), imagem de capa (já não aparece em nenhuma tela).

## Decisões

| Decisão | Escolha | Por quê |
|---|---|---|
| Exclusão | **Anonimizar**, não apagar | Partidas, prêmios, rankings e o caixa de outras pessoas apontam para o usuário; apagar faria placares e caixa não fecharem |
| Como o excluído aparece | Some de toda lista atual (ranking, presença, membros); nos registros passados aparece **"Ex-jogador"**, avatar cinza, sem link | O ranking (`RankingService`) e o ranking de presença já só listam membros atuais; ninguém vê "excluído" em 4º lugar |
| Criador / único responsável financeiro | **Bloqueia** a exclusão e lista as pendências com links | Reusa a transferência e a regra de "pelo menos um responsável" que já existem; o sistema não decide por outras pessoas |
| Mensagens do chat | **Ficam**, com autor "Ex-jogador" | A conversa dos outros continua fazendo sentido. Reavaliar na spec LGPD |
| Estrutura | **Uma página com seções** (como as configurações da pelada) | Poucas seções; o índice "Nesta página" já existe (`SettingsNav`) |
| Tema | Claro / Escuro / **Sistema**, por dispositivo | Continua em `localStorage.theme`; "Sistema" apaga a chave e o script do `index.html` volta a seguir o SO |

## Frontend

### Navegação

- `UserMenu`: o item "Notificações" vira **"Configurações"** → `/settings`. O atalho de tema continua no menu.
- A engrenagem do painel do sino → `/settings#notificacoes`.
- `/settings/notifications` redireciona (`<Navigate replace>`) para `/settings#notificacoes`, para os links antigos e e-mails já enviados.
- `/profile/:id`: "Editar perfil" vira `<Button asChild><Link to="/settings#perfil"/></Button>`. `EditProfileModal` é apagado (sai também da lista de antipatterns 8 do FRONTEND.md).
- `NotificationSettingsPage` é apagada; `AccountSettingsPage` (`pages/AccountSettingsPage.tsx`, lazy) toma o lugar.

### Página `/settings`

`PageContainer` + `PageHeader` ("Configurações"), cards `SettingsCard` empilhados, cada um com seu `useForm`, rodapé ("Tudo salvo." / "Alterações não salvas", Cancelar, Salvar alterações) e envio independente; a partir de `lg`, o `SettingsNav` fixo ("Nesta página"). Componentes em `components/account-settings/`, hooks em `components/account-settings/hooks/`.

| Seção (âncora) | Conteúdo | Salva |
|---|---|---|
| **Perfil** (`#perfil`) | Foto (enviada ao escolher, `validateImageFile`; "Remover" fora do escopo), nome (3–30), posição (`EDITABLE_POSITIONS` ou nenhuma) | `PUT /users/{id}` (existente), `profileSchema`. Atualiza o usuário do `AuthContext` para o sidebar refletir |
| **Segurança** (`#seguranca`) | Senha atual, nova senha (≥ 8), confirmação, mostrar/ocultar (`AuthTextField`) | `POST /users/me/password`. Sucesso: `login(token, user)` com o token novo, `form.reset`, toast "Senha alterada. Outros dispositivos foram desconectados." Senha atual errada: erro no campo (`applyServerErrors`) |
| **Aparência** (`#aparencia`) | `ToggleGroup` Claro / Escuro / Sistema, aplicado na hora; nota "Vale só para este dispositivo." | `useTheme` ganha `preference: "light" \| "dark" \| "system"` e `setPreference`; "system" remove `localStorage.theme` e aplica `prefers-color-scheme` (o listener do `index.html` passa a valer de novo) |
| **Notificações** (`#notificacoes`) | `NotificationPreferencesCard` atual, sem mudanças, + "As notificações dentro do app ficam sempre ativas." | como hoje |
| **Zona de perigo** (`#excluir-conta`) | "Excluir conta" | ver abaixo |

Schema novo em `schemas/user.ts`: `changePasswordSchema` (espelha `ChangePasswordRequestDTO`: `currentPassword` `.min(1)`, `newPassword` `.min(8)`, `confirmPassword` igual via `.refine`, só no cliente).

### Excluir conta

- O card diz: "Seus dados pessoais são apagados e você sai de todas as peladas. Nas sessões passadas você aparece como Ex-jogador. Não dá para desfazer."
- `useDeletionBlockers` carrega `GET /users/me/deletion-blockers`. Com pendências, lista cada uma com link e o botão fica desabilitado:
  - `CREATOR`: "Você criou **Pelada do Fut**: transfira ou exclua a pelada" → `/pelada/:id/settings#perigo`
  - `SOLE_FINANCE_MANAGER`: "Você é o único responsável financeiro de **X**: designe outro" → `/pelada/:id/financeiro` (aba Configurações)
- Sem pendências: `AlertDialog` com campo de senha (react-hook-form + `deleteAccountSchema`), "Excluir minha conta" (`variant="destructive"`). Sucesso: `logout()`, toast "Conta excluída", `navigate("/", { replace: true })`. Um 409 (pendência criada nesse meio-tempo) recarrega as pendências e mostra a mensagem no root do diálogo.

### "Ex-jogador" na tela

- `types/user.ts` `PublicUser` e `types/daily.ts` `PlayerDTO` ganham `isDeleted: boolean`.
- `PlayerAvatar` recebe `deleted` (ou lê do objeto): fundo `bg-avatar-fallback`, sem iniciais coloridas.
- Onde um nome de jogador vira link para `/profile/:id` (sessão finalizada, chat, notificações, histórico), sem link quando `isDeleted`.

## Backend

### Trocar senha — `POST /api/v1/users/me/password`

- Body `ChangePasswordRequestDTO {currentPassword @NotBlank, newPassword @NotBlank @Size(min = 8)}`.
- Senha atual errada → 400 `{errors: {currentPassword: "Senha atual incorreta"}}` (mesmo formato da validação, para cair no campo).
- Sucesso: novo hash BCrypt, `tokenVersion++`, responde 200 `AuthResponseDTO {token, user}` com token já na versão nova. As outras sessões recebem 401 na próxima requisição; o STOMP aberto sobrevive até reconectar (gap conhecido do `tokenVersion`).
- `AuthRateLimitFilter`: 10/min por IP também nesta rota.
- Em `UserService` (`changePassword`); o caller vem do token, a rota não tem `{id}`.

### Pendências — `GET /api/v1/users/me/deletion-blockers`

- `List<DeletionBlockerDTO {type: CREATOR | SOLE_FINANCE_MANAGER, peladaId, peladaName}>` (enum Java `DeletionBlockerType`).
- Duas consultas: peladas onde é criador; peladas onde é o único responsável (`finance_managers` agrupado com `count = 1`). A segunda vem de `FinancePreservation` (o domínio de finanças continua dono das suas tabelas).

### Excluir conta — `POST /api/v1/users/me/delete`

- Body `DeleteAccountRequestDTO {password @NotBlank}`. POST pelo mesmo motivo de `/players/remove` (DELETE com corpo).
- Senha errada → 400 no campo `password`. Pendências → 409 com a primeira pendência na mensagem ("Transfira ou exclua a Pelada do Fut antes de excluir a conta"), recalculadas dentro da transação.
- Uma transação, `AccountDeletionService` em `domain/user` (orquestra; cada domínio faz a sua parte). Trava as linhas das peladas do usuário (ordem por id, como as rotas de membros) **antes** de checar pendências.

| Alvo | Ação |
|---|---|
| `users` | Linha mantida. `email = "deleted-{id}@invalid"`, `password` = hash BCrypt de um valor aleatório, `username = "Ex-jogador"`, `image`, `backgroundImage`, `position` = null, `tokenVersion++`, `deleted_at = now` |
| Peladas | Sai de todas como membro e admin pelo mesmo caminho da remoção de membro (`PeladaService`: estrelas, mensalista, presença e times de sessões `SCHEDULED`/`CONFIRMED`), sem notificar `REMOVED_FROM_PELADA` e sem gravar `PeladaLinkBlock` |
| Finanças | `FinancePreservation`: deixa de ser responsável; `FinancePerson.name = "Ex-jogador"` nas peladas dele. Cobranças, recebimentos, avisos e auditoria ficam |
| Convites | Pendentes recebidos → `CANCELED`; links criados por ele → revogados; `pelada_link_blocks` dele apagados |
| Apagados | `notifications` recebidas, `notification_preferences`, `password_reset_tokens`, `user_tours`, `daily_attendance_bumps` em que é o bumpado |
| Mantidos | `player_match_stats`, `user_daily_stats`, `team_players` de sessões passadas, `daily_award_*`, `rankings`, `stats`, `messages`, notificações de outros em que é `actor` |
| Arquivos | Foto e capa: `deleteImageAfterCommit` |

Depois: 204. Os tokens dele morrem pelo `tokenVersion` (e o login com o e-mail antigo não acha usuário).

### Contas excluídas nas leituras

- `GET /users/{id}`, `/stats`, `/stats/timeline`, `/stats/matches`, `/peladas` de uma conta excluída → 404.
- `GET /users/search` ignora `deleted_at IS NOT NULL`.
- `PublicUserDTO` e `DailyDetailDTO.PlayerDTO` ganham `isDeleted` (de `deletedAt != null`). Atualizar os snapshots de contrato (`-Dcontract.update=true`) e os tipos do cliente na mesma mudança.
- E-mails: nenhum vai para `@invalid` (ele não recebe mais notificações porque não é membro de nada, e o `ScheduledNotificationJob` só pega membros).

### Migration

`V17__add_user_deleted_at.sql`: `ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;`. Aditiva, não destrutiva. **V16 já é de `feat/legal-pages`** (`terms_version`, `terms_accepted_at`): esta feature entra depois dela e a anonimização mantém esses dois campos (registro do aceite). `User.deletedAt` (`Instant`, `@Column(name = "deleted_at")`).

## Testes

- **Senha:** senha atual errada (400 no campo), nova curta (400), sucesso devolve token que funciona e o antigo recebe 401, rate limit 429.
- **Pendências:** criador → `CREATOR`; único responsável → `SOLE_FINANCE_MANAGER`; responsável com outro → nada.
- **Exclusão:** senha errada 400; criador 409; único responsável 409; sucesso → login com o e-mail antigo 401, `GET /users/{id}` 404, busca não acha, e-mail reusável no cadastro, ranking da pelada sem ele e com os mesmos números para os outros, saldo do caixa igual, sessão finalizada mostra `isDeleted: true` e "Ex-jogador", imagem apagada só depois do commit, rollback não muda nada.
- **N+1:** `QueryCounter`: `reads` da exclusão igual para um usuário em 1 e em 5 peladas.
- `FlywayMigrationTest` / `MigrationDataTest` verdes com a V17.
- **Frontend:** `changePasswordSchema` e `useTheme` (preference → classe e storage) em Vitest; visual regression de `/settings` (com e sem pendências, diálogo de exclusão) nos quatro projetos; o fixture de `/settings/notifications` vira o de `/settings`.

## Documentação

FRONTEND.md: rota `/settings` no lugar de `/settings/notifications`, redirect, `UserMenu`, pasta `account-settings`, `useTheme`, antipatterns (sai `EditProfileModal`), `isDeleted`. BACKEND.md: rotas `/users/me/*`, matriz de permissões (self), `User.deletedAt`, V17, regras de exclusão, rate limit.
