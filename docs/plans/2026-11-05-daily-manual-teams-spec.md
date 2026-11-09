# SPEC — Times manuais e bloqueio de início sem times (Sessão)

## Status

- [x] Implementado (branch `feat/daily-manual-teams`, backend + frontend + docs)

Decisões tomadas na implementação:

- **Ordem dos erros do `PUT /teams`:** 404 (sessão não existe) vem antes do 403 (não admin), como em todas as rotas do projeto; o restante segue a ordem do spec (travada → repetido → não confirmado → vazio).
- **Lock:** `updateStatus`, `saveTeams`, `disconfirmAttendance` e `adminDisconfirmAttendance` passam a carregar a sessão com `findByIdForUpdate` (antes só as confirmações usavam o lock).
- **Saída do time:** `DailyTeamManagementService.removeFromTeams(daily, user)` reaproveita o `removePlayersFromTeams` que a remoção de membro já usava.
- **`TeamsReadiness`:** a regra de início (409) e a mensagem de time vazio reutilizada pelo salvamento (400).
- **Tipos do cliente:** `sortTeams` / `swapPlayers` em `api/dailies.ts` estavam tipados como `DailyDetail`; o backend devolve `TeamDTO[]`, e o tipo foi corrigido.
- **Dica do início desabilitado:** texto visível ao lado do botão (`aria-describedby`), não um Tooltip (um botão desabilitado não dispara o Tooltip).
- **Pendências no diálogo de início:** usam as mesmas linhas da seção Times ("Branco desfalcado (4 de 5)", "1 confirmado sem time: João"), e o botão vira "Iniciar mesmo assim".
- **Teclado no editor:** o `KeyboardSensor` padrão do dnd-kit (espaço pega, setas movem, espaço solta), com instruções e anúncios em pt-BR.
- **Bug corrigido de passagem:** o input de cor oculto do `TeamCard` (`sr-only` + `w-full` do `Input`) criava rolagem horizontal em sessões abertas com times. Ele agora é `relative` + `size-px`, e o novo fixture visual `daily-confirmed` cobre o caso.

## Contexto

Na tela da sessão (`/daily/:id`) há dois problemas:

1. **Bug:** `PUT /dailies/{id}/status` com `IN_COURSE` só confere a máquina de estados (`DailyStatus.canTransitionTo`). Uma sessão `CONFIRMED` sem nenhum time pode ser iniciada, e a sessão ao vivo fica sem times para lançar resultados.
2. **Funcionalidade:** os times só nascem do sorteio (`POST /dailies/{id}/sort-teams`, que exige exatamente `numberOfTeams × playersPerTeam` confirmados) ou do "Importar mensagem" (`populate`, que leva a sessão direto a `IN_COURSE`). O admin não tem como montar os times à mão antes do início.

Há ainda um problema relacionado: hoje, cancelar a presença **não** tira o jogador do time (`DailyAttendanceService` não toca em `Team`). O time fica com alguém que não vai jogar.

Item de origem: `docs/melhorias-escritas-a-mao.md` — "Admin montar times manualmente. Bloquear iniciar partida sem time."

## Decisões do usuário

- **Abordagem A:** um endpoint atômico que substitui todos os times da sessão. O editor monta um rascunho local e salva de uma vez.
- **Formato livre:** de 2 a 10 times, com tamanhos que podem ser diferentes.
- **Só confirmados** entram em times. **Pode sobrar** confirmado sem time, o que vira pendência e não erro.
- **Quem cancela sai do time.** O time fica desfalcado e o admin repõe com alguém que confirme depois ou refaz os times. Com a lista lotada ninguém novo confirma (regra atual, "Sessão lotada"). O cancelamento abre a vaga.
- **Pendência não bloqueia o início:** ela aparece em Times e no diálogo de "Iniciar sessão", que pede confirmação.
- **Interação:** arrastar e soltar no desktop; no mobile, tocar no jogador e escolher o time num `Drawer`.

## Fluxo de referência

1. Os times estão montados e a lista está lotada (10/10).
2. A Ana (Time 2) cancela a presença no site. Ela sai do Time 2, que fica com 4 de 5, e a lista vai a 9/10.
3. O Bruno confirma na vaga aberta e fica **sem time**.
4. Em Times aparece: "Pendências: Time 2 desfalcado (4 de 5) · 1 confirmado sem time".
5. O admin abre "Editar times", arrasta o Bruno para o Time 2 e salva. Ou usa "Sortear de novo".
6. Se ninguém repuser, o admin pode iniciar assim mesmo. O diálogo avisa: "Time 2 está com 4 de 5. Iniciar mesmo assim?".

## Backend

### 1. Validação para iniciar (`CONFIRMED → IN_COURSE`)

Em `DailyService.updateStatus`, quando `newStatus == IN_COURSE`:

- A sessão é carregada com `DailyRepository.findByIdForUpdate` (mesmo lock da presença), para não disputar com uma confirmação ou cancelamento simultâneos.
- Os times precisam ser **iniciáveis**:
  - pelo menos 2 times;
  - nenhum time vazio;
  - todo jogador de time está confirmado na sessão (esta condição é garantida pela seção 2 e mantida como proteção para dados antigos).
- Se falhar, a resposta é **409** com a mensagem do primeiro problema encontrado:
  - "Monte os times antes de iniciar a sessão" (menos de 2 times);
  - "O Time 3 está vazio";
  - "Ana não está mais confirmada e está no Time 2".
- Confirmados sem time e times desfalcados **não** bloqueiam.

A regra fica num único helper do domínio `daily` (por exemplo `TeamsReadiness.requireStartable(teams, confirmedIds)`), usado também pelo salvamento manual (seção 3).

`SCHEDULED → CONFIRMED` e `→ CANCELED` não mudam. O `populate` também não muda: ele já cria times e presença coerentes.

### 2. Cancelar presença remove do time

Na mesma transação (e sob o mesmo lock) que apaga a `DailyAttendance`, o jogador sai de qualquer time da sessão nestes casos:

- `disconfirmAttendance` (o próprio jogador);
- `adminDisconfirmAttendance` (o admin cancela a presença de alguém);
- o **bump**: o diarista retirado por um mensalista sai do time, e o mensalista entra como "sem time".

O time continua existindo, mesmo que fique vazio. Ninguém é promovido automaticamente.

A remoção de membro (`removeFromUpcomingSessions`) já faz isso e não muda. As sessões travadas (`LOCKED`) não mudam, porque nelas a presença já não pode ser alterada.

Implementação: um método em `DailyTeamManagementService` (por exemplo `removeFromTeams(daily, user)`) chamado pelo `DailyAttendanceService`, sem duplicar a lógica.

### 3. `PUT /dailies/{id}/teams` (times manuais)

| | |
|---|---|
| Auth | admin da pelada da sessão |
| Body | `SaveTeamsRequestDTO {teams: [{name, color, playerIds}]}` |
| Resposta | `List<TeamDTO>` (mesmo formato do sort/swap) |

Validação do DTO (400, mensagens em pt-BR):

- `teams`: de 2 a 10 itens, `@Valid`;
- `name`: `@NotBlank`, `@Size(max = 30)`, como o `UpdateTeamNameRequestDTO`;
- `color`: opcional, `^#[0-9a-fA-F]{6}$` quando presente;
- `playerIds`: lista de ids positivos, que pode ser vazia **na validação do DTO**. A regra do serviço decide, abaixo.

Regras do serviço, nesta ordem e sob `findByIdForUpdate`:

1. Caller admin (403); sessão existe (404); sessão não travada (400, mesma mensagem do `requireUnlocked`).
2. Um jogador aparece no máximo uma vez (400 "Ana aparece em mais de um time").
3. Todo jogador está confirmado na sessão (409 "Ana não está mais confirmada"). Usa 409 porque a presença pode ter mudado enquanto o admin editava.
4. Nenhum time vazio (400 "O Time 3 está vazio").
5. Confirmados fora dos times são permitidos.

Efeito: apaga os times atuais (`clearTeams`) e cria os novos, na ordem enviada. Os ids mudam; antes do início nada guarda id de time (partidas só existem em sessões ao vivo, e a notificação calcula o time do destinatário na leitura).

Publica `TeamsSortedEvent(daily, caller, jogadoresDosTimes)`: mesmo tipo `TEAMS_SORTED`, as mesmas regras de colapso e **sem migration**. O texto passa a ser neutro, porque agora vale para sorteio e montagem:

- app (`utils/notifications.ts`): "Times definidos para {data}: você está no **{time}**";
- e-mail (`NotificationEmailContent`): assunto "Times definidos: {data}", título "Times definidos", corpo "Os times da sessão de {data} da {pelada} foram definidos.".

Não há mudança de schema nem de entidade.

### 4. O que não muda

- `sort-teams` continua exigindo exatamente `numberOfTeams × playersPerTeam` confirmados.
- `teams/swap`, renomear e cor continuam como estão.
- Capacidade, janela de diarista e bump continuam com as mesmas regras.

## Frontend

### 5. Seção Times (`TeamsSection`)

- **Sem times:** ao lado de "Sortear times" entra "Montar manualmente" (outline). Fica habilitado com 2+ confirmados, mesmo que o sorteio esteja desabilitado.
- **Com times:** ao lado de "Sortear de novo" entra "Editar times" (outline).
- **Pendências** (`Alert`, não destrutivo), calculadas por `teamPendencies(daily)` em `utils/teamDraft.ts`:
  - "Time 2 desfalcado (4 de 5)": um time com menos que `playersPerTeam` jogadores;
  - "1 confirmado sem time": lista os nomes, até 5;
  - "Time 3 está vazio": este item bloqueia o início.
- Cada `TeamCard` mostra a contagem ("4/5") e um badge "Desfalcado" quando o time está abaixo de `playersPerTeam`.

### 6. Iniciar sessão (`DailyHeader` + `StatusConfirmDialog`)

- Com menos de 2 times ou um time vazio, "Iniciar sessão" fica desabilitado com uma dica ("Monte os times antes de iniciar").
- Com pendências que não bloqueiam, o diálogo de confirmação lista as pendências e pede "Iniciar mesmo assim".
- Um 409 do servidor vira um toast com a mensagem e um refetch do detalhe.

### 7. Editor "Montar times" (`TeamsEditorSheet`)

O editor fica num `Sheet` aberto pelos dois botões e começa com os times atuais (ou vazio, com `numberOfTeams` times "Time 1…N"). Os confirmados que não estão em time vão para a coluna **Sem time**.

- **Desktop (≥ 768 px):** `Sheet` largo com colunas "Sem time" + uma por time, em grid que rola na horizontal se precisar. Os jogadores (`PlayerAvatar`, nome, estrelas) são arrastados entre colunas com **`@dnd-kit/core`** (nova dependência: o HTML5 drag and drop nativo não funciona com toque nem com teclado). Usa o `KeyboardSensor` e anúncios em pt-BR para leitor de tela. Cada coluna mostra o total de estrelas e a contagem.
- **Mobile (< 768 px, `useIsMobile`):** `Sheet` em tela cheia, com "Sem time" e os times empilhados. Tocar num jogador abre um `Drawer` "Mover Ana para…" com os times (contagem de cada) e "Sem time". Não há arrastar.
- **Comum:**
  - "Adicionar time" (até 10);
  - remover time (os jogadores voltam para "Sem time"; mínimo de 2);
  - renomear (Input, máximo de 30);
  - "Embaralhar sem time": não entra (YAGNI, o sorteio já existe).
- **Rodapé:** pendências ao vivo e "Salvar times" (brand). O botão fica desabilitado com um time vazio ou com menos de 2 times. Também tem "Cancelar".
- **Estado:** react-hook-form + `useFieldArray` sobre `teams`, com `saveTeamsSchema` em `schemas/daily.ts` espelhando o `SaveTeamsRequestDTO`. As operações puras (`moveToTeam`, `addTeam`, `removeTeam`, `teamPendencies`, `draftFromDaily`) ficam em `utils/teamDraft.ts`, com testes.
- **Salvar:** `saveTeams(id, payload)` em `api/dailies.ts`, chamado por `useDailyActions`. No sucesso, o resultado entra com `setDaily`, o toast diz "Times salvos" e o Sheet fecha. Erros vão para `applyServerErrors`. Num 409 (presença mudou), o editor faz refetch e reconcilia o rascunho: novos confirmados vão para "Sem time", quem saiu é removido e o restante fica. O erro aparece no root do formulário.
- Cor do time: o editor mantém a cor atual de cada time (os times novos ficam sem cor). A cor continua sendo trocada no card, como hoje.

## Testes

**Backend (`./mvnw test`):**

- `DailyServiceTest` / controller:
  - iniciar sem times → 409;
  - iniciar com 1 time → 409;
  - iniciar com time vazio → 409;
  - iniciar com desfalque e confirmado sem time → 200.
- `DailyAttendanceServiceTest`: o cancelamento próprio, o cancelamento pelo admin e o bump tiram o jogador do time; o time continua existindo.
- `PUT /teams`:
  - caminho feliz (tamanhos diferentes, sobra);
  - 400 do DTO (1 time, 11 times, nome longo, cor inválida);
  - 400 para jogador repetido e time vazio;
  - 409 para jogador não confirmado;
  - 400 para sessão travada;
  - 403 para quem não é membro e para membro que não é admin;
  - 404;
  - evento `TEAMS_SORTED` publicado.
- `ContractSnapshotTest`: o novo request `SaveTeamsRequestDTO`.
- `NotificationEmailContentTest`: o novo texto.

**Frontend (`bun run lint && bun run build && bun run test`):**

- `utils/teamDraft.test.ts`: mover, adicionar e remover time, pendências (desfalque, sem time, vazio) e rascunho a partir do detalhe.
- `schemas/daily` test do `saveTeamsSchema`.
- Visual: o editor no desktop e no mobile (light e dark), mais a seção Times com pendência. Os fixtures em `e2e/fixtures.ts` ganham uma sessão com desfalque.

## Docs

- `BACKEND.md`:
  - nova linha na tabela de Dailies;
  - matriz de permissões ("Salvar times manualmente: admin");
  - nota em Attendance ("cancelar remove do time");
  - regra de início na máquina de estados.
- `FRONTEND.md`:
  - rota `/daily/:id` (editor, pendências, bloqueio do início);
  - `api/dailies.ts`;
  - `useDailyActions`;
  - `utils/teamDraft`;
  - `@dnd-kit/core` no Stack.

## Fora do escopo

- Lista de espera ou promoção automática quando alguém cancela.
- Arrastar no mobile.
- Remover o swap atual (o editor o cobre, mas a remoção fica para depois).
- Histórico de quem saiu do time.
