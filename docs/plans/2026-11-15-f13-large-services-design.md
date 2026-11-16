# F-13: dividir services grandes do backend

Aprovado em 2026-11-15. `DailyResultsService` (651 linhas) e `PeladaService` (513) passam da regra de ~300 linhas
do BACKEND.md e concentram várias responsabilidades, o que aumenta o risco de regressão de qualquer mudança.
Escopo: só esses dois. `FinanceCollectionService` (355), `DailyService` (338) e `DailyTeamManagementService` (302)
ficam para quando forem tocados.

## Abordagem

Divisão por sub-domínio (como os daily services): cada service novo é dono de uma responsabilidade e os callers
chamam o service certo. Descartadas: extrair só helpers estáticos (reduz linhas, não responsabilidades) e fachadas
que delegam (camada extra sem ganho).

**Invariante:** refatoração pura. O código é movido literalmente; rotas, DTOs, mensagens, ordem das validações,
locks (`findByIdForUpdate` antes da checagem), `requireWritable`, eventos publicados, `@Transactional` de cada
método público e número de queries ficam iguais. Nenhuma migração.

## `domain/daily/results` (8 → 11 classes)

| Classe | Responsabilidade |
|---|---|
| `DailyResultsService` | `submitResults` (validação + escrita, tabela ao vivo) e `clearResults` |
| `DailyFinalizeService` (novo) | `finalizeDaily`, `uploadChampionImage`, `closeSession` (package-private, também usado por `submitResults` numa sessão FINISHED) |
| `DailyImportService` (novo) | `populateFromMessage` + `distributeStats` |
| `DailyLeagueTable` (novo `@Component`) | calcula e persiste a tabela da sessão (`replace`), e a apaga (`delete`) |

Dependências: `DailyResultsService` → `DailyFinalizeService`, `DailyLeagueTable`; `DailyFinalizeService` →
`DailyLeagueTable`; `DailyImportService` independente. Callers: `DailyController` (finalize, champion-image,
populate), `DataInitializer` (finalize). `DailyResultsServiceTest` continua um arquivo só: o `setUp` monta os
services reais sobre os mesmos mocks.

## `domain/pelada` (raiz 9 → 6, `member/` 4 → 9)

| Classe | Onde | Responsabilidade |
|---|---|---|
| `PeladaService` | raiz | criar, ler, atualizar, excluir, foto |
| `PeladaSummaryAssembler` (novo `@Component`) | raiz | `toSummaries`: o read model agrupado de `/peladas/my` e `/users/{id}/peladas` |
| `PeladaMemberService` (novo) | `member/` | remover (um, vários, conta excluída), estrelas, mensalistas, admins, transferir criador |

Os eventos `MembersRemovedEvent`, `AdminsChangedEvent`, `MensalistasChangedEvent` e `PeladaTransferredEvent`
vão para `member/`, ao lado de quem os publica. Sem ciclo: `member` → `invitation`, e `invitation` não usa `member`.
Callers: `PeladaController` (rotas de membro), `AccountDeletionService`, `StompSessions` e `NotificationService`
(imports dos eventos), testes de integração (injeção). Os testes de membro de `PeladaServiceTest` vão para
`member/PeladaMemberServiceTest`.

## Entrega

Branch de integração `refactor/f13-large-services` (a partir de `dev`) com dois sub-PRs:
`refactor/f13-daily-results` e `refactor/f13-pelada-service`. BACKEND.md atualizado no mesmo change
(layout de pacotes, permissão e referências aos services).

## Validação

`./mvnw test` completo em `core/` em cada sub-PR, incluindo `DomainLayoutTest` (≤ 12 classes, sem ciclos),
`DailyResultsQueryCountTest` e os testes de concorrência quando houver Docker.
