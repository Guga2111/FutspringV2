# Problemas conhecidos — Futspring

Problemas críticos encontrados durante a escrita de `core/docs/BACKEND.md` e `client/docs/FRONTEND.md` (2026-10-03). Todos foram corrigidos; os detalhes ficam abaixo como histórico.

| # | Problema | Severidade | Status |
|---|----------|-----------|--------|
| 1 | Backend não compila (`UserDailyStatsRepository`) | Crítica (bloqueia build/deploy) | Corrigido na branch `lf/user-history-dialog` |
| 2 | Segredos de produção em texto puro no `deploy.sh` | Alta | Corrigido em `fe59429` (`scripts/deploy.sh` versionado, segredos vêm do GitHub / `.env`) |
| 3 | IDOR no envio de resultados (`matchId`) | Alta | Corrigido em `cf31a82` (`findByIdAndDaily`, 404) |
| 4 | Chat via WebSocket sem autorização de inscrição | Alta | Corrigido em `cf31a82` (`JwtChannelInterceptor`) |

Ao corrigir um item, mude o status para "Corrigido" com o commit/PR e remova a entrada correspondente das tabelas de antipatterns / "Known gaps" do `BACKEND.md`.

---

## 1. O backend não compila no commit atual

> **Corrigido** na branch `lf/user-history-dialog`: a query virou text block com `JOIN FETCH uds.daily`, e o endpoint `GET /api/v1/peladas/{id}/members/{userId}/history` checa se quem chama e o jogador-alvo são membros da pelada.

**Onde:** `backend/src/main/java/com/futspring/backend/repository/UserDailyStatsRepository.java:87-89` (método `findHistoryByUserAndPelada`, introduzido no commit `53efc75`).

**Problema:**
- A string da `@Query` quebra linha com aspas simples `"..."`, o que não compila em Java 17. É preciso usar um text block `"""`.
- A JPQL mistura os aliases `usd` e `uds`, então ela também falharia ao subir o contexto do Spring.

```java
@Query("SELECT usd FROM UserDailyStats
 uds WHERE usd.user.id = :userId
  AND uds.daily.pelada.id = :peladaId
   ORDER BY uds.daily.dailyDate DESC")
```

**Impacto:** `./mvnw test`, `./mvnw package` e o build da imagem Docker falham. Nada pode ser publicado enquanto isso não for corrigido.

**Correção sugerida:**

```java
@Query("""
        SELECT uds FROM UserDailyStats uds
        WHERE uds.user.id = :userId
          AND uds.daily.pelada.id = :peladaId
        ORDER BY uds.daily.dailyDate DESC
        """)
List<UserDailyStats> findHistoryByUserAndPelada(@Param("userId") Long userId, @Param("peladaId") Long peladaId);
```

Considere usar `JOIN FETCH uds.daily` se o resultado for acessar a daily, para evitar N+1. O endpoint que vai expor esse histórico precisa checar se quem chama é membro da pelada e se o usuário-alvo também é membro.

---

## 2. Segredos de produção em texto puro no `deploy.sh`

> **Corrigido** em `fe59429`: o script agora é `scripts/deploy.sh`, versionado e sem segredos; os valores vêm dos segredos do GitHub (CD) ou de um `.env` fora do git, e o script recusa um `.env` com `DDL_AUTO`. Se o `deploy.sh` antigo ainda existir em alguma máquina, apague-o e troque a senha do banco e o `JWT_SECRET` (passo 1 abaixo).

**Onde:** `deploy.sh`, na raiz:
- linhas 10-12: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` do Supabase;
- linha 15: `JWT_SECRET`;
- linhas 96-100: os mesmos valores passados ao `docker run`.

**Problema:** as credenciais do banco de produção e o segredo de assinatura dos JWTs estão escritos no script. O arquivo é ignorado pelo git (`.gitignore`: `*.sh`) e o histórico (`git log --all`) mostra que ele nunca foi commitado. Mesmo assim, os segredos ficam expostos a quem tiver acesso à máquina, a backups e a ferramentas que leem o diretório do projeto.

**Impacto:**
- Quem tiver o `JWT_SECRET` consegue forjar tokens válidos para qualquer usuário.
- Quem tiver as credenciais do banco tem acesso total aos dados.

**Correção sugerida:**
1. Gerar credenciais novas: trocar a senha do banco no Supabase e gerar um `JWT_SECRET` novo com pelo menos 32 bytes (`openssl rand -base64 48`). Trocar o segredo invalida todos os tokens atuais, então os usuários terão que fazer login de novo.
2. Fazer o `deploy.sh` ler de um `.env.deploy` fora do git (`set -a; source .env.deploy; set +a`) ou das variáveis do shell, e falhar se alguma estiver faltando (`: "${DB_PASSWORD:?missing}"`).
3. Documentar as chaves necessárias num `.env.deploy.example` sem valores.
4. Aproveitar para revisar o `backend/docker-compose.yml`, que tem credenciais de desenvolvimento commitadas. Elas são só de dev, mas não devem ser reutilizadas em nenhum outro ambiente.

---

## 3. IDOR no envio de resultados (`matchId` não é checado contra a daily)

> **Corrigido** em `cf31a82`: `submitResults` busca a partida com `matchRepository.findByIdAndDaily(matchId, daily)` e responde 404 "Partida não encontrada nesta sessão" sem alterar nada; o corpo é validado (`List<@Valid MatchResultDTO>`), e estatísticas de jogadores fora da partida são rejeitadas. Testes: `DailyResultsServiceTest.submitResults_matchIdFromAnotherDaily_*`.

**Onde:** `backend/src/main/java/com/futspring/backend/service/DailyResultsService.java:67-68` (`submitResults`), rota `POST /api/v1/dailies/{id}/results`.

```java
if (result.getMatchId() != null) {
    match = matchRepository.findById(result.getMatchId()).orElse(null);
}
```

**Problema:** o serviço confere que quem chama é admin da pelada da daily `{id}` e que `team1Id`/`team2Id` pertencem a essa daily. O `matchId` do corpo da requisição, porém, é buscado direto por id, sem verificar se a partida pertence à daily da rota.

**Impacto:** um admin de qualquer pelada (e qualquer usuário pode criar uma pelada e virar admin) consegue enviar o `matchId` de uma partida de **outra** pelada. Com isso ele:
- troca os times e o placar dessa partida;
- apaga os `PlayerMatchStat` dela (linha 128).

Isso corrompe resultados, rankings e estatísticas de terceiros.

**Correção sugerida:**
- Buscar a partida restrita à daily, por exemplo `matchRepository.findByIdAndDaily(result.getMatchId(), daily)`, e responder 400/404 se ela não existir nessa daily, em vez de criar uma partida nova silenciosamente.
- Também ativar `@Valid` no corpo da rota (`DailyController.java:115`, `List<@Valid MatchResultDTO>`), para que o `@Min(0)` dos placares seja aplicado.
- Adicionar testes: um `matchId` de outra daily deve ser rejeitado sem alterar nada, nem a partida nem as estatísticas.

---

## 4. Chat via WebSocket sem autorização de inscrição

> **Corrigido** em `cf31a82`: `CONNECT` exige token válido, `SUBSCRIBE` só em `/topic/pelada/{id}` para membros (e na fila `/user/queue/errors` do próprio usuário), `SEND` exige sessão autenticada, e os erros do `ChatController` voltam ao remetente. Testes: `JwtChannelInterceptorTest`.

**Onde:**
- `backend/src/main/java/com/futspring/backend/websocket/JwtChannelInterceptor.java`
- `backend/src/main/java/com/futspring/backend/controller/ChatController.java:28`
- `backend/src/main/java/com/futspring/backend/config/SecurityConfig.java` (`/ws/**` é `permitAll`)

**Problema:**
- O interceptor só valida o token no frame `CONNECT`, e um `CONNECT` sem token ou com token inválido também é aceito: a sessão só fica sem usuário.
- O `SUBSCRIBE` não passa por nenhuma checagem. Qualquer cliente, autenticado ou não, pode se inscrever em `/topic/pelada/{id}` de qualquer pelada.
- Um `SEND` sem autenticação gera `NullPointerException` em `principal.getName()`.

**Impacto:** qualquer pessoa na internet pode acompanhar ao vivo o chat de qualquer pelada (os ids são sequenciais). É um vazamento de mensagens privadas e dos nomes dos membros.

**Correção sugerida:**
1. No `CONNECT`, rejeitar a conexão (lançar `MessagingException`/`AccessDeniedException`) quando não houver um token válido.
2. No `SUBSCRIBE`, extrair o `peladaId` do destino `/topic/pelada/{id}` e permitir a inscrição só se o usuário da sessão for membro (com uma consulta `exists`). Rejeitar qualquer outro destino que não esteja numa lista de permitidos.
3. No `SEND` (`ChatController`), não depender de `principal` ser não-nulo. O `ChatService` já valida a participação; com o `CONNECT` obrigatório isso fica garantido. Adicionar um `@MessageExceptionHandler` para os erros voltarem ao cliente.
4. Adicionar testes de integração: inscrição sem token deve ser rejeitada; inscrição de quem não é membro deve ser rejeitada; inscrição de membro deve funcionar.
