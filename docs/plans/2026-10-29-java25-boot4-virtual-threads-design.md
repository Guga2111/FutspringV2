# Java 25, Spring Boot 4, virtual threads and observability

Date: 2026-10-29 · Status: approved; PR 1 implemented (on `dev`, not deployed) · Delivery: 4 PRs, each merged `dev → main` and deployed before the next starts (see [Delivery](#delivery))

## Goal

Move the backend from Java 17 / Spring Boot 3.2.3 to **Java 25 / Spring Boot 4 with virtual threads**, rebuild the API image (layers, non-root, AOT cache), run Postgres 17 everywhere, and bring the code to modern Java (records, pattern matching). Every step is **measured**: a shared Prometheus + Grafana stack on the VPS records a baseline before the platform changes, so the effect of Boot 4 and of virtual threads is visible instead of assumed.

Decisions:

| Topic | Decision |
|-------|----------|
| Java | **25 (LTS)**. JEP 491 (Java 24+) stops virtual threads from pinning on `synchronized`, which the JDBC driver, Hibernate and Caffeine still use |
| Strategy | Staged: Java 25 + Boot 3.5 first (surface the deprecations), then Boot 4, then virtual threads, then records |
| PRs | 4 PRs, each a `dev → main` merge with its own deploy |
| "Modern stack" | Records for DTOs, Java 25 language features, Actuator + Micrometer, springdoc-openapi |
| API image | Rewritten multi-stage `Dockerfile` on `eclipse-temurin:25` (noble): dependency layer cache, extracted layered jar, non-root user, AOT cache (JEP 483/514), container-aware JVM flags, `HEALTHCHECK` |
| Management | Actuator on an **internal** port (9091, never published); only `/livez` and `/readyz` on the public port |
| Observability | Prometheus + Grafana + node-exporter + cAdvisor, **shared by the whole VPS**, starting with Futspring only |
| Grafana access | `grafana.luisgosampaio.com` over HTTPS through the host nginx, Grafana login, no sign-up, no anonymous access. Prometheus is not exposed |
| Observability deploy | Its own workflow, `deploy-observability.yml`, independent from the API deploy |
| Schema | **No migration.** Nothing in the schema changes; a Hibernate 7 `validate` complaint is fixed in the mapping |
| API contract | **Zero JSON changes**, guarded by a contract snapshot test written in PR 1 |

### VPS facts this design is based on (checked 2026-10-29)

| | |
|---|---|
| CPU / RAM / swap | 2 vCPU · 7.8 GiB (≈ 3.6 GiB available) · **no swap** (an OOM kills a process of any project) |
| Disk | 96 GB, 81 GB free |
| Host ports taken | 22, 80, 443, 3000 (`vap-web`), 8080, 8081 (Futspring), 8082, 8083, 8084, 8085, 8086, 8087, 8090 |
| Free ports chosen | **8088** (Grafana, `127.0.0.1` only) |
| Existing monitoring | none (no Prometheus / Grafana container) |
| Other projects | lia, vap, gwent, sessao-a-dois, oriens, zugaming, pilling (16 containers in total) |

The management port 9091 lives inside the API container's network namespace and is never published, so it can't collide with a host port (8082 on the host belongs to `zugaming-api`).

### Out of scope

- Centralized logs (Loki) and tracing (Tempo / OpenTelemetry); Grafana alerting.
- GraalVM native image, Spring Modulith, JSpecify null-safety.
- Removing Lombok from **entities** (they stay `@Getter @Setter` + `EntityIdentity`).
- The other projects' nginx or their `0.0.0.0` port bindings (`8080`, `8082`–`8085` bypass nginx; worth fixing, separately).
- Frontend changes. The JSON contract stays identical; if a field would change, the backend is fixed, not the client.

## Global acceptance criteria (every PR)

1. `./mvnw verify` green, including `FlywayMigrationTest` and `MigrationDataTest` (Docker).
2. The contract snapshot test passes **without regenerating snapshots** (PR 2 and PR 4 must not touch `src/test/resources/contract/`).
3. The deploy waits for the container's `healthy` state; the `deploy.yml` smoke test gets 200 from `<API_URL>/readyz`.
4. No Flyway migration is added. Rollback = revert the merge on `main` and redeploy; with no schema change it is always safe.
5. `core/docs/BACKEND.md` (and the root `CLAUDE.md` stack line) describe the new state in the same PR.

## PR 1 — Platform: Java 25, Boot 3.5, image, Postgres 17, observability

### Build

- `pom.xml`: parent `spring-boot-starter-parent` → latest **3.5.x**; `java.version` 25.
- Lombok: override `lombok.version` to a release that supports JDK 25 (≥ 1.18.40) if Boot 3.5's managed one doesn't.
- Remove the `testcontainers.version` override (Boot 3.5 manages a 1.21.x that talks to Docker 29); keep it only if the managed version is older than 1.21.
- Mockito/Byte Buddy on JDK 25: run Mockito's inline mock maker as a `-javaagent` in Surefire (`argLine`) to silence the dynamic agent warning (JEP 451), which will become an error.
- Fix every deprecation warning Boot 3.5 reports (`./mvnw -Dmaven.compiler.showDeprecation=true compile`), since Boot 4 deletes them.
- CI (`ci-backend.yml`): `setup-java` with `java-version: 25`.

### Dockerfile (`core/Dockerfile`)

```
# build: dependencies cached in their own layer
FROM eclipse-temurin:25-jdk-noble AS build
  copy mvnw, .mvn, pom.xml → ./mvnw -B dependency:go-offline
  copy src → ./mvnw -B package -DskipTests
  java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

# training: same JRE as runtime, so the AOT cache matches it
FROM eclipse-temurin:25-jre-noble AS training
  copy extracted layers
  java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh \
       --spring.flyway.enabled=false \
       --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
       (dummy JWT_SECRET / DB_* so the context starts without a database)
       -jar app.jar

# runtime
FROM eclipse-temurin:25-jre-noble
  non-root user (uid 10001), WORKDIR /app, uploads dir owned by it
  COPY layers in order: dependencies → spring-boot-loader → snapshot-dependencies → application, then app.aot
  ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:AOTCache=app.aot"
  HEALTHCHECK curl -fsS http://localhost:9091/actuator/health/readiness
  ENTRYPOINT ["java", "-jar", "app.jar"]
```

- The training run must not need a database. If the context can't refresh with JPA/Flyway disabled, fall back to a training run limited to class loading (`-Dspring.context.exit=onRefresh` with a profile `aot-training` that excludes `DataSourceAutoConfiguration`), and document what the cache misses. If neither works, ship without the AOT cache and record it as a follow-up; it must not block the PR.
- `curl` is installed in the runtime stage (`apt-get install --no-install-recommends curl`) unless the base image already has it.
- `/opt/futspring-uploads` on the host must be writable by uid 10001: `deploy.sh` runs `chown -R 10001:10001 /opt/futspring-uploads` (idempotent) before `docker compose up`.
- Measure the startup time before and after (log line "Started FutSpringApplication in …") and write both numbers in the PR description.

### Postgres 17 in dev

- `docker-compose.dev.yml`: `postgres:17-alpine`; drop the obsolete `version: '3.8'`.
- A Postgres 15 data directory can't be opened by 17. The dev database is the `DataInitializer` seed, so the step is `docker compose -f docker-compose.dev.yml down -v` and up again; written in `BACKEND.md` Commands.
- Production (Supabase) is already on 17. The Flyway 9 "PostgreSQL 17 is newer" warning goes away in PR 2 (Flyway 11).

### Actuator and Micrometer

Dependencies: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`.

```properties
management.server.port=9091
management.endpoints.web.exposure.include=health,info,metrics,prometheus,threaddump
management.endpoint.health.show-details=always          # only reachable on 9091
management.endpoint.health.probes.enabled=true
management.endpoint.health.probes.add-additional-paths=true   # /livez, /readyz on the main port
management.metrics.tags.application=futspring
management.metrics.distribution.percentiles-histogram.http.server.requests=true
```

- `SecurityConfig`: `permitAll` on `GET /livez` and `GET /readyz`. Nothing under `/actuator` is served on 8080, so nothing is reachable through nginx except the two probes (`https://futspring.luisgosampaio.com/api/readyz`).
- Domain metrics (Micrometer `MeterRegistry`, names under `futspring.`):

  | Metric | Type | Where |
  |--------|------|-------|
  | `futspring.email.queue.size` | gauge | `NotificationEmailDispatcher` executor queue |
  | `futspring.email.sent` (tag `result=ok\|failed`) | counter | `NotificationEmailDispatcher` after each send |
  | `futspring.email.dropped` | counter | full queue (today only logged) |
  | `futspring.ratelimit.rejected` (tag `route`) | counter | `AuthRateLimitFilter` on a 429 |
  | `futspring.notifications.push` (tag `result`) | counter | `NotificationPushService` |

  Built-in: JVM (heap, GC, `jvm.threads.live` / `jvm.threads.states`), Hikari (`hikaricp.connections.active/pending/acquire`), Tomcat, `http.server.requests`, `tasks.scheduled.execution`, Caffeine (when the cache is registered with `recordStats()`).

### Contract snapshot test

The guard for PR 2 (Jackson 3) and PR 4 (records).

- `ContractSnapshotTest` (`@JsonTest`, the application's mapper): for every **response DTO** (one fixture per DTO, built with fixed ids/dates, booleans `true` so `is*` fields show up) serialize and compare with `src/test/resources/contract/<Dto>.json`. Comparison is on the JSON tree, **order-insensitive** (Jackson 3 sorts properties alphabetically by default; order is not part of the contract).
- For every **request DTO**: deserialize `src/test/resources/contract/requests/<Dto>.json` (the shape the frontend sends today, taken from `client/src/api/*`) and assert the fields. This catches Jackson 3's stricter defaults (`FAIL_ON_NULL_FOR_PRIMITIVES`, `FAIL_ON_TRAILING_TOKENS`).
- Snapshots are generated once in PR 1 (`-Dcontract.update=true` writes them) and reviewed by hand against `client/src/types/*`: any field whose name already differs from the frontend type is a bug to report, not to snapshot silently.

### Shared observability stack (`ops/observability/`)

```
ops/observability/
├── docker-compose.yml        prometheus, grafana, node-exporter, cadvisor; network "observability" (external)
├── prometheus/prometheus.yml scrape_interval 30s; jobs: futspring-api (futspring_api:9091/actuator/prometheus),
│                             node-exporter, cadvisor, prometheus
├── grafana/provisioning/
│   ├── datasources/prometheus.yml
│   └── dashboards/           dashboards.yml + JSON: Futspring (custom), JVM Micrometer, Node Exporter, cAdvisor
└── .env.example              GRAFANA_ADMIN_PASSWORD, GRAFANA_ROOT_URL
```

| Container | Image (pinned versions) | Host port | `mem_limit` | Notes |
|-----------|-------------------------|-----------|-------------|-------|
| prometheus | `prom/prometheus` | none | 512m | `--storage.tsdb.retention.time=15d`, `--storage.tsdb.retention.size=2GB`, volume `prometheus_data` |
| grafana | `grafana/grafana-oss` | `127.0.0.1:8088:3000` | 256m | `GF_USERS_ALLOW_SIGN_UP=false`, `GF_AUTH_ANONYMOUS_ENABLED=false`, `GF_SERVER_ROOT_URL`, admin password from env, volume `grafana_data` |
| node-exporter | `prom/node-exporter` | none | 64m | host `/proc`, `/sys`, `/` read-only, `pid: host` |
| cadvisor | `gcr.io/cadvisor/cadvisor` | none | 256m | `--docker_only=true --housekeeping_interval=30s` and disabled heavy metrics (2 vCPU) |

Total ≈ 1.1 GB of limits, ≈ 400–500 MB in practice. The API container also gets a `mem_limit` (set in PR 1 from what Grafana shows it uses, with `MaxRAMPercentage=75` inside it).

- Network: the stack creates nothing; `observability` is an external network created with `docker network create observability` (idempotent: `|| true`) by both deploy scripts. `docker-compose.prod.yml` adds the API to it next to its default network. Another project joins later by doing the same and adding a scrape job.
- The **Futspring** dashboard (provisioned JSON): request rate, p50/p95/p99 and error rate per route; JVM heap and GC; **platform vs virtual threads**; Hikari active / pending / acquire time; e-mail queue, sent/failed/dropped; rate-limit 429s; container CPU/RAM of `futspring_api` (cAdvisor).

### Grafana behind nginx (manual, documented in `ops/observability/README.md`)

1. DNS: `A grafana.luisgosampaio.com → 31.97.169.38`.
2. `/etc/nginx/sites-available/grafana`:

   ```nginx
   server {
       server_name grafana.luisgosampaio.com;
       location / {
           proxy_pass http://127.0.0.1:8088;
           proxy_set_header Host $host;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
       location /api/live/ {               # Grafana Live websocket
           proxy_pass http://127.0.0.1:8088;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
           proxy_set_header Host $host;
       }
   }
   ```

3. `ln -s` into `sites-enabled`, `nginx -t && systemctl reload nginx`, `certbot --nginx -d grafana.luisgosampaio.com`.

### Deploy changes

- `deploy-observability.yml` (new): on push to `main` touching `ops/observability/**`, and `workflow_dispatch`. Writes `.env` from secrets, `rsync`s the folder to `~/observability`, `docker network create observability || true`, `docker compose up -d --remove-orphans`, then smoke-tests: Grafana `/api/health` 200 (through `127.0.0.1:8088` over SSH) and every Prometheus target `up` (`docker exec prometheus wget -qO- localhost:9090/api/v1/targets`).

  | GitHub | Name | Notes |
  |--------|------|-------|
  | secret | `GRAFANA_ADMIN_PASSWORD` | required |
  | variable | `GRAFANA_ROOT_URL` | `https://grafana.luisgosampaio.com` |

- `scripts/deploy.sh`: `docker network create observability || true`; `chown` of the uploads dir; instead of polling for the 401, wait (up to 2 min) for `docker inspect -f '{{.State.Health.Status}}' futspring_api` = `healthy`, else print the logs and fail.
- `deploy.yml` smoke test: `<API_URL>/readyz` → 200 (replaces the `/files/__smoke-test__.png` 404 trick).

### Baseline

After PR 1 is in production, leave it **one week** before merging PR 3 (PR 2 can be developed meanwhile) and write down in the PR 3 description, from Grafana: p95/p99 of the main routes, Hikari pending and acquire time at peak, live threads, heap after GC, container CPU and RAM. Plus the local load test below on PR 1's image.

### Tests

- `ActuatorExposureTest` (`BaseIntegrationTest`): `/livez` and `/readyz` 200 without a token; `/actuator/health` and `/actuator/prometheus` not served on the main port; with a `management.server.port` set to a random port, `/actuator/prometheus` answers and contains `futspring_` metrics.
- Unit tests for the new counters/gauge where the code changed (dispatcher, rate-limit filter).
- `ContractSnapshotTest` (above).

### Implementation (PR 1)

- [x] **PR 1 done** (2026-10-29, commits on `dev`; not merged to `main` nor deployed yet)

Decisions and findings:

- **Boot 3.5.16 already brings Flyway 11** (and Hibernate 6.6, Spring 6.2, Lombok 1.18.46, Testcontainers 1.21.4, Byte Buddy 1.17.8). `flyway-database-postgresql` is added here, not in PR 2, and the Flyway "PostgreSQL 17 is newer" warning is already gone. The `testcontainers.version` override was removed (managed 1.21.4).
- **Lombok** is declared in the compiler's `annotationProcessorPaths`: JDK 23+ no longer runs processors found on the classpath, so nothing compiled without it. Lombok itself prints a `sun.misc.Unsafe` warning at compile time (its own code, nothing to fix here).
- **Deprecations fixed now:** `UriComponentsBuilder.fromHttpUrl` → `fromUriString`; `HandlerMethodValidationException.getAllValidationResults` → `getParameterValidationResults`; Bucket4j `Bandwidth.classic` / `Refill` → `Bandwidth.builder()` (the builder already exists in 8.7, so PR 2 only bumps the version); Hibernate `HHH90000025`: the explicit `hibernate.dialect` was removed from the main and test config (planned for PR 2, but Boot 3.5 already reports it). The AOT training run, which has no database, names the dialect on its command line. `./mvnw -Dmaven.compiler.showDeprecation=true test-compile` reports nothing.
- **Hibernate 6.6 is stricter on flush:** a managed entity referencing one deleted by a bulk `DELETE` now throws `TransientObjectException`. Only `PeladaControllerTest.deletePelada_withFinishedDailyAndMessages…` hit it, because it finalizes and deletes in the same test transaction; in production those are separate requests with fresh persistence contexts. The test now flushes and clears before the request; no production code changed.
- **Security:** a second `SecurityFilterChain` (`@Order(1)`, `EndpointRequest.toAnyEndpoint()`, `permitAll`) because Boot applies the security filter to the management port too (Prometheus got 401 without it). `/actuator/**` on the main port answers 401 (not served).
- `src/test/resources/application.properties` replaces the main file in tests, so the management block is repeated there (port 0).
- **Contract snapshot:** `ContractFixtures` fills each DTO by reflection, with values derived from field name and type only, so PR 4's records produce identical JSON; 24 response snapshots, 27 request bodies (round-trip check: every value the client sends survives deserialization). Reviewed against `client/src/types`: every name the client reads matches. Two findings, recorded, not fixed (zero JSON changes in this PR):
  1. Every primitive `boolean isX` field annotated `@JsonProperty("isX")` is serialized **twice**, as `isX` and as `x` (Lombok's `isX()` getter): `isAdmin`/`admin`, `isFinished`/`finished`, `isConfirmed`/`confirmed`, `isMensalista`/`mensalista` (`PeladaMemberDTO`, `PeladaResponseDTO` and `NextDailyDTO`, `DailyListItemDTO`, `DailyDetailDTO`, `PeladaAttendanceDTO.Row`). The client never reads the short names. Records (PR 4) will drop them, so **PR 4 must decide**: drop them deliberately (update the snapshots in a reviewed commit) or keep them with `@JsonProperty`.
  2. Client `sortTeams` / `swapPlayers` (`client/src/api/dailies.ts`) are typed `Promise<DailyDetail>`, but the API returns `List<TeamDTO>`. Harmless today (`useDailyActions` ignores the value and reloads); fix the client type when touching it.
- **Image:** `--application-filename app.jar` on `extract` (otherwise the jar keeps its Maven name). The AOT training refreshes the context without a database (Flyway off, `ddl-auto=none`, no JDBC metadata access) and works; no fallback needed. Startup measured locally against Postgres 17: **3.1 s** (Java 17 / Boot 3.2 image) → **3.1 s** (Java 25 / Boot 3.5, cache disabled) → **1.8 s** (with the AOT cache, −42%). Image 309 MB → 532 MB (`app.aot` is 125 MB). `curl` is installed in the runtime stage (not in the base image).
- **Deploy:** `mem_limit: 1g` for the API to start with (heap max ≈ 768 MB); revisit with the Grafana numbers. Idle RSS measured locally ≈ 430 MiB.
- **Observability stack:** images pinned to `prom/prometheus:v3.15.1`, `grafana/grafana-oss:13.0.2`, `prom/node-exporter:v1.12.1`, `ghcr.io/google/cadvisor:v0.60.6` (cAdvisor moved to ghcr.io after v0.52). Grafana `mem_limit` raised to **384m** (≈ 160 MiB idle already); `GF_PLUGINS_PREINSTALL_DISABLED` and empty `plugins/` / `alerting/` provisioning folders silence startup errors. Prometheus scrapes **`futspring-api:9091`** (compose service name): Tomcat answers 400 to the container name `futspring_api` (underscore in `Host`). Community dashboards 4701, 1860, 14282 downloaded and versioned with the datasource pinned to uid `prometheus`; the Futspring dashboard is generated JSON (24 panels) and every query was checked against a local Prometheus.
- The **virtual vs platform threads** panel shows `jvm_threads_live` (platform threads only) and executor activity; series about virtual threads (pinning, via `micrometer-java21`) come with PR 3.
- `deploy-observability.yml` smoke test: Grafana `/api/health` and the stack's own targets must be up; a project target (`futspring-api`) only warns, because on a push to `main` the API deploy runs at the same time and takes longer.
- **Load test** (`ops/loadtest/run.sh`) lives in PR 1, so the baseline runs on this image: `oha --urls-from-file` (random mix), live platform threads sampled from the management port, `JFR=1` summarizes `jdk.VirtualThreadPinned`; requests aborted by oha's deadline are not counted as errors. Baseline: `ops/loadtest/results/pr1-2026-10-29.md`.
- **Pending manual steps (owner):** DNS `grafana.luisgosampaio.com`, the nginx site + certbot (`ops/observability/README.md`), secret `GRAFANA_ADMIN_PASSWORD` and variable `GRAFANA_ROOT_URL`; then the `dev → main` PR and the one-week production baseline before PR 3.

## PR 2 — Spring Boot 4

Start with OpenRewrite (`org.openrewrite.recipe:rewrite-spring`, recipe `UpgradeSpringBoot_4_0`) on a branch, then review every change by hand; the recipe is an accelerator, not the source of truth.

| Area | Change |
|------|--------|
| Parent | `spring-boot-starter-parent` → latest **4.0.x** |
| Starters (Boot 4 is modular) | `spring-boot-starter-web` → `spring-boot-starter-webmvc`; add `spring-boot-starter-flyway` (Flyway autoconfig is no longer in core) + `flyway-database-postgresql` (Flyway 11); test starters per module (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`…) and the moved test annotations (`@AutoConfigureMockMvc`, `@DataJpaTest`, `@JsonTest` packages) |
| Jackson 3 | `com.fasterxml.jackson.databind` → `tools.jackson.databind` in the 11 files that import it (`SecurityConfig` injects `ObjectMapper` for the 401 body; prefer `JsonMapper`); annotations stay in `com.fasterxml.jackson.annotation`. New defaults checked by the contract test: alphabetical property order (harmless), `FAIL_ON_NULL_FOR_PRIMITIVES` and `FAIL_ON_TRAILING_TOKENS` on. Fix each failure explicitly (a primitive that can be null becomes a wrapper with `@NotNull`); don't use a blanket "Jackson 2 defaults" switch unless a failure can't be fixed locally, and then document it |
| Hibernate 7 | `validate` must still pass against V1–V10 (`FlywayMigrationTest`); remove the explicit `hibernate.dialect` (detected); check the native/JPQL queries in the aggregate repositories (`DailyAwardRepository`, `UserDailyStatsRepository`) and `@Lock` queries with their tests |
| Spring Security 7 | `SecurityConfig` lambda DSL already; replace any `AntPathRequestMatcher` with `PathPatternRequestMatcher`; `JwtChannelInterceptor` unchanged in behaviour |
| JUnit / Mockito | JUnit Jupiter 6 (managed); Mockito 5 agent from PR 1 |
| Testcontainers 2 | artifacts `testcontainers-postgresql`, `testcontainers-junit-jupiter`; `org.testcontainers.postgresql.PostgreSQLContainer`; remove any version override |
| JJWT | 0.11.5 → 0.12.x/0.13.x: `Jwts.parser().verifyWith(key).build().parseSignedClaims(…)`, `Jwts.builder().subject(…).claims(…)`; tokens issued before the upgrade must keep validating (test with a token signed by the old code path: same HS256 key, same claims) |
| Bucket4j | latest 8.x (`com.bucket4j:bucket4j_jdk17-core`); `Bandwidth.builder()` API |
| springdoc | `springdoc-openapi-starter-webmvc-ui` 3.x. `springdoc.api-docs.enabled=${API_DOCS_ENABLED:false}` (on in `docker-compose.dev.yml`, off in production; the SPA is the only consumer there). Bearer auth scheme declared once (`@SecurityScheme`) so Swagger UI can call protected routes in dev; `/v3/api-docs/**` and `/swagger-ui/**` `permitAll` only when enabled |
| Config | `application.properties` keys renamed by Boot 4 (the `spring-boot-properties-migrator` dependency during the PR, removed before merge) |

Acceptance: contract snapshots unchanged; existing production JWTs still valid after the deploy (no forced logout); Flyway 11 logs no "newer than this version" warning; Grafana shows no regression in p95 / error rate over the days after the deploy.

## PR 3 — Virtual threads

```properties
spring.threads.virtual.enabled=true
spring.datasource.hikari.maximum-pool-size=10      # explicit, see below
spring.datasource.hikari.connection-timeout=5000
```

What it changes and what stays:

| Component | With virtual threads |
|-----------|----------------------|
| Tomcat request handling | one virtual thread per request instead of the 200-thread pool |
| `@Async` (`NotificationPushService`, `PasswordResetEmailListener`) | Boot's `SimpleAsyncTaskExecutor` on virtual threads: **unbounded**. Each push opens a read transaction, so concurrency is bounded by Hikari, not by a pool. Acceptable at today's volume; a `spring.task.execution.simple.concurrency-limit` is set if the Hikari pending metric shows bursts |
| `@Scheduled` jobs | virtual-thread scheduler; behaviour unchanged (each pelada already runs in its own `TransactionTemplate`) |
| `NotificationEmailDispatcher` | **stays a single-thread executor with its 500-slot queue** (pacing to Resend's 2 req/s depends on it). It may use `setVirtualThreads(true)` with core = max = 1; the serialization is what matters |
| STOMP channels (simple broker) | verify which executor Boot 4 uses for `clientInbound/OutboundChannel` with virtual threads on; no change unless the threaddump shows otherwise |
| `ResendEmailSender` (`RestClient`) | blocking I/O on a virtual thread, which is the case virtual threads are made for |

- **Hikari is the real limit.** With platform threads, Tomcat's 200 threads already exceeded the 10 connections; with virtual threads the queue in front of Hikari can grow more. The pool stays at 10 (Supabase session pooler limits apply); `connection-timeout` 5 s turns a saturated pool into a fast 500 instead of a 30 s hang. The `PESSIMISTIC_WRITE` locks (daily, pelada, reset token) keep a connection while waiting, so watch `hikaricp.connections.pending` together with lock waits.
- **Pinning:** `-Djdk.tracePinnedThreads` no longer exists in Java 24+. Use JFR: the load test runs the container with `-XX:StartFlightRecording` and the report lists `jdk.VirtualThreadPinned` events (`jfr print --events jdk.VirtualThreadPinned`). Expected: none or only short ones in native/class-init frames.
- **ThreadLocals:** the security context and the Hibernate session are per-request ThreadLocals, which is fine on virtual threads. No code pools objects in ThreadLocals (checked: no `ThreadLocal` in `src/main`).
- `AttendanceConcurrencyTest` must still pass (row lock under concurrent diaristas), now on virtual threads.

### Measurement (`ops/loadtest/`)

- `ops/loadtest/run.sh`: builds and runs the API image locally with the production limits (`--cpus=1`, the same `mem_limit`), Postgres 17 with the dev seed, logs in as a seed user and runs `oha` against a fixed mix: `GET /peladas/my`, `GET /peladas/{id}`, `GET /dailies/{id}`, `GET /peladas/{id}/ranking`, `GET /peladas/{id}/attendance`, at 50 / 200 / 500 concurrent connections for 60 s each. Output: a markdown table (RPS, p50/p95/p99, errors) saved to `ops/loadtest/results/<pr>-<date>.md`, plus the JFR pinning summary.
- It runs **locally, never against production**: the VPS has 2 vCPU shared by 7 projects.
- Run on PR 1's image (baseline), PR 2's and PR 3's; the three tables go in PR 3's description, next to the production numbers from Grafana.

Expectation stated up front: most routes are database-bound and Hikari caps them at 10 concurrent queries, so **throughput is not expected to rise much**. The expected gains are fewer platform threads and less memory under concurrency, no request starvation while threads wait on Resend or locks, and stable latency at high concurrency. The PR is accepted when nothing regresses:

- p95 and error rate at 50 / 200 connections ≤ the PR 2 baseline (+10% tolerance);
- no new errors at 500 connections other than fast Hikari timeouts;
- `jvm.threads.live` clearly lower under load;
- no long `jdk.VirtualThreadPinned` events (> 20 ms);
- one week in production with no regression on the Futspring dashboard.

## PR 4 — Records and modern Java

### DTOs as records

- The 48 DTO classes in `domain/*/dto/` (2 are already records) become `record`s. `@Builder` stays where tests use it (Lombok supports it on records; 338 `.builder()` calls in tests keep compiling); `@Data`/`@Setter`/`@NoArgsConstructor` go.
- Validation annotations move to the record components (`record UpdatePlayerStarsRequestDTO(@NotNull @Min(1) @Max(5) Integer stars)`); nested lists keep `List<@Valid …>`.
- Static factories (`from(entity)`) stay as static methods on the record.
- Where a service mutates a DTO after building it (setters), build it complete instead or add a `with…` method. List them first (`grep` for `.set` on DTO types) and handle each one.
- **JSON names:** a `boolean isAdmin` record component serializes as `"isAdmin"`, whereas a Lombok `boolean isAdmin` field with getter `isAdmin()` may have serialized as `"admin"`. The contract snapshot is the authority: if it fails, add `@JsonProperty` to keep the **current** name; never change a name the frontend reads.
- Entities are untouched.

### Language features (only in files this PR or later PRs touch; no mass rewrite)

- `switch` expressions and pattern matching for `instanceof` / sealed hierarchies (e.g. `NotificationEmailContent`'s exhaustive switch, the domain event listeners).
- Record patterns where a record is destructured.
- `SequencedCollection` (`getFirst()` / `getLast()` / `reversed()`) instead of `get(0)` / `get(size - 1)`.
- Text blocks for multi-line strings (already used in `@Query`).
- Unnamed variables `_` (Java 22+) for unused lambda/catch parameters.
- `var` only where the type is obvious from the right-hand side.

Acceptance: contract snapshots unchanged; `./mvnw verify` green; no behaviour change (this PR touches no service logic other than the DTO construction).

## Delivery

| PR | Branch flow | Merge to `main` when |
|----|-------------|----------------------|
| 1 | commits on `dev` → PR `dev → main` | CI green, API `healthy`, observability stack up with the Grafana subdomain, dashboards receiving production data. Then the one-week baseline starts |
| 2 | `dev → main` | CI green, contract snapshots unchanged, existing JWTs valid |
| 3 | `dev → main` | load test table + one week of baseline in the description, criteria above met |
| 4 | `dev → main` | CI green, contract snapshots unchanged |

Each PR description lists what changed in the deploy (new secrets/variables, manual steps such as the nginx block in PR 1) and states that there is no migration.

## Docs to update

| PR | `core/docs/BACKEND.md` | Other |
|----|------------------------|-------|
| 1 | Stack (Java 25, Boot 3.5, Actuator, Micrometer), Commands (dev volume reset, `ops/loadtest`), CI/CD (health wait, `/readyz` smoke test, observability workflow, `GRAFANA_*`), Configuration (management port), Security (public probes), Testing (contract and actuator tests), the Flyway/PG17 note | root `CLAUDE.md` stack line; `ops/observability/README.md` |
| 2 | Stack (Boot 4, Hibernate 7, Jackson 3, Flyway 11, Testcontainers 2, JJWT, springdoc), Configuration (`API_DOCS_ENABLED`), Security (springdoc paths), the Flyway 10 upgrade note removed | root `CLAUDE.md` |
| 3 | Configuration (virtual threads, Hikari), Performance (virtual threads, Hikari as the limit, "Single instance assumptions"), Notification e-mails (dispatcher stays serialized) | — |
| 4 | Package layout / Patterns (DTOs are records), Conventions (records, Java 25 features) | — |

## Risks

| Risk | Mitigation |
|------|------------|
| Jackson 3 or records rename a JSON field and the SPA breaks silently | Contract snapshot test from PR 1, checked against `client/src/types/*` |
| AOT training needs a database | JDBC metadata access off + Flyway off in training; fallback to a reduced training run or no cache (doesn't block PR 1) |
| Non-root container can't write uploads | `chown` of `/opt/futspring-uploads` in `deploy.sh` before `up` |
| JJWT upgrade invalidates sessions | Test that a token produced by the 0.11 code validates with the new parser |
| Unbounded `@Async` on virtual threads exhausts Hikari | 5 s connection timeout, Hikari pending on the dashboard, `concurrency-limit` if needed |
| Observability eats the VPS (2 vCPU, no swap) | `mem_limit` on every container, 30 s scrape, cAdvisor housekeeping 30 s, retention 15 d / 2 GB |
| Grafana exposed on the internet | HTTPS, no sign-up/anonymous, strong admin password from a secret; Prometheus never exposed |
| Boot 4 libraries not ready (springdoc, Bucket4j) | Pin to their Boot 4–compatible lines; if one lags, PR 2 waits rather than forking |
