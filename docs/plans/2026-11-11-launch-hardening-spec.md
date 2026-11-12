# Prontidão para lançamento: segurança, sessão e observabilidade

Endurecer o Futspring antes de abrir para usuários reais: fechar os itens de `docs/security-review-2026-11-06.md`, trocar a sessão de 7 dias em `localStorage` por access token curto + refresh token em cookie, e passar a enxergar falhas (logs centralizados, erros do frontend, alertas no celular).

## Escopo

| Frente | O que entra | PRs |
|---|---|---|
| A. Segurança sem mudança de contrato | Itens #1, #2, #3, #4, #6, #8 (só o login), #9, #10 da revisão; o #7 só é documentado | A1–A6 |
| C. Observabilidade | Logs JSON + Loki, captura de erros do frontend, alertas no Telegram | C1–C3 |
| B. Sessão | Redis, refresh token em cookie `HttpOnly`, access de 15 min, CSP | B1–B4 |

**Fora do escopo (risco aceito, registrado aqui):**
- **Backup e restore.** O único dump é o `pg_dump` do `deploy.sh` (quando `PG_DUMP_URL` existe); nenhum restore foi testado e os uploads não têm cópia. Voltar a isso antes de crescer.
- **#7, `/files` público e permanente.** O nome UUID funciona como link secreto; o EXIF/GPS é resolvido no A5. Documentado no BACKEND.md, sem mudança de rota.
- **#8, enumeração no cadastro.** O 409 "Este e-mail já está cadastrado" fica; o rate limit corrigido (A2) limita a varredura. Confirmação de e-mail fica para depois.
- **Detecção de reuso de refresh token** (ver B2).

## Decisões

| Tema | Decisão | Por quê / alternativa descartada |
|---|---|---|
| Sessão | Access JWT de 15 min em memória + refresh token rotativo em cookie `HttpOnly; Secure; SameSite=Strict` | Um XSS deixa de levar uma sessão de 7 dias. Descartado: só CSP + TTL menor |
| Store do refresh | **Redis** (container novo na VPS, AOF ligado) | Escolha do dono. Postgres foi a alternativa recomendada (zero infra nova); o Redis exige persistência para um restart não deslogar todo mundo |
| Vida do refresh | 30 dias deslizantes, teto absoluto de 90 dias por sessão | Quem joga toda semana nunca desloga |
| Reuso | **Sem detecção de reuso**: o refresh anterior continua válido até o próprio TTL | Evita logout acidental com duas abas/PWA + navegador renovando juntos. A rotação fica quase cosmética; a proteção vem de o token estar fora do alcance do JS, do logout por sessão e da revogação em bloco por `tokenVersion` |
| Virada do front (B3) | **Re-login forçado** uma vez | Mais simples que uma rota de troca do token legado |
| IP real | Tomcat `RemoteIpValve` (`forward-headers-strategy=native`, `internal-proxies=127.0.0.1`) | Corrigido e testado no repositório, sem depender do nginx |
| Erros do frontend | Endpoint próprio → log JSON → Loki | Sem terceiro (LGPD), sem SDK no bundle. Descartados: Sentry, GlitchTip |
| Alertas | Grafana alerting → Telegram | Notificação imediata no celular, contact point nativo |
| WebP | Lido via TwelveMonkeys `imageio-webp`, regravado em JPEG/PNG | O contrato de upload do front não muda |

## Frente A — Segurança

### A1. Busca de usuários (#1)
- `UserRepository.searchByUsernameOrEmail`: e-mail só por igualdade (`LOWER(u.email) = :email`), username por `LIKE … ESCAPE '\'`. O escape de `\`, `%`, `_` é feito no `UserService`.
- Deletados continuam fora.
- Front: placeholder/ajuda do `InvitePlayerDialog` passa a dizer "Nome ou e-mail completo".

### A2. Rate limit e IP real (#2, #3)
- `AuthRateLimitFilter`: casar as rotas com `PathPatternRequestMatcher` (caminho decodificado, o mesmo mecanismo do Spring Security), nunca `getRequestURI()` cru. A chave do bucket é `"<rota lógica>:<ip>"` (`login`, `register`, `forgot-password`…), nunca a URI recebida.
- `server.forward-headers-strategy=native`, `server.tomcat.remoteip.internal-proxies=127\.0\.0\.1`, `remote-ip-header=X-Forwarded-For`; o mesmo no `application.properties` de teste.
- `ops/nginx/futspring.conf` versionado: `location /api/` com `proxy_set_header X-Forwarded-For $remote_addr` e `X-Real-IP`, `/ws` com upgrade, os blocos de cache do PWA (substitui `NGINX-PWA.md`, apagado) e `gzip` para JSON/JS/CSS (item #4 da revisão de performance). O deploy do nginx continua manual; o passo fica no BACKEND.md (CI/CD).

### A3. Entradas de autenticação (#8 login, #10)
- Senhas `@Size(min = 8, max = 72)` em `RegisterRequestDTO`, `ResetPasswordRequestDTO`, `ChangePasswordRequestDTO` (nova senha); schemas zod iguais no front.
- E-mail normalizado com `trim().toLowerCase(Locale.ROOT)` em cadastro, login, forgot-password e na busca exata do A1.
- Login com e-mail inexistente compara contra um hash BCrypt fixo (`DUMMY_HASH`), igualando o tempo.
- **Migration V22**: `UPDATE users SET email = LOWER(TRIM(email))` + índice único `uq_users_email_lower ON users (LOWER(email))`. **Pré-condição:** antes de abrir o PR, rodar em produção `SELECT LOWER(TRIM(email)), count(*) FROM users GROUP BY 1 HAVING count(*) > 1` e colar o resultado na descrição. Havendo duplicatas, parar e decidir com o dono antes de seguir.

### A4. Actuator (#9)
- `management.endpoints.web.exposure.include=health,prometheus`, `management.endpoint.health.show-details=never`.
- Conferir que o dashboard Futspring e o `HEALTHCHECK` não dependem de `/metrics`, `/info` ou `/threaddump`.

### A5. Uploads públicos (#6, #7)
- `FileUploadService` decodifica o arquivo e decide o tipo pelo conteúdo; tipo declarado ≠ real → 400. Limite de pixels compartilhado com `FinanceFiles` (extraído para um helper em `domain/file`).
- A imagem é regravada a partir dos pixels (sem EXIF/GPS/ICC): JPEG quando opaca, PNG quando tem alfa. WebP é lido pelo TwelveMonkeys `imageio-webp`. A extensão salva segue o formato regravado.
- Excluir pelada apaga a imagem da pelada e as fotos de campeão das sessões; excluir sessão apaga a foto de campeão (`deleteImageAfterCommit`).
- #7 documentado no BACKEND.md (Uploads): links UUID são públicos e permanentes enquanto o arquivo existir.

### A6. Revogação do chat (#4)
- Registro `sessionId → WebSocketSession` num `WebSocketHandlerDecorator` (em `domain/chat` ou `domain/auth`, conforme a regra de pacotes).
- Listener after-commit de `MembersRemovedEvent`: fecha as sessões STOMP dos removidos (o cliente reconecta e não consegue mais assinar `/topic/pelada/{id}`).
- Mudança de `tokenVersion` (reset, troca de senha, exclusão de conta): um evento after-commit fecha todas as sessões do usuário.
- Remove a parte do chat de "Known gaps" no BACKEND.md.

## Frente C — Observabilidade

### C1. Logs estruturados + Loki
- API: `logging.structured.format.console=ecs` (nativo do Boot); campos extras `service=futspring-api`.
- Auditoria dos `log.*`: nenhum e-mail, token, senha ou corpo de requisição em log (ex.: `NoopEmailSender` loga o destinatário hoje → passa a logar o id do usuário ou nada).
- `ops/observability`: Loki (armazenamento em disco, retenção 14 dias, `mem_limit`) + Grafana Alloy coletando os logs dos containers pelo `docker.sock` montado **somente leitura**, com label `service`. Datasource Loki provisionado no Grafana. Loki sem porta publicada.

### C2. Erros do frontend
- Backend, domínio novo `domain/telemetry` (plano): `POST /api/v1/client-errors` com `ClientErrorRequestDTO {message ≤ 500, stack ≤ 4000, path ≤ 200, release ≤ 40, userAgent ≤ 300}`; 204; sem banco, logado em WARN com `event=client_error`.
  - Público: em `permitAll` e no `shouldNotFilter` do `JwtAuthFilter` (um erro com a sessão vencida também precisa chegar).
  - Rate limit 20/min por IP no `AuthRateLimitFilter`; corpo > 8 KB → 413.
  - Sem userId, sem query string.
- Front: `api/clientErrors.ts` + `lib/errorReporting.ts` (`reportError(error, source)`), ligado em `main.tsx` (`window.onerror`, `unhandledrejection`) e no `ErrorBoundary`.
  - `path` = `location.pathname` (sem query nem hash: o reset de senha leva token na query); query/hash também removidos de URLs dentro do stack.
  - `release` = `__APP_VERSION__` (git sha curto via `define` do Vite).
  - Deduplicação por mensagem+stack e no máximo 10 envios por carregamento; falha no envio é ignorada.

### C3. Alertas no Telegram
- `ops/observability/grafana/provisioning/alerting/`: contact point Telegram (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` como secrets do `deploy-observability.yml`) e as regras:

| Regra | Condição |
|---|---|
| API fora | `up{job="futspring"} == 0` por 2 min |
| Erros 5xx | 5xx / total > 1% por 5 min (com mínimo de tráfego) |
| Pool do banco | `hikaricp_connections_pending > 0` por 5 min |
| Disco | uso do disco da VPS > 85% |
| Memória da JVM | heap > 90% por 10 min |
| Container reiniciando | restarts do container da API em 15 min > 0 |
| E-mail | `futspring_email_dropped_total` ou `futspring_email_sent_total{result="failed"}` aumentou em 15 min |
| Web Push | `futspring_webpush_sent_total{result="failed"}` > 10 em 15 min |
| Erros do front | > 20 linhas `event=client_error` em 10 min (Loki) |

- README de `ops/observability` explica como criar o bot e obter o chat id.

## Frente B — Sessão

### B1. Redis
- `core/docker-compose.prod.yml` e `docker-compose.dev.yml`: `redis:7-alpine` (versão fixa), `--appendonly yes`, `--requirepass ${REDIS_PASSWORD}`, volume persistente, `mem_limit`, sem porta publicada, só na rede da API.
- `spring-boot-starter-data-redis`; `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (secret no GitHub, no `.env.example` e na tabela do BACKEND.md); `deploy.sh` espera o Redis ficar `healthy`.
- O Redis **não** entra no readiness: fora do ar, o refresh falha mas o access atual continua valendo.
- Testes com Testcontainers Redis (pulados sem Docker, como os de Postgres).
- Nenhuma mudança de comportamento ainda.

### B2. API de refresh
**Modelo no Redis:**
- `rt:{sha256(token)}` → `{userId, sessionId, tokenVersion}`, TTL = min(30 dias, teto da sessão).
- `sess:{sessionId}` → `{userId, createdAt, absoluteExpiresAt, lastUsedAt}`, TTL de 30 dias renovado a cada refresh, nunca além de `absoluteExpiresAt` (criação + 90 dias).
- Um refresh vale quando `rt` e `sess` existem e o `tokenVersion` guardado é igual ao de `users` (um SELECT indexado). Reset de senha, troca de senha e exclusão de conta já incrementam `tokenVersion`, o que invalida todas as sessões sem índice por usuário.
- Rotação: cada refresh cria um `rt` novo; o anterior expira sozinho (sem detecção de reuso, ver Decisões).

**Cookie:** `futspring_refresh`, `HttpOnly; Secure; SameSite=Strict`, `Path` = caminho de `API_URL` + `/api/v1/auth` (produção: `/api/api/v1/auth`, porque o nginx tira o `/api`), `Max-Age` igual ao TTL. `app.auth.cookie-secure` (padrão `true`; `false` só no compose de dev). 5173 e 8080 em `localhost` são o mesmo *site*, então `Strict` funciona em dev. CORS com `allowCredentials(true)`.

**Rotas:**

| Método | Caminho | Comportamento |
|---|---|---|
| POST | `/auth/login`, `/auth/register` | como hoje + cria a sessão e grava o cookie |
| POST | `/users/me/password` | como hoje (novo `tokenVersion`) + cria uma sessão nova e grava o cookie |
| POST | `/auth/refresh` | lê o cookie; válido → `AuthResponseDTO {token, user}` + cookie rotacionado; inválido → 401 e limpa o cookie. Exige `Origin` em `ALLOWED_ORIGINS` (senão 403). Bucket próprio de 30/min por IP |
| POST | `/auth/logout` | apaga a `sess` do cookie e limpa o cookie; idempotente, 204 |

- **Nesta etapa o access continua com 7 dias** (`JWT_EXPIRATION_MS` intocado): o front atual segue funcionando igual.
- Logout de uma sessão deixa o access dela válido por até 15 min (depois do B3); documentado.
- Métrica `futspring.session.refresh{result=ok|invalid|error}` e regra de alerta "refresh com erro > 0 em 5 min" (Redis fora) adicionada ao provisionamento do C3.

### B3. Cliente
- `AuthContext`: token só em memória; `futspring_token` deixa de ser lido/gravado e é apagado no boot (re-login forçado uma vez). `futspring_user` continua em cache para pintar o shell.
- Boot: `refreshSession()`; enquanto responde, `PrivateRoute` mostra skeleton em vez de checar o `localStorage`. Refresh 401 → `/auth?returnTo=…`.
- Interceptor de resposta: 401 fora de `/auth/*` → um refresh *single-flight* por aba → repete a requisição uma vez; falhou → logout + `/auth?expired=1&returnTo=…` (comportamento atual).
- `RealtimeProvider`: `beforeConnect` lê o token atual (cada reconexão usa um token válido).
- `logout()`: desinscreve o push com o token (como hoje) e chama `POST /auth/logout`.
- `api/auth.ts`: `refreshSession`, `logoutSession`; `apiClient` com `withCredentials` só nas rotas `/auth/*`.
- **Depois do merge no `main`:** `JWT_EXPIRATION_MS=900000` (15 min) no GitHub (variável). Um PWA desatualizado leva um 401, vai ao login e funciona depois.
- e2e: fixture de `/auth/refresh` no `mockApi.ts`; login nos testes deixa de depender de `futspring_token`.

### B4. Content-Security-Policy
- No `ops/nginx/futspring.conf`, para o app: `default-src 'self'`; `script-src 'self' 'sha256-…'` (hash do script inline de tema do `index.html`, conferido no build); `style-src 'self' 'unsafe-inline'` (recharts/Radix usam `style` inline); `img-src 'self' data: blob:`; `connect-src 'self'` (+ `wss:` do próprio host); `worker-src 'self'`; `frame-ancestors 'none'`; `base-uri 'self'`; `form-action 'self'`; `object-src 'none'`.
- Primeiro como `Content-Security-Policy-Report-Only` por alguns dias (violações aparecem no console; opcionalmente via `report-to` para o endpoint do C2), depois a política valendo.
- Um teste de build (`vitest`) recalcula o hash do script inline e falha se diferir do nginx.

## Testes

| PR | Testes obrigatórios |
|---|---|
| A1 | `q=%%%` e `q=___` retornam vazio; trecho de e-mail não casa; e-mail exato (qualquer caixa) casa; deletado nunca aparece |
| A2 | Servidor real (`RANDOM_PORT` + `RestClient`): 11 logins em `/api/v1/auth/%6Cogin` → 429; `X-Forwarded-For` variando não abre bucket novo; rotas não limitadas seguem livres |
| A3 | Senha de 73 bytes → 400 (não 500); login com `Ana@X.com ` encontra `ana@x.com`; cadastro de e-mail que só difere por caixa → 409; `MigrationDataTest` (V22); `FlywayMigrationTest` verde |
| A4 | `ActuatorExposureTest`: só `health` e `prometheus` na porta de gestão; health sem detalhes |
| A5 | Texto com `image/png` → 400; JPEG com GPS → salvo sem EXIF; PNG com alfa continua PNG; WebP aceito e salvo como JPEG/PNG; excluir pelada/sessão apaga os arquivos depois do commit |
| A6 | Removido não recebe a mensagem seguinte do tópico; reset de senha fecha as sessões do usuário; rollback não fecha nada |
| C1 | Log em JSON com os campos ECS (teste de configuração); nenhum `log.*` com e-mail (revisão no PR) |
| C2 | Controller: 204, 400 de validação, 413, 429, aceita token vencido; `errorReporting.test.ts`: query/hash removidos, deduplicação, limite de 10 |
| C3 | Provisionamento validado subindo o stack local (`docker compose up` em `ops/observability`), alerta de teste chegando no Telegram |
| B1 | Contexto sobe com Redis (Testcontainers); readiness continua `UP` com o Redis parado |
| B2 | Login grava cookie com os atributos certos; refresh rotaciona e devolve token válido; refresh após logout, após reset de senha e após exclusão de conta → 401; teto de 90 dias com `Clock.fixed`; `Origin` estranho → 403; 429; contract snapshots atualizados |
| B3 | `vitest` do single-flight (dois 401 simultâneos → um refresh); visual regression verde com a fixture nova |
| B4 | Teste do hash do script inline; app navegável sem violação em Report-Only (verificação manual registrada no PR) |

## Plano de PRs

Treze PRs pequenos, cada um revisável sozinho, com seus testes e a parte do BACKEND.md / FRONTEND.md que mudou (como pede o CLAUDE.md). Itens corrigidos saem de "Known gaps" e da coluna "Found in"; o `docs/security-review-2026-11-06.md` ganha o status de cada item.

- **Branches:** integração `feat/launch-hardening`, saindo de `dev`. Cada PR sai dela e volta contra ela (`--base feat/launch-hardening`), nunca contra `main`.
- **Ordem:** A1 → A6, depois C1 → C3, depois B1 → B4. Dentro da frente A os PRs são independentes e podem andar em paralelo; C3 depende de C1 (regra do Loki); B2 depende de B1; B3 de B2; B4 é independente mas fecha a frente.
- **Para o `main`:** a frente A e a C podem subir (via `dev`) assim que prontas. B2 pode subir sozinho (access ainda de 7 dias). B3 sobe junto com a troca de `JWT_EXPIRATION_MS`. B4 sobe primeiro em Report-Only.

Para planejar um PR, rode o prompt dele numa sessão limpa (`/clear` antes). Ao terminar, o `/plan-task` marca o `[x]` e anota as decisões abaixo do item.

### [x] PR A1 — Busca de usuários (`fix/user-search`)
- **Entrega:** e-mail por igualdade, escape de curingas no username, texto do `InvitePlayerDialog`.
- **Docs:** BACKEND.md (`GET /users/search`), FRONTEND.md se o texto do diálogo for descrito.
- **Decisões:**
  - `searchUsers` saiu do `PeladaService` e foi para o `UserService` (o spec já o colocava lá; a busca é do domínio de usuário). Testes unitários movidos para `UserServiceTest`.
  - A query compara `LOWER(u.email) = :email` (o `LOWER` fica na coluna até a V22 do A3 normalizar os e-mails gravados); o serviço manda `q` com `trim` e `toLowerCase(Locale.ROOT)`. Username com `LIKE :pattern ESCAPE '\'`, padrão montado no serviço.
  - `UserRepository.searchForPlatform` (painel do admin da plataforma) tem o mesmo `LIKE` sem escape, mas só responde ao operador (404 para os outros) e não é oráculo: ficou fora.
  - Sem mudança de contrato (rota, parâmetro, DTO e 400 iguais); o diálogo diz "Nome ou e-mail completo". Nenhum snapshot visual cobre o diálogo.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR A1 — Busca de usuários"
```

### [x] PR A2 — Rate limit e IP real (`fix/rate-limit-ip`)
- **Entrega:** matcher decodificado com chave lógica, `RemoteIpValve`, `ops/nginx/futspring.conf` (proxy, PWA, gzip), remoção do `NGINX-PWA.md`.
- **Docs:** BACKEND.md (Rate limiting, Configuration, CI/CD → nginx).
- **Decisões:**
  - **`internal-proxies` fica no padrão do Boot** (loopback + 10/8, 172.16/12, 192.168/16), não `127\.0\.0\.1` como dizia a seção A2/Decisões. O nginx chega ao container pela porta publicada do Docker, então a API vê o gateway da rede do compose (`172.25.0.1` na VPS hoje), não 127.0.0.1; com o regex do spec todo mundo cairia num bucket só. Risco aceito: um container nosso nas mesmas redes poderia forjar o IP.
  - #3 confirmado na VPS: o `location /api/` usava `$proxy_add_x_forwarded_for` (acrescenta ao valor do cliente). O `ops/nginx/futspring.conf` é cópia do arquivo da VPS (2026-11-11) com `X-Forwarded-For $remote_addr`, `X-Forwarded-Proto`, `proxy_pass http://127.0.0.1:8081` (o Docker só publica em IPv4; `localhost` pode tentar `::1`) e `gzip_types` (o `gzip on` do `nginx.conf` só cobria `text/html`). Linhas do Certbot intactas. **Aplicar à mão na VPS** (passos no cabeçalho do arquivo); o valve já protege mesmo com o nginx antigo, porque lê da direita para a esquerda.
  - A tag `route` de `futspring.ratelimit.rejected` passou a ser o nome lógico (`login`, `invite-preview`…); o painel do Grafana agrupa por `route` e não mudou.
  - `JwtAuthFilter.shouldNotFilter` também compara o `getRequestURI()` cru, mas falha fechado (uma variante codificada só passa pela checagem do JWT): ficou fora.
  - `AuthRateLimitFilterTest` usa o `HttpClient` do JDK com `URI.create` (o `RestClient` recodifica o `%` de templates).

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR A2 — Rate limit e IP real"
```

### [x] PR A3 — Entradas de autenticação (`fix/auth-inputs`)
- **Entrega:** senha 8–72 nos dois lados, e-mail normalizado, BCrypt dummy, V22 (com o resultado da consulta de duplicatas na descrição).
- **Docs:** BACKEND.md (Domain model `User`, migrations, Auth), FRONTEND.md (schemas).
- **Decisões:**
  - **Consulta em produção (2026-11-11, 56 contas):** 0 duplicatas por `LOWER(TRIM(email))` e 0 e-mails fora do padrão. A V22 só cria o índice em prod e nenhum JWT antigo quebra (o `sub` já estava normalizado).
  - **O máximo é em bytes, não em caracteres:** o BCrypt do Spring Security 7.1 lança exceção acima de 72 bytes UTF-8 tanto no `encode` quanto no `matches`. Por isso o `@Size(max = 72)` do spec virou a constraint `@BcryptLength` (`domain/auth`, validador aninhado), sem o `max` no `@Size` (as duas juntas dariam mensagens diferentes a cada vez para 73 caracteres). Uma mensagem só: "A senha é longa demais (até 72 caracteres; letras com acento contam em dobro)". O `@Size(min = 8)` continua.
  - `@BcryptLength` vale também para as senhas que só são comparadas (login, senha atual da troca, exclusão de conta): sem ela, um `matches` de 73 bytes dava 500.
  - **E-mail normalizado no construtor compacto** dos records de cadastro, login e forgot-password (`User.normalizeEmail`), antes da validação; assim `"Ana@X.com "` passa no `@Email` e os serviços não mudam. A busca do A1 usa o mesmo helper; a query `LOWER(u.email) = :email` ficou e agora usa o índice funcional da V22.
  - `DUMMY_HASH` é um literal BCrypt de custo 10 (o padrão do `BCryptPasswordEncoder` do `SecurityConfig`).
  - Front: `passwordField` / `newPasswordField` em `schemas/auth.ts` (limite com `TextEncoder`), reusados em `schemas/user.ts`. O e-mail não é rebaixado no front.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR A3 — Entradas de autenticação"
```

### [x] PR A4 — Actuator (`fix/actuator`)
- **Entrega:** exposição mínima, health sem detalhes.
- **Docs:** BACKEND.md (Configuration, Observability).
- **Decisões:**
  - Conferido: o Prometheus lê só `/actuator/prometheus`, o `HEALTHCHECK` só o status HTTP de `/actuator/health/readiness`, o smoke test do deploy `/readyz` e os dashboards do Grafana consultam o Prometheus. Nada dependia de `metrics`, `info` ou `threaddump` em produção.
  - **O load test dependia** (`threaddump` na sonda STOMP, `metrics/jvm.threads.live` e `hikaricp.connections.pending` na amostragem): o `ops/loadtest/run.sh` reabre `metrics,threaddump` no próprio container com `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE`. A imagem não muda; a mesma variável serve para reabrir em produção temporariamente (documentado no BACKEND.md).
  - Um endpoint não exposto na porta 9091 deixa de ser endpoint do Actuator e cai na cadeia da API, que responde 401 sem token (não 404). O teste afirma "não 2xx", como já fazia para a porta principal.
  - Autenticação básica no `actuatorFilterChain` (opcional na revisão) ficou fora.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR A4 — Actuator"
```

### [x] PR A5 — Uploads públicos (`fix/upload-sanitize`)
- **Entrega:** decodificação e regravação sem metadados, TwelveMonkeys para WebP, helper de validação compartilhado com o financeiro, limpeza de arquivos na exclusão, #7 documentado.
- **Docs:** BACKEND.md (Uploads, Stack).
- **Decisões:**
  - **Orientação EXIF aplicada no servidor** (decisão do dono): sem ela, a foto vertical do celular sairia de lado ao perder o EXIF. Além do `imageio-webp`, entraram `imageio-jpeg` (lê também JPEG CMYK) e `contrib` (`EXIFUtilities.findImageOrientation` + `TIFFUtilities.applyOrientation`), TwelveMonkeys 3.15.3.
  - O helper é `domain/file/ImageDecoder` (classe estática, sem bean): formato pelo conteúdo, teto de 20 Mpx lido do cabeçalho antes de decodificar, formatos permitidos por quem chama, tipo declarado ≠ real → 400, orientação aplicada. `FinanceFiles.verify` usa o mesmo helper com JPEG/PNG e **continua gravando os bytes originais** (arquivos privados, com hash); só os uploads públicos são regravados.
  - Alfa decidido pelo `ColorModel.hasAlpha()` (PNG com canal alfa continua PNG mesmo se todos os pixels forem opacos); JPEG com qualidade 0.9. O ICC é descartado (fotos Display P3 podem perder um pouco de saturação).
  - A limpeza na exclusão já existia (`DailyService.deleteDailyData`, `PeladaService.deletePelada`); o PR só acrescentou os testes.
  - **Arquivos já gravados não foram reprocessados** (decisão do dono): mantêm o EXIF que tinham; `.webp` antigos continuam servidos.
  - Sem mudança no front nem no contrato (JPEG/PNG/WebP, 5 MB); um arquivo que antes passava só pelo `Content-Type` agora recebe 400.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR A5 — Uploads públicos"
```

### [x] PR A6 — Revogação do chat (`fix/stomp-revocation`)
- **Entrega:** registro de sessões, fechamento na remoção de membro e na mudança de `tokenVersion`.
- **Docs:** BACKEND.md (WebSocket, Known gaps).
- **Decisões:**
  - **Na remoção de membro a conexão não é fechada** (decisão do dono, diferente da seção A6): o servidor manda um `UNSUBSCRIBE` ao broker para as inscrições do removido em `/topic/pelada/{id}`. O Spring responde a um SUBSCRIBE recusado com ERROR e fecha a conexão, e o `RealtimeProvider` zera as tentativas a cada CONNECT. Fechando, um removido ainda na página da pelada reconectaria em loop a cada 1–4 s e ficaria sem o sino. Assim o sino continua, o `REMOVED_FROM_PELADA` chega ao vivo e o envio ao chat já dá 403 no `ChatService`. Sem mudança no front.
  - **Mudança de `tokenVersion`** fecha todas as conexões do usuário (`POLICY_VIOLATION`), como no spec. O evento é `TokensRevokedEvent(email)` em `domain/user` (ao lado de `User`, dono do `tokenVersion`; dois dos três publicadores estão lá). É publicado no reset, na troca de senha e na exclusão; a exclusão publica **antes** de trocar o e-mail, porque as sessões foram abertas com o original. A troca de senha fecha também a conexão de quem trocou, que reconecta com o token novo.
  - Uma classe só, `domain/auth/StompSessions` (auth passa de 9 para 10 classes): o decorator guarda `sessionId → WebSocketSession`. Sessões e inscrições do usuário vêm do `SimpUserRegistry` do Spring (chave = e-mail), sem mapa próprio. Os dois listeners são `@TransactionalEventListener`, então um rollback não mexe em nada. `SimpUserRegistry` e `SimpMessagingTemplate` entram por `ObjectProvider` para quebrar o ciclo com `WebSocketConfig`.
  - O `UNSUBSCRIBE` do servidor não atualiza o `DefaultSimpUserRegistry` (ele só acompanha frames do cliente); a entrada velha é inofensiva. Um removido e reconvidado com a página aberta só volta a receber o tópico quando o chat se inscreve de novo (ao abrir a pelada ou reconectar).
  - `StompRevocationTest` usa um cliente SockJS/STOMP real, sem transação de teste, e apaga o que gravou (o H2 é compartilhado entre contextos). Com os listeners desligados, os 4 testes de revogação falham.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR A6 — Revogação do chat"
```

### [x] PR C1 — Logs estruturados e Loki (`feat/logs-loki`)
- **Entrega:** logs ECS em JSON, auditoria de PII nos logs, Loki + Alloy + datasource.
- **Docs:** BACKEND.md (Observability), `ops/observability/README.md`.
- **Decisões:**
  - **ECS no `application.properties`** (`logging.structured.format.console=ecs`, `logging.structured.ecs.service.name=futspring-api`: o "service" do spec é o `service.name` do ECS). Dev em texto (decisão do dono): `docker-compose.dev.yml` passa `LOGGING_STRUCTURED_FORMAT_CONSOLE: ""`; valor vazio volta ao padrão do Boot (coberto pelo teste). Os testes continuam em texto (o `application.properties` de teste não define o formato). O ECS do Boot 4.1 é aninhado (`log.level`, `service.name`, `error.stack_trace` na mesma linha).
  - `StructuredLoggingTest` sobe uma `SpringApplication` mínima não-web com as linhas `logging.*` do arquivo principal e lê a saída com `OutputCaptureExtension`. Depois de cada caso, o logging volta a texto e é marcado como não inicializado (`cleanUp`), senão o próximo contexto da JVM não se reconfigura.
  - **Socket proxy em vez de `docker.sock:ro`** (decisão do dono, diferente da seção C1): `:ro` não limita a API do Docker. `tecnativa/docker-socket-proxy:v0.5.0` com `CONTAINERS=1`, `NETWORKS=1` (o `discovery.docker` lê `/networks` para os labels; sem ele, 403 e nenhum alvo), `POST=0`, numa rede `internal` só com o Alloy. Conferido localmente: `POST /containers/create` e `GET /images/json` → 403.
  - Versões: `grafana/loki:3.7.9`, `grafana/alloy:v1.21.3`. `mem_limit` Loki 384m, Alloy 192m, proxy 64m (medidos parados: ~80, ~100 e ~25 MiB).
  - Alloy coleta **todos** os containers do host (o stack é compartilhado), labels `service` (serviço do compose), `project`, `container` e `level` (de `log.level` quando a linha é JSON). O resto do JSON é lido com `| json` no LogQL.
  - O deploy reinicia `loki` e `alloy` junto com o `prometheus` (leem a config só no start) e o smoke test exige o `/ready` do Loki (lido de dentro do container do Prometheus).
  - **Auditoria dos 37 `log.*`:** só o `NoopEmailSender` logava PII (destinatário; o assunto também leva nomes) → agora uma mensagem fixa. A mensagem do PgJDBC numa violação de unicidade traz `Detail: Key (lower(email))=(…)`, logada pelo Hibernate e pelo `GlobalExceptionHandler`: resolvido na origem com `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false` (o nome da constraint continua). Os demais logam ids, nomes UUID e contadores. `RestClientException` do Resend (corpo de erro do provedor) fica, sem o destinatário. A regra "sem e-mail, token, senha, chave Pix ou corpo em log" entrou nas Conventions do BACKEND.md.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR C1 — Logs estruturados e Loki"
```

### [x] PR C2 — Erros do frontend (`feat/client-errors`)
- **Entrega:** `POST /client-errors`, `errorReporting` no front, `__APP_VERSION__`.
- **Docs:** BACKEND.md (API reference, Security, Rate limiting, Package layout), FRONTEND.md (api, lib, ErrorBoundary). Política de privacidade só se o texto atual não cobrir registros técnicos de erro.
- **Decisões:**
  - **Política de privacidade sem mudança** (decisão do dono): "registros do servidor" em Dados técnicos já cobre os registros de erro; sem bump de `LEGAL_VERSION` / `CURRENT_VERSION`.
  - **Campo `source` a mais no DTO** (`WINDOW | PROMISE | BOUNDARY`, enum `ClientErrorSource`, opcional): o `reportError(error, source)` do spec já recebia a origem e ela ajuda a ler o log. `message` e `path` obrigatórios, o resto opcional.
  - **A linha de log:** WARN de `ClientErrorService`, mensagem "Erro no cliente em {path}: {message}" e key-value pairs do SLF4J (`event=client_error`, `client_path`, `client_source`, `client_release`, `client_user_agent`, `client_stack`), que o ECS do Boot 4.1 escreve como campos da linha (coberto no `StructuredLoggingTest`). Consulta do C3: `{service="futspring-api"} | json | event="client_error"`. Sem userId, sem IP.
  - **413 lendo o corpo**, não só o `Content-Length`: `ClientErrorBodyLimitFilter` (servlet filter, depois da cadeia de segurança, então o 429 vem antes) lê até 8 KB + 1 byte e repassa os bytes; um corpo chunked também fica limitado.
  - O servidor também corta o `path` no primeiro `?`/`#` (defesa contra cliente antigo ou forjado); o front já manda só o `pathname` e tira query/hash das URLs da mensagem e do stack, mantendo `:linha:coluna`.
  - Ruído ignorado no front: "ResizeObserver loop…" e "Script error." sem stack (scripts de outra origem, extensões).
  - `__APP_VERSION__` = `git rev-parse --short HEAD` no `vite.config.ts`, `"dev"` sem git (Docker do visual test), `"test"` no Vitest; declarado em `src/globals.d.ts`.
  - Envio pelo `apiClient` (o token vai junto e é ignorado); `sendBeacon` descartado porque fugiria de `src/api`. Nenhuma fixture nova no e2e: um erro real numa tela vira um POST sem fixture e derruba o visual test.
  - O `<button>` cru do `ErrorBoundary` virou `Button` (antipattern #2, arquivo já tocado).

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR C2 — Erros do frontend"
```

### [x] PR C3 — Alertas no Telegram (`feat/grafana-alerts`)
- **Entrega:** contact point e regras provisionadas, secrets no workflow.
- **Docs:** `ops/observability/README.md`, BACKEND.md (Observability, tabela de secrets).
- **Decisões:**
  - Três arquivos em `grafana/provisioning/alerting/`: `contact-points.yml`, `policies.yml` (tudo → Telegram, agrupado por regra, repete a cada 4 h) e `rules.yml` (grupo `futspring`, pasta Futspring, avaliação a cada 1 min). Cada regra: query A instantânea + threshold C (`__expr__`), uid fixo, `summary` estático em pt-BR. Sem template de mensagem próprio: o padrão do Telegram já traz valor, labels e `summary`. Nada de `$` nas anotações (o provisioning expande `$VARS`).
  - **O job é `futspring-api`**, não `futspring` como na tabela acima: `up{job="futspring-api"}`. As métricas da API filtram por `application="futspring"`, as do cAdvisor por `name="futspring_api"` (como o dashboard).
  - **Chat id escrito no arquivo pelo workflow**, não por variável de ambiente: o Grafana relê como YAML o valor expandido de `$VAR`, então um id numérico vira número e o contact point é recusado (aspas não resolvem; testado no Grafana 13.0.2). O `contact-points.yml` versionado tem `__TELEGRAM_CHAT_ID__`; o workflow valida `^-?[0-9]+$` e troca com `sed` antes do rsync. O token vai pelo `.env` (`TELEGRAM_BOT_TOKEN`, sempre string por ter `:`). Os dois secrets são obrigatórios: sem eles o deploy falha cedo (um Telegram sem token derruba o Grafana). **Criar o bot e os secrets antes do merge no `main`.**
  - O deploy reinicia também o `grafana` (provisioning de alerting só é lido no start) e o smoke test exige todas as regras de `rules.yml` carregadas (`/api/v1/provisioning/alert-rules`, senha lida do `.env` da VPS).
  - **Container reiniciando ignora deploys:** `changes(container_start_time_seconds{name="futspring_api"}[15m]) > 0`. Um `restart` mantém o id do container (mesma série muda); o deploy recria (série nova, `id` diferente). **Não deu para verificar localmente** (o cAdvisor no OrbStack não enxerga containers) nem na VPS (leitura de produção negada nesta sessão): conferir depois de um deploy no Explore; se disparar a cada deploy, aceitar ou trocar a regra.
  - **Disco** por tipo de filesystem (`ext4|xfs|btrfs`, fora `/boot`) em vez de `mountpoint="/"`: sem o `/` a regra ficaria vazia e calada. Duração 10 min (o spec não dava).
  - 5xx com `rate[5m]` e mínimo de 0,2 req/s; heap dividido só pelos pools com máximo > 0 (G1 informa −1 em Eden/Survivor). "Sem dados" só alerta em "API fora"; erro de consulta (Prometheus/Loki fora) notifica como erro.
  - Validado com o stack local: Grafana sobe, 9 regras carregadas e avaliando sem erro, "API fora" dispara sem a API, 21 linhas `event=client_error` enviadas ao Loki disparam "Erros do front" e o Grafana tenta entregar ao Telegram (falha esperada com token falso). **A entrega real no celular fica para o dono** (Contact points → telegram → Test depois do deploy).

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR C3 — Alertas no Telegram"
```

### [x] PR B1 — Redis (`feat/session-redis`)
- **Entrega:** Redis em dev/prod com AOF e senha, starter, configuração, healthcheck no deploy, Testcontainers.
- **Docs:** BACKEND.md (Stack, Configuration, CI/CD, secrets).
- **Decisões:**
  - Imagem `redis:7.4.12-alpine` (último patch da 7.4 no Docker Hub em 2026-11-12). Flags: `--appendonly yes`, `--requirepass "$REDIS_PASSWORD"` lido do ambiente do container (`sh -c`, `$$` escapa a interpolação do compose), `--maxmemory 64mb` com `noeviction` (cheio recusa escrita em vez de derrubar sessões) sob `mem_limit: 128m` (folga para o fork da reescrita do AOF). Volume nomeado `redis_data`. Healthcheck `redis-cli ping` com `REDISCLI_AUTH` (a senha não vai na linha de comando do `redis-cli`). Conferido localmente: sem senha → `NOAUTH`, AOF gravando em `/data/appendonlydir`, `healthy`.
  - **Sem `depends_on` da API no Redis:** o Redis não é necessário para subir nem para o readiness; um Redis quebrado não impede a API de subir. O `deploy.sh` espera os dois `healthy` (Redis primeiro) com uma função `wait_healthy` e falha se algum não ficar.
  - **`management.health.redis.enabled=false`:** além de fora do readiness (já era assim por padrão), o Redis também fica fora do `/actuator/health` raiz; senão o `ActuatorExposureTest` (que exige `UP` sem Redis) e qualquer leitura do health cairiam com o Redis fora. A queda do Redis será vista pela métrica/alerta de refresh do B2.
  - `spring.data.redis.connect-timeout` e `timeout` de 2 s (o refresh falha rápido no B2); `spring.data.redis.repositories.enabled=false` (só `RedisTemplate`; evita o Spring Data varrer os repositórios JPA de novo).
  - **`REDIS_PASSWORD` obrigatório no `deploy.yml`** (falha cedo, como `JWT_SECRET`) e no compose de prod (`${REDIS_PASSWORD:?}`). `REDIS_HOST` vem fixo do compose (`futspring-redis`), não do `.env`. Senha em hex (`openssl rand -hex 32`): o compose interpola `$` nos valores do `.env`. **Criar o secret `REDIS_PASSWORD` antes do merge no `main`.**
  - Teste com `GenericContainer` + `@DynamicPropertySource` (como o `FlywayMigrationTest`), sem `spring-boot-testcontainers`. Com o health do Redis ligado o teste falha (conferido).

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR B1 — Redis"
```

### [x] PR B2 — API de refresh (`feat/session-refresh-api`)
- **Entrega:** sessões no Redis, cookie, `/auth/refresh`, `/auth/logout`, cookie no login/cadastro/troca de senha, métrica e alerta.
- **Fora:** o TTL do access não muda.
- **Docs:** BACKEND.md (Tokens, Security, CORS, Rate limiting, API reference, Known gaps), FRONTEND.md (tabela de API).
- **Decisões:**
  - Novo subpacote `domain/auth/session`: `RefreshSessionService` (Redis via `StringRedisTemplate`, valores JSON com o `JsonMapper`), `SessionCookie` (monta o `Set-Cookie`, `Path` derivado do caminho de `API_URL`) e `IssuedSession` (access + refresh). `AuthService.login/register` e `UserService.changePassword` passam a devolver `IssuedSession`; o controller só chama `sessionCookie.respond(...)`. O `ResetTokens` do reset de senha virou `auth/SecretTokens` (público) e gera/hasheia também o refresh token.
  - **Redis fora no login/cadastro/troca de senha: responde o access sem cookie** (não falha). No B2 o front ainda vive do access de 7 dias, então derrubar o login por causa do Redis seria regressão; no B3 isso significa só "relogar depois que o access vencer". Conta em `futspring.session.open{result=error}` (métrica a mais que o spec: no B2 ninguém chama refresh, então só a métrica de refresh nunca dispararia o alerta).
  - **Refresh com Redis fora → 503 mantendo o cookie** (não 401): o cliente do B3 distingue queda de sessão encerrada. Logout com Redis fora → 204 e cookie limpo; a sessão expira sozinha.
  - O `Origin` é exigido pelo próprio serviço (ausente ou fora de `ALLOWED_ORIGINS` → 403, sem limpar o cookie): em produção app e API são a mesma origem, então o `CorsFilter` não barraria um `Origin` igual ao host. O CORS já tinha `allowCredentials(true)`.
  - `/auth/refresh` e `/auth/logout` entram em `SecurityConfig.SESSION_COOKIE_PATHS`: o `JwtAuthFilter` ignora o `Authorization` (o cliente chama refresh justamente com o access vencido). Logout sem rate limit (só apaga).
  - O teto de 90 dias é conferido também no relógio da aplicação (`absoluteExpiresAt` guardado na sessão), além do TTL do Redis; o `findById` do usuário confere `tokenVersion` e conta excluída.
  - Alerta "Sessões (Redis)": `futspring_session_refresh_total{result="error"}` + `futspring_session_open_total{result="error"}` cresceram em 5 min (regra 10 do `rules.yml`; o smoke test conta as regras pelo arquivo).
  - Testes: `RefreshSessionTest` com Redis real (Testcontainers, pulado sem Docker) e `@MockitoBean Clock` para o teto; `RefreshWithoutRedisTest` sempre roda (o `application.properties` de teste aponta o Redis para a porta 1, recusada na hora, em vez de `localhost:6379`, para não depender de um Redis local). Contract snapshots inalterados: a resposta continua `AuthResponseDTO`, o refresh token só vai no cookie.
  - **Antes do merge no `main`:** nada novo de secret; `AUTH_COOKIE_SECURE` só existe no compose de dev.

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR B2 — API de refresh"
```

### [ ] PR B3 — Sessão no cliente (`feat/session-refresh-client`)
- **Entrega:** token em memória, refresh no boot e no 401, STOMP com token atual, logout no servidor, e2e.
- **Depois do merge no `main`:** `JWT_EXPIRATION_MS=900000`.
- **Docs:** FRONTEND.md (API client and auth, Environment, Visual regression), BACKEND.md (Tokens).

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR B3 — Sessão no cliente"
```

### [ ] PR B4 — Content-Security-Policy (`feat/csp`)
- **Entrega:** política no nginx (Report-Only, depois valendo), teste do hash do script inline.
- **Docs:** BACKEND.md (CI/CD → nginx), FRONTEND.md (Styling / index.html).

```
/plan-task docs/plans/2026-11-11-launch-hardening-spec.md "PR B4 — Content-Security-Policy"
```

## Riscos

- **Apps de loja (`MOBILE_APPS`):** se forem um wrapper nativo (Capacitor e afins), o cookie `HttpOnly` precisa de outra estratégia; um TWA funciona como o navegador.
- **Redis é um ponto novo de falha:** fora do ar, ninguém consegue renovar a sessão (o access atual vale até 15 min). Coberto pelo alerta do B2; sem AOF, um restart deslogaria todos.
- **Instância única:** rate limit em memória, STOMP, fila de e-mail e `@Scheduled` continuam assumindo uma instância da API (o Redis do B1 não muda isso).
- **Backup** fora do escopo (ver Escopo).
