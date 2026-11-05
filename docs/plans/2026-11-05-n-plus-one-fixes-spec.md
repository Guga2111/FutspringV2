# SPEC — Correção de N+1 em comandos (financeiro e resultados)

## Contexto

Uma revisão de performance (2026-11-05) mostrou que as **leituras** estão em ordem: ranking, perfil, detalhe da sessão, `/peladas/my`, convites, notificações e o financeiro usam fetch join, `@EntityGraph` ou consultas agrupadas. Os N+1 estão em três **comandos**. Cada um roda uma ou mais consultas por item, dentro de uma transação que segura um lock de linha:

| # | Comando | Onde | Custo hoje |
|---|---------|------|------------|
| 1 | Adicionar participantes / criação guiada | `FinanceCollectionService.addParticipants` (`:182`) + `person()` (`:276`) | ~4 SELECTs por pessoa, mais os INSERTs. 100 membros + 100 convidados ≈ 1.000 comandos com o lock da pelada |
| 2 | Publicar / encerrar / reabrir / cancelar cobrança | `FinanceCollectionService.transition` (`:117`) | 1 SELECT por cobrança (`existsByChargeIdAndStatus`); no cancelar, mais 2 por cobrança (`reads.paid(c)`) |
| 3 | Lançar / editar resultados | `DailyResultsService.submitResults` (`:69`) | 1 SELECT (`findByIdAndDaily`) e 1 DELETE (`deleteByMatch`) por partida, mais o `findByDaily` final |

Nenhuma regra de negócio muda. Os comandos continuam tudo ou nada, com a mesma revisão, a mesma idempotência e a mesma auditoria. O único efeito visível é uma mensagem de erro melhor no item 1.

## Decisões do usuário

- **Medição:** Hibernate Statistics, só nos testes (sem dependência nova). Prometheus + Grafana continuam disponíveis para olhar produção se for preciso.
- **Branches:** uma branch de integração com um PR por item, como em `feat/notifications`.
- **Mensagem do item 1:** nomear a pessoa: "Ana já tem cobrança nesta lista".
- **Escopo extra:** `FinanceReadService.paid(...)` deixa de carregar avisos (entra no PR das transições).
- **Fora do escopo:** trocar `IDENTITY` por `SEQUENCE` (habilitaria o batch de INSERT, mas exige migração em várias tabelas) e o N+1 da exclusão de pelada (`PeladaService.deletePelada`, operação rara). Os dois ficam registrados em "Riscos e pendências".

## Branches e ordem

```
dev
 └─ feat/perf-n-plus-one                 (integração; PR final → dev)
     ├─ PR A  perf/finance-transitions   (item 2 + paid() + QueryCounter)
     ├─ PR B  perf/finance-participants  (item 1)
     └─ PR C  perf/daily-results         (item 3)
```

- A entra primeiro porque é o menor e cria o `QueryCounter` que B e C usam.
- B e C saem de `feat/perf-n-plus-one` depois que A for integrado. Entre si são independentes (domínios diferentes).
- Cada sub-PR roda `./mvnw test` e passa na CI de backend. Nenhum toca no `client/`, nenhum tem migração.

## Infraestrutura de medição (PR A)

**Config de teste** (`core/src/test/resources/application.properties`, que substitui o arquivo principal nos testes):

```properties
spring.jpa.properties.hibernate.generate_statistics=true
# sem o log "Session Metrics" a cada sessão
logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN
```

Produção não muda: as estatísticas continuam desligadas no `application.properties` principal.

**Helper** `support/QueryCounter.java` (`@Component` de teste, injetado nos testes que estendem `BaseIntegrationTest`):

```java
// Counts what a block sends to the database through Hibernate Statistics (test config only)
public long reads(Runnable block)   // JPQL/derived queries + entity loads + lazy entity/collection fetches
public long statements(Runnable block) // every prepared statement, inserts included
```

- `reads` = `queryExecutionCount + entityLoadCount + entityFetchCount + collectionFetchCount` medidos antes e depois do bloco (`statistics.clear()` não é usado, para não interferir em outros testes).
- Os testes de N+1 afirmam que **`reads` é o mesmo** para um lote pequeno e um grande (ex.: 2 e 20 itens). Não comparam um número absoluto, que quebraria a cada consulta nova legítima.
- INSERTs **não** entram na comparação: com `GenerationType.IDENTITY` o Hibernate manda um INSERT por linha mesmo com `batch_size=50`, e isso está fora do escopo.
- Todo teste chama o comando uma vez antes de medir (aquecimento). Assim, carregamentos únicos de contexto (conta financeira, caller) não distorcem a comparação.

## PR A — Transições da cobrança (item 2) + `paid()`

- [x] Concluído (PR #68)

Decisões:
- O teste de contagem falhou no código antigo: 29 → 47 leituras por transição com 2 → 20 participantes, e 31 → 85 no cancelar. Depois da correção, o número é igual nos dois tamanhos.
- `QueryCounter` fica em `support/` como `@Component`. Os `@SpringBootTest` o encontram pelo component scan, e os `@DataJpaTest` não o carregam.
- O cancelar medido passa por um recebimento anulado (`receive` + `reverse`), então cobre `paid(rows)` e `hasValidReceipts`.

### `FinanceReadService`

- `Map<Long, BigDecimal> paid(List<FinanceCharge> rows)`: **uma** consulta (`movements.findByChargeIdIn`), sem carregar avisos. Lista vazia → mapa vazio, sem consulta.
- `data(rows)` reaproveita `paid(rows)` e continua buscando os avisos recentes. O detalhe e a visão geral continuam com as mesmas duas consultas de hoje.
- `paid(FinanceCharge)` e `remaining(FinanceCharge)` passam a custar 1 consulta em vez de 2, o que beneficia também `charge`, `report`, `receive` e `cancelCharge`.

### `FinanceCollectionService.transition`

- Uma vez, para todas as cobranças da lista: `pending = !ids.isEmpty() && !notices.chargeIdsWithStatus(ids, PENDING).isEmpty()`. O método já existe e é usado por `receiveAll`/`adjustCharges`.
- No `CANCEL`: `var paid = reads.paid(chargeRows)` uma vez, e `require(paid.values().stream().allMatch(v -> v.signum() == 0), …)`. A mensagem continua igual.
- `hasValidReceipts(chargeRows)` já é em lote e não muda.
- Ordem e texto das validações iguais aos atuais.

### Testes (`FinanceServiceTest`)

- Os testes de publicar/encerrar/reabrir/cancelar continuam passando sem alteração.
- Novo: `transitionsReadAConstantNumberOfQueries`. Uma lista com 2 cobranças e outra com 20; publicar, encerrar, reabrir e cancelar cada uma; `reads` igual entre as duas. Também cobre o cancelar com recebimento anulado (o caminho de `paid` e `hasValidReceipts`).
- Novo: "um aviso pendente em qualquer cobrança bloqueia o encerrar". Garante que a troca do `anyMatch` pela consulta em lote manteve a regra.

## PR B — Adicionar participantes (item 1)

- [x] Concluído (PR #69)

Decisões:
- O teste de contagem falhou no código antigo: 33 → 105 leituras com 2 → 20 membros mais convidados. Depois da correção, o número é igual nos dois tamanhos, tanto em participantes quanto na criação guiada.
- A checagem de conflito roda antes de qualquer escrita. No teste, as contagens de pessoas, cobranças, revisão e auditoria não mudam.
- O nome da mensagem é o da pessoa financeira (`FinancePerson.name`), o mesmo que aparece na cobrança.
- Um convidado com nome em branco continua dando 409 "Selecione um membro ou informe um convidado", como acontecia pelo `person()`. Pela API, o `@NotBlank` do DTO já barra esse caso antes.
- O BACKEND.md não citava o texto antigo do 409, então não precisou mudar.

Vale para `POST /lists/{listId}/participants` e para a criação guiada (`POST /lists/create`), que chamam o mesmo `addParticipants`.

### Novo fluxo de `addParticipants`

1. As validações atuais ficam como estão: lista não vazia, sem jogador repetido e `peladas.findMemberIds(id, userIds)` (já é em lote).
2. **Pessoas dos membros em uma consulta:** `people.findByPeladaIdAndUserIdIn(peladaId, userIds)`, um novo método derivado em `FinancePersonRepository`, resolvido num mapa `userId → FinancePerson`.
3. **Nomes dos que ainda não têm pessoa:** `users.findAllById(semPessoa)` uma vez, e depois `people.saveAll(novas)`.
4. **Convidados:** sempre ganham uma pessoa nova, como hoje (nomes iguais nunca se ligam). Vão no mesmo `saveAll`.
5. **Cobrança ativa repetida em uma consulta:** um novo `charges.findActivePersonIds(listId, personIds)` (`SELECT c.personId … WHERE c.listId = :listId AND c.personId IN :ids AND c.canceled = false`), chamado só com as pessoas **já existentes**, porque pessoa recém-criada e convidado não podem ter cobrança. O primeiro conflito, na ordem do pedido, falha o comando inteiro com **"{nome} já tem cobrança nesta lista"** (409, como hoje).
6. As cobranças são montadas como hoje (componentes copiados da lista, `amountDue` = soma, vencimento da lista) e salvas com `charges.saveAll`.

`addParticipants` deixa de chamar `person()`. O `person()` não muda e continua servindo `charge()` / `replace`, que tratam uma pessoa por vez e validam a pertença ali mesmo.

### Contrato

- Requests e responses iguais. O texto do 409 muda de "Um participante já tem cobrança nesta lista" para "{nome} já tem cobrança nesta lista".
- Nenhum teste ou fixture (`client/src`, `client/e2e`, `core/src/test`) compara o texto antigo. O cliente mostra o `message` do servidor, então a nova mensagem aparece sem mudança no front.

### Testes (`FinanceServiceTest`)

- Os testes de criação guiada e de participantes continuam passando: tudo ou nada, replay idempotente, convidados com nomes iguais ficam diferentes, membro que volta recupera a identidade antiga.
- Novo: `addingParticipantsReadsAConstantNumberOfQueries`. Adicionar 2 e depois 20 membros (metade com `FinancePerson` existente, de uma lista anterior) mais convidados; `reads` igual. Também mede o mesmo na criação guiada.
- Novo: o conflito nomeia a pessoa e não grava nada (nem pessoas novas, nem cobranças, nem revisão).

## PR C — Lançar resultados (item 3)

### Novo fluxo de `submitResults`, em duas fases

1. **Carregar:** times com jogadores (`findByDailyWithPlayers`, como hoje) e **as partidas da sessão uma vez** (`matchRepository.findByDaily(daily)`), num mapa `id → Match`.
2. **Validar tudo antes de escrever:** para cada resultado, as mesmas checagens de hoje, na mesma ordem e com os mesmos textos. A diferença é que um `matchId` fora do mapa dá 404 "Partida não encontrada nesta sessão", sem consulta. Nenhuma escrita acontece até todos os resultados serem válidos. Isso mantém o teste "erro de validação não apaga estatísticas" (`DailyResultsServiceTest:170`).
3. **Escrever:**
   - um único `playerMatchStatRepository.deleteByMatchIn(partidasExistentes)`, que cobre as editadas e as removidas, no lugar de um `deleteByMatch` por partida mais o `deleteByMatchIn(removidas)`;
   - salvar as partidas (as novas precisam de `save` para ganhar id; as existentes já estão gerenciadas);
   - um único `playerMatchStatRepository.saveAll` com as estatísticas de todas as partidas;
   - `matchRepository.deleteAll(removidas)`, onde removidas = mapa − enviadas, sem novo `findByDaily`.
4. O restante (tabela ao vivo ou `closeSession` numa sessão finalizada) não muda.

### Testes

- `DailyResultsServiceTest` (Mockito): os stubs de `findByIdAndDaily` viram `findByDaily`; o `verify(...).deleteByMatchIn(List.of(removed))` passa a esperar as partidas existentes. As regras testadas continuam as mesmas.
- `MatchRepository.findByIdAndDaily` fica sem uso em `main`: remover, se nenhum outro chamador aparecer.
- Novo teste de integração (`DailyControllerTest` ou um `DailyResultsQueryCountTest` com `BaseIntegrationTest`): lançar resultados com 2 e depois 8 partidas, numa sessão ao vivo e numa finalizada; `reads` igual.

## Documentação (no PR em que cada parte muda)

- `core/docs/BACKEND.md`, seção **Testing**: um item sobre o `QueryCounter` (Hibernate Statistics só no teste, comparar lotes pequeno × grande, INSERTs fora da comparação por causa do `IDENTITY`) — PR A.
- `core/docs/BACKEND.md`, seção **Performance**: uma linha dizendo que comandos em lote também mantêm um número fixo de leituras e que `IDENTITY` impede o batch de INSERT — PR A.
- Seção do financeiro do BACKEND.md: só se algum texto ali descrever o fluxo alterado (ex.: a mensagem do 409) — PR B.
- `FRONTEND.md` não muda.

## Validação em produção (opcional)

Sem métrica nova. Depois do deploy, comparar no dashboard **Futspring** do Grafana o p95 de `http.server.requests` das rotas `/api/v1/peladas/{peladaId}/finance/lists/create`, `…/lists/{listId}/participants`, `…/lists/{listId}/{operation}` e `/api/v1/dailies/{id}/results`, além de `hikaricp.connections.pending`. Só vale olhar se o uso real tiver listas grandes.

## Riscos e pendências

- **INSERTs continuam um por linha** (`IDENTITY`). Numa criação guiada com 200 pessoas ainda são ~200 INSERTs de pessoa/cobrança, mais os componentes. As leituras deixam de crescer, a escrita não. Se o Grafana mostrar lentidão nessas rotas, o próximo passo é a migração para `SEQUENCE` com `allocationSize`, numa spec própria.
- **Exclusão de pelada** continua com ~10 consultas por sessão (`PeladaService.deletePelada` → `deleteDailyData`). Fica para quando for mexer ali.
- `FinanceNoticeRepository.recentByChargeIds` usa uma subconsulta correlacionada quadrática por cobrança. Hoje não pesa (poucos avisos por cobrança) e fica como está.
