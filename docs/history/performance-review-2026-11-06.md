# Revisão de performance do backend — 2026-11-06

> Historical investigation, not a current optimization checklist. Image resizing,
> private photo authorization and client-side image fetching have since changed.
> In particular, the original public-file caching and direct-nginx suggestions
> must not bypass current authorization or the no-store policy. Revalidate each
> hypothesis against the [Backend](../../core/docs/README.md) and
> [Frontend](../../client/docs/README.md) guides before implementation.

Os 10 problemas de performance mais relevantes do `core/`, encontrados lendo o código (arquivos, configuração JPA, services de daily/pelada/stats, scheduler, auth). Ordem: do maior ganho ao menor. Ficou de fora o que seria exagero para a escala de hoje (ver "Fora do top 10").

Contexto: as **leituras** já estão em ordem (fetch join, `@EntityGraph`, consultas agrupadas) e os N+1 dos **comandos** foram corrigidos nos PRs #68–70 (see the current batch-command rules in [Backend](../../core/docs/performance.md)). O que sobra é mais configuração e alguns pontos isolados.

Impacto: **Alto** (o usuário percebe em toda tela), **Médio** (segura conexão/lock ou cresce com o tempo), **Baixo** (desperdício pequeno).

| # | Problema | Impacto | Status |
|---|----------|---------|--------|
| 1 | Imagens servidas sem cache | Alto | Confirmado |
| 2 | Imagens guardadas no tamanho original (até 5 MB) | Alto | Confirmado |
| 3 | `open-in-view` ligado segura a conexão até o fim da resposta | Médio | Confirmado |
| 4 | Respostas JSON sem gzip | Médio | A confirmar (nginx) |
| 5 | Lista de sessões da pelada sem paginação | Médio / Baixo | Confirmado |
| 6 | Exclusão de pelada com consultas por sessão sob lock | Médio / Baixo | Confirmado |
| 7 | Datas de Puskás regravadas em todo rebuild | Baixo | Confirmado |
| 8 | Scheduler abre uma transação por pelada a cada hora | Baixo | Confirmado |
| 9 | Duas buscas do usuário por requisição | Baixo | Confirmado |
| 10 | Busca de usuários com `LIKE '%q%'` | Baixo | Confirmado |

---

## 1. Imagens servidas sem cache

**Impacto:** Alto

**Onde:** `core/src/main/java/com/futspring/backend/domain/file/FileController.java` (`serveFile`).

**Problema:** a resposta só tem `Content-Type` e `Content-Disposition`, sem `Cache-Control` nem `ETag`. Avatares, banners de pelada e fotos de campeão são baixados de novo a cada tela (sidebar, membros, ranking, galeria). Cada download passa pelo nginx, pelo Tomcat e pela leitura do disco, e pode ter até 5 MB (ver #2).

**Solução:**
1. Os nomes são UUID e um arquivo nunca é reescrito (substituir gera nome novo), então dá para usar cache permanente: `CacheControl.maxAge(365, DAYS).cachePrivate().immutable()`. É `private` para combinar com o item 7 da revisão de segurança (`docs/history/security-review-2026-11-06.md`).
2. Opcional: o nginx servir `/api/files/` direto do volume (`alias /opt/futspring-uploads/`), tirando o Tomcat do caminho. Exige versionar o trecho do nginx.
3. Teste: `GET /files/{nome}` devolve o `Cache-Control` esperado.

---

## 2. Imagens guardadas no tamanho original (até 5 MB)

**Impacto:** Alto (principalmente no celular)

**Onde:** `core/.../domain/file/FileUploadService.java:54` (`Files.copy` do arquivo enviado, sem processamento).

**Problema:** a foto sai do celular com 3–5 MB e é servida assim para um avatar de 40 px, um card da galeria ou o banner. Numa lista de membros ou na galeria de sessões, isso dá dezenas de MB por tela.

**Solução (uma das duas):**
- **Client (mais simples, sem dependência):** antes do upload, redimensionar e comprimir no navegador com `canvas` (ex.: lado maior 1600 px para fotos de campeão/banner, 400 px para avatar, WebP ~0.8). Fica num helper em `client/src/lib/` usado pelos formulários de upload.
- **Servidor:** redimensionar no `FileUploadService`. O `ImageIO` não lê WebP, então precisaria de uma biblioteca (TwelveMonkeys). Combina com o item 6 da revisão de segurança (regravar os pixels também remove o EXIF), e aí vale fazer os dois juntos.

O limite de 5 MB do servidor continua igual.

---

## 3. `open-in-view` ligado segura a conexão até o fim da resposta

**Impacto:** Médio

**Onde:** `core/src/main/resources/application.properties` (a propriedade não está definida, então vale o padrão `true`; o Spring só avisa no log ao subir).

**Problema:** com OSIV, a sessão do Hibernate fica aberta da primeira consulta até a resposta ser escrita. Com a configuração do Spring (`prepareConnection`), a conexão JDBC fica presa nesse intervalo, inclusive durante a serialização do JSON e a escrita na rede. O pool tem **10 conexões** e timeout de **5 s** (`hikari.connection-timeout`), e as requisições rodam em virtual threads: sob carga, a fila de conexões é o gargalo (veja a nota do teste de carga PR3 no `core/docs/README.md`). Também esconde lazy loads fora do service, contra a regra "monte os DTOs dentro da transação".

**Solução:**
1. `spring.jpa.open-in-view=false` no `application.properties` principal e no de teste.
2. Rodar `./mvnw test` e corrigir qualquer `LazyInitializationException` (o lugar certo é o service ou um fetch join, não o controller).
3. Observar no Grafana `hikaricp.connections.pending` / acquire time antes e depois.

---

## 4. Respostas JSON sem gzip

**Impacto:** Médio

**Onde:** nginx da VPS (fora do repositório) ou `server.compression.*` (não configurado).

**Problema:** o detalhe da sessão, o financeiro (overview + detalhe a cada comando), o ranking e o histórico de perfil são JSONs grandes e repetitivos, que comprimem 70–85%. Não há compressão no Spring, e não dá para ver no repositório se o nginx comprime.

**Como confirmar:** `curl -sI -H 'Accept-Encoding: gzip' -H 'Authorization: Bearer …' https://futspring.luisgosampaio.com/api/api/v1/peladas/my` e procurar `Content-Encoding: gzip`.

**Solução:** de preferência no nginx (`gzip on; gzip_types application/json; gzip_min_length 1024;`). Se não der, `server.compression.enabled=true` + `server.compression.mime-types=application/json` + `server.compression.min-response-size=1KB`. Versionar o trecho do nginx junto com o do item 1 e o da revisão de segurança (#3).

---

## 5. Lista de sessões da pelada sem paginação

**Impacto:** Médio / Baixo (cresce com o tempo)

**Onde:** `core/.../domain/daily/DailyService.java:91` (`getDailiesForPelada`), `GET /api/v1/peladas/{id}/dailies`.

**Problema:** devolve todas as sessões da pelada desde o início (~52 por ano numa pelada semanal), com contagens e resultado do caller. As consultas são agrupadas (número constante), mas o payload e o trabalho crescem sem limite. É a única lista que cresce com o tempo sem limite, contra a regra de paginação do `core/docs/README.md`. A visão geral, por exemplo, só usa as 3 últimas.

**Solução:** não é urgente. Quando a resposta pesar, aceitar `?from=` (data) ou `?limit=` opcional e usar na visão geral e no card da próxima sessão. A galeria e a lista por mês continuam pedindo o período inteiro, ou passam a pedir pelo filtro de datas que já existe (`DateRangeFilter`). É mudança de contrato nos dois lados, no mesmo PR.

---

## 6. Exclusão de pelada com consultas por sessão sob lock

**Impacto:** Médio / Baixo (operação rara, mas a mais longa do sistema)

**Onde:** `core/.../domain/pelada/PeladaService.java` (`deletePelada`), que chama `DailyService.deleteDailyData` para cada sessão.

**Problema:** cada sessão faz ~10 comandos (resultados, times, presença, bumps, notificações, a própria daily). Uma pelada com 100 sessões dá ~1.000 comandos numa transação que segura o lock da pelada (`findByIdForUpdate`). Já está registrado como pendência no spec de N+1.

**Solução:** versões em lote por pelada dos deletes de `deleteDailyData` (`deleteByDailyIn` / `DELETE … WHERE daily.pelada = :pelada`), mantendo a ordem por causa das FKs. A lista de jogadores afetados vem de uma consulta só (`UserDailyStats` da pelada). Teste com `QueryCounter`: `reads` igual para 2 e 20 sessões.

---

## 7. Datas de Puskás regravadas em todo rebuild

**Impacto:** Baixo

**Onde:** `core/.../domain/stats/AggregateRebuildService.java:83` (`stats.setPuskasDates(new ArrayList<>(…))`).

**Problema:** `puskasDates` é um `@ElementCollection` sem coluna de ordem. Trocar a lista inteira faz o Hibernate apagar todas as linhas de `stats_puskas_dates` do jogador e inserir de novo, para **cada jogador** em todo finalize, edição de resultado e exclusão de sessão, mesmo quando nada mudou.

**Solução:** só mexer quando mudar: `if (!stats.getPuskasDates().equals(novas)) { stats.getPuskasDates().clear(); stats.getPuskasDates().addAll(novas); }`. Um teste do rebuild garante o resultado igual.

---

## 8. Scheduler abre uma transação por pelada a cada hora

**Impacto:** Baixo

**Onde:** `core/.../domain/daily/DailySchedulerService.java` (`autoCreateDailies`).

**Problema:** a cada hora, para toda pelada com criação automática, abre uma transação e faz `findById`, e só depois calcula se o dia está a menos de 24 h. Em 23 de cada 24 execuções (ou mais) a conta dá "não" e a transação foi à toa: 24 × N transações por dia.

**Solução:** fazer a conta de "está na janela?" com a entidade já listada (dia, horário e fuso são campos simples, não lazy) e só abrir a transação e recarregar quando for criar. O `existsByPeladaAndDailyDate` continua dentro da transação. Os testes existentes (`DailySchedulerTransactionTest`) cobrem o comportamento.

---

## 9. Duas buscas do usuário por requisição

**Impacto:** Baixo

**Onde:** `core/.../domain/auth/AccessTokenVerifier.java:35` (`findTokenVersionByEmail`) e `UserAuthenticationHelper.getAuthenticatedUser` (`findByEmail`) em quase todo service.

**Problema:** toda chamada autenticada faz duas consultas por e-mail na tabela `users`. As duas são indexadas e custam pouco, mas acontecem em 100% das requisições.

**Solução:** só vale se ficar simples. Por exemplo, o filtro carrega o `User` (em vez de só o `tokenVersion`) e guarda como atributo da requisição, e o `UserAuthenticationHelper` reaproveita quando existir. Atenção: a entidade viria de outra sessão do Hibernate (detached), então para escrita o service ainda precisa recarregar. Se complicar, deixar como está.

---

## 10. Busca de usuários com `LIKE '%q%'`

**Impacto:** Baixo (hoje)

**Onde:** `core/.../domain/user/UserRepository.java` (`searchByUsernameOrEmail`).

**Problema:** `LOWER(...) LIKE '%q%'` em `username` e `email` não usa índice e varre a tabela inteira. Com centenas de usuários não importa.

**Solução:** nada de performance por agora. A correção de segurança (item 1 da revisão de segurança: e-mail só por igualdade) já reduz a consulta a um `LIKE` só. Quando passar de dezenas de milhares de usuários, índice `pg_trgm` em `LOWER(username)` (migração).

---

## Fora do top 10

- **`GenerationType.IDENTITY` impede insert em lote** (`batch_size=50` não vale). Trocar por `SEQUENCE` exige migração em várias tabelas; os maiores lotes hoje (notificações de `SESSION_CREATED`, participantes do financeiro) têm dezenas de linhas. Exagero agora.
- **Cache de leitura** (Caffeine/Redis) para ranking, perfil e `/peladas/my`: as consultas já são constantes e baratas; a invalidação custaria mais que o ganho.
- **Índice em `notifications.created_at`** para o job de retenção das 04:00: varredura diária de uma tabela pequena, já aceita no `core/docs/README.md`.
- **Várias instâncias da API** (ShedLock, broker externo, rate limit distribuído): uma instância atende com folga (teste de carga PR3).

## O que já está bem

Leituras com fetch join e consultas agrupadas em número constante (`/peladas/my`, detalhe da sessão, ranking, presença, perfil, notificações, financeiro), checagens de permissão com `exists`, comandos em lote sem N+1 com `QueryCounter` nos testes, agregados reconstruídos em lote (`AggregateRebuildService`), virtual threads com pool limitado, e-mails fora da transação com fila e ritmo próprios, chat paginado.

## Ordem sugerida

1. #1 e #4 (configuração, maior ganho percebido; confirmar #4 no nginx antes).
2. #3 (uma propriedade + a suíte de testes).
3. #2 (de preferência junto com o item 6 da revisão de segurança).
4. #7 e #8 (pequenos, isolados).
5. #5 e #6 quando crescerem; #9 e #10 só se ficarem fáceis ou a escala mudar.
