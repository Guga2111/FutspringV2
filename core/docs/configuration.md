# Configuration

> Environment variables, runtime settings, secrets and Redis. Part of the [backend guide](README.md).

## Rules

1. Every credential comes from the environment; nothing secret in a tracked file.
2. A property the tests need is repeated in `src/test/resources/application.properties` (it replaces the main file).
3. A new variable reaches a compose container only when listed under `environment`.
4. The dev seed runs only with the `dev` profile.

## Configuration & environment

There is a single `src/main/resources/application.properties`; there are no `application-<profile>` files.

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://futspring-postgres-dev:5432/futspringdb` |
| `DB_USERNAME`, `DB_PASSWORD` | DB credentials | — (required) |
| `JWT_SECRET` | HS256 key; **must be ≥ 32 bytes** (`Keys.hmacShaKeyFor`) | — (required) |
| `JWT_EXPIRATION_MS` | access token TTL | `604800000` (7 days) |
| `AUTH_COOKIE_SECURE` | `Secure` attribute of the refresh cookie (`app.auth.cookie-secure`); `false` only in `docker-compose.dev.yml` (plain http) | `true` |
| `DDL_AUTO` | `spring.jpa.hibernate.ddl-auto`; leave unset (the schema is owned by Flyway) | `validate` |
| `ALLOWED_ORIGINS` | allowed browser origins, comma-separated; used by HTTP CORS (Spring Security `http.cors`) and the STOMP endpoint (`app.cors.allowed-origins`, `CorsConfig`) | `http://localhost:5173` |
| `APP_URL` | frontend base URL (`app.frontend-url`), used to build links sent by e-mail | `http://localhost:5173` |
| `API_URL` | public base URL of this API (`app.api-url`), for the one-click `List-Unsubscribe` header the mail provider calls; production `https://futspring.luisgosampaio.com/api` | `http://localhost:8080` |
| `RESEND_API_KEY` | Resend API key (`app.mail.resend-api-key`); empty means no e-mail is sent | — |
| `MAIL_FROM` | sender (`app.mail.from`), must belong to the domain verified in Resend (`futspring.luisgosampaio.com`) | `Futspring <no-reply@futspring.luisgosampaio.com>` |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | the VAPID P-256 pair in raw base64url (`app.push.vapid-public-key` / `-private-key`; public 87 characters, private 43), see [Web Push](notifications.md#web-push). Empty private key = push off; a half-set or mismatched pair fails the startup | — |
| `VAPID_SUBJECT` | contact the push services require (`app.push.vapid-subject`) | `mailto:contato@futspring.luisgosampaio.com` |
| `BILLING_PIX_KEY` | the Pix key the platform is paid with (`app.billing.pix-key`, `BillingPixConfig`): a phone (DDD + number; `+55` is added), an e-mail or a random key. Empty = no Pix on the creator's screen ("Fale com o suporte"); any other format fails the startup | — |
| `BILLING_PIX_NAME` | full name of who receives, shown on screen; required with the key | — |
| `BILLING_PIX_MERCHANT_NAME` | the name inside the BR Code (≤ 25 after removing accents, upper-cased); longer fails the startup, never truncated | `BILLING_PIX_NAME` |
| `BILLING_PIX_CITY` | the city inside the BR Code (≤ 15, same rules); required with the key | — |
| `SPRING_PROFILES_ACTIVE` | `dev` runs the seed (`docker-compose.dev.yml`), `prod` in production | — |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | console log format (`logging.structured.format.console`): `ecs` JSON by default; empty = Boot's plain text, set by `docker-compose.dev.yml` (locally: `LOGGING_STRUCTURED_FORMAT_CONSOLE= ./mvnw spring-boot:run`) | `ecs` |
| `API_DOCS_ENABLED` | serves the OpenAPI document (`/v3/api-docs`) and Swagger UI (`/swagger-ui.html`) and makes them public (`springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled`); `true` in `docker-compose.dev.yml`, unset in production (the web app is the only client) | `false` |
| `REDIS_HOST`, `REDIS_PORT` | Redis server (`spring.data.redis.host` / `port`); production: the compose service `futspring-redis` (set by `docker-compose.prod.yml`, not by `.env`) | `localhost`, `6379` |
| `REDIS_PASSWORD` | Redis password (`spring.data.redis.password`), also the container's `requirepass` | — (empty) |

Locally, `docker-compose.dev.yml` runs Redis (`futspring-redis-dev`, fixed dev password, no published port) next to Postgres and passes `RESEND_API_KEY`, `MAIL_FROM`, `APP_URL`, `API_URL`, `ALLOWED_ORIGINS` (default `http://localhost:5173`) and the `VAPID_*` keys from `core/.env` (gitignored, next to the compose file). Compose only uses `.env` to fill `${…}` in the file, so a new variable reaches the container only when it is listed under `environment`.

**Testing the installed app and Web Push on a phone (dev).** A phone needs real HTTPS (iOS also only pushes to the app installed from that URL), so `localhost` doesn't work there. One account-less Cloudflare tunnel (`brew install cloudflared`) serves the app, and the preview server proxies `/api` (stripping it, as nginx does) and `/ws` to the API, so the page and the API share an origin like production. Two tunnels would not work: `trycloudflare.com` is on the Public Suffix List, so two tunnels are two sites and the `SameSite=Strict` refresh cookie would never be sent (every reload would log out); `SameSite=None` wouldn't help on an iPhone either, since Safari blocks cross-site cookies.

1. `cloudflared tunnel --url http://localhost:5173` in its own terminal; note the URL.
2. In `core/.env`: the `VAPID_*` pair (`npx web-push generate-vapid-keys`; a dev-only pair, never the production one), `ALLOWED_ORIGINS=https://<tunnel>` (comma-separate `http://localhost:5173` to keep using it on the computer) and `API_URL=https://<tunnel>/api` (the refresh cookie's path follows it); restart the compose stack.
3. In `client/` (stop `bun run dev`: it has no service worker and Vite blocks the tunnel's host): `VITE_API_URL=https://<tunnel>/api bun run build`, then `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS=.trycloudflare.com bun run preview --port 5173 --strictPort`.
4. Open the tunnel on the phone and on the computer (an origin missing from `ALLOWED_ORIGINS` gets 403), install, turn notifications on and trigger events from another account (the actor is never notified).

A quick tunnel gets a new URL on every restart: repeat steps 2–3 and reinstall the app on the iPhone (an installed app is bound to its URL). Remove `ALLOWED_ORIGINS` and `API_URL` from `core/.env` afterwards.

- `spring.threads.virtual.enabled=true`: Tomcat handles each HTTP request on a virtual thread. Boot's `applicationTaskExecutor` (`SimpleAsyncTaskExecutor`) runs `@Async` on virtual threads without a concurrency limit. `spring.task.execution.mode=force` is required because STOMP declares `Executor` beans that otherwise suppress this auto-configuration; do not expose the notification e-mail executor as a bean.
- `TaskSchedulingConfig` imports Boot 4.1's `DefaultTaskSchedulerConfiguration` **after** `TaskSchedulingAutoConfiguration`: STOMP's `messageBrokerTaskScheduler` otherwise suppresses the default scheduler. `@Scheduled` resolves the named `taskScheduler` (`SimpleAsyncTaskScheduler`) and runs cron/one-shot jobs on virtual threads; the STOMP/SockJS scheduler remains separate. Fixed-delay jobs run on the scheduler thread; current production jobs use cron.
- Hikari: `spring.datasource.hikari.maximum-pool-size=10`, `spring.datasource.hikari.connection-timeout=5000` (ms), repeated in test config. Database access stays bounded by ten connections; waiting longer than five seconds for a connection fails the request with the existing 500 response.
- Uploads go to `app.uploads.dir=/app/uploads` (container path; override it when running outside Docker). Multipart limit is 10 MB, but `FileUploadService` enforces 5 MB.
- `server.forward-headers-strategy=native` (+ `server.tomcat.remoteip.remote-ip-header=X-Forwarded-For`, `protocol-header=X-Forwarded-Proto`): Tomcat's `RemoteIpValve` makes `getRemoteAddr()` the client IP (rate limiting). It reads `X-Forwarded-For` right to left and stops at the first address that isn't a trusted proxy, so a value the client injects at the left is ignored. `internal-proxies` keeps Boot's default (loopback, 10/8, 172.16/12, 192.168/16): nginx reaches the container through Docker's published port, so the API sees the Docker network gateway (`172.25.0.1` today), not `127.0.0.1`. The API is only published on `127.0.0.1`. The test `application.properties` repeats these lines.
- Hibernate: `show-sql=false`, `batch_size=50`, ordered inserts/updates; the dialect is detected from the connection (not set). `spring.jpa.open-in-view` is not set, so it is **on** (default).
- Management (Actuator): `management.server.port=9091`, endpoints `health` and `prometheus` only (what the `HEALTHCHECK` and Prometheus read), health details never shown (`show-details=never`: status only, no pool, database or disk), probes on, `add-additional-paths` serves `/livez` and `/readyz` on the main port too, every metric tagged `application=futspring`, percentile histograms for `http.server.requests`.
- `DataInitializer` (`@Profile("dev")`) seeds an empty database with 20 users (password `senha123`; admin `leal@futspring.com`), the pelada "Pelada do Fut", 7 weekly finished dailies and 1 scheduled daily for today. Each finished daily gets teams (4 teams of 5, or 3 teams / 15 confirmed players in two of the weeks so histories differ), 6 matches with random results from a fixed seed (`new Random(2026)`), and is closed through `DailyResultsService.submitResults` + `finalizeDaily`, so match stats, league table, `UserDailyStats`, `Ranking`, `Stats` and awards follow the real rules.
  It only runs with the `dev` profile, never in tests or production.
- Test config (`src/test/resources/application.properties`, which replaces the main file in tests, so any property tests need is repeated there): H2 PostgreSQL mode, `create-drop`, Flyway disabled, fixed test JWT secret, 1 h TTL, uploads in a temp dir, the same management settings on a random port.
- **Secrets:** all credentials come from the environment (`.env`, `.env.example` documents the keys). Never write a password, DB URL with credentials, or JWT secret into a tracked file, script, compose file or doc.

### Redis

- Store of the refresh-token sessions (`RefreshSessionService`, see [Tokens](security.md#tokens-sdomainauthjwtservicejava); spec `docs/history/2026-11-11-launch-hardening-spec.md`): keys `rt:*` and `sess:*`, nothing else.
- **Container:** `redis:7.4.12-alpine` (fixed version) in `docker-compose.prod.yml` (`futspring_redis`) and `docker-compose.dev.yml`, started with `--appendonly yes` (a restart replays the AOF, so nobody is logged out), `--requirepass "$REDIS_PASSWORD"` (read from the container's environment), `--maxmemory 64mb` under a `mem_limit: 128m` (room for the AOF rewrite fork) with `noeviction` (a full Redis refuses writes instead of dropping sessions). Data in the named volume `redis_data` (`/data`). No published port, only on the project's `default` network (not `observability`). Healthcheck: `redis-cli ping` with `REDISCLI_AUTH`.
- **The API doesn't depend on it to start or be ready:** no `depends_on`, Lettuce connects on first use, `management.health.redis.enabled=false` (not in `/actuator/health` nor readiness). With Redis down a login still answers its access token (without a refresh cookie) and a refresh answers 503; the access token keeps working. `connect-timeout` and `timeout` are 2 s so that failure is fast. `spring.data.redis.repositories.enabled=false`: only `RedisTemplate`, no Redis repositories.
- `RedisConnectionTest` (Testcontainers, the same image with a password, skipped without Docker): reads and writes, then stops Redis and checks that health and readiness stay `UP`.

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 5 | Secrets in tracked or deployable files (DB password, JWT secret, Supabase URL) | Environment variables / `.env` (gitignored); rotate anything that leaked | `docker-compose.dev.yml` (dev values) |
| 22 | Seeding known-password users whenever the profile isn't `prod` | Seed only with an explicit `dev` profile | — |
