# Stack and commands

> Libraries and versions, the Maven and compose commands and CI. Part of the [backend guide](README.md).

## Rules

1. Jackson 3 (`tools.jackson.*`): inject `JsonMapper`.
2. No caching layer and no mapping library: DTOs map themselves.
3. Before finishing: `./mvnw test`; CI runs `./mvnw -B verify` and blocks the deploy.

## Stack

- Java 25, Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7, Hibernate 7.4), Maven (`./mvnw`). Boot 4 is modular: each technology has its own starter (`spring-boot-starter-webmvc`, `-flyway`, `-restclient`…) and its own test starter (`-webmvc-test`, `-data-jpa-test`, `-security-test`, `-actuator-test`, `spring-boot-resttestclient`). Lombok runs through `annotationProcessorPaths` (JDK 23+ no longer picks processors up from the classpath)
- Spring Web MVC, Spring Data JPA (Hibernate), Spring Security, Bean Validation
- Jackson 3 (`tools.jackson.*`; annotations stay in `com.fasterxml.jackson.annotation`); inject `JsonMapper`. Jackson 2 is on the classpath only as `jjwt-jackson`'s runtime dependency
- Spring WebSocket — STOMP over SockJS, in-memory simple broker (chat)
- PostgreSQL 17 (runtime: Supabase in production, `postgres:17-alpine` in dev), H2 in PostgreSQL mode (tests)
- JJWT 0.13 (HS256), Bucket4j 8.21 (`bucket4j_jdk17-core`) + Caffeine (login/register rate limit, in memory), Lombok
- TwelveMonkeys ImageIO 3.15 (`imageio-webp`, `imageio-jpeg`, `contrib`): ImageIO readers for WebP and CMYK JPEG, and the EXIF orientation (`EXIFUtilities`) applied before an upload is re-encoded
- `zerodep-web-push-java` 2.1.5 (Web Push: VAPID ES256 + RFC 8291 `aes128gcm` on the JDK's own crypto, no transitive dependencies; it only builds a `java.net.http.HttpRequest`, sent by the JDK `HttpClient`)
- Tests: JUnit Jupiter 6, Mockito 5 (inline mock maker loaded as a Surefire `-javaagent`, JEP 451), AssertJ, spring-security-test, Testcontainers 2 (`testcontainers-postgresql`, managed by Boot), ArchUnit 1.5 (package layout rules, test scope)
- Flyway 12 (`spring-boot-starter-flyway`) + `flyway-database-postgresql` (schema migrations, see [Database schema & migrations](migrations.md#database-schema--migrations))
- Spring Boot Actuator + Micrometer Prometheus registry, on an internal management port (see [Observability](observability.md#observability))
- springdoc-openapi 3 (OpenAPI document + Swagger UI), served only when `API_DOCS_ENABLED=true` (dev)
- Logs: Boot's native structured logging, one Elastic Common Schema (ECS) JSON line per event on the console, collected into Loki (see [Observability](observability.md#observability))
- Redis 7.4 (`spring-boot-starter-data-redis`, Lettuce): the store of the refresh-token sessions (see [Tokens](security.md#tokens-sdomainauthjwtservicejava)). Connects on first use, never part of health or readiness
- Not present: caching, MapStruct/ModelMapper

## Commands

PRs with security impact must follow the mandatory [security review workflow](../../docs/SECURITY_REVIEW.md), using Trail of Bits `differential-review` before opening/marking ready and before merge. Fix confirmed new and pre-existing vulnerabilities in the reviewed flows, include regression tests/migrations as needed, and record review evidence. Passing backend CI does not replace that review.

Run from `core/`:

```bash
./mvnw spring-boot:run                           # needs DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
./mvnw test                                      # all tests (H2, src/test/resources/application.properties)
./mvnw test -Dtest=PeladaServiceTest             # one test class
./mvnw clean package -DskipTests                 # build jar
./mvnw test -Dtest=ContractSnapshotTest -Dcontract.update=true   # rewrite the JSON contract snapshots (see Testing)
docker compose -f docker-compose.dev.yml up --build   # API + postgres:17-alpine (dev seed) + redis:7.4.12-alpine; Swagger UI on http://localhost:8080/swagger-ui.html
docker compose -f docker-compose.dev.yml down -v      # drop the dev database (needed once: a Postgres 15 volume can't be opened by 17)
../ops/loadtest/run.sh pr1                       # local load test of the image (see Observability)
```

CI (`.github/workflows/ci-backend.yml`) runs `./mvnw -B verify` (every test, including `FlywayMigrationTest` and `MigrationDataTest`) on pushes and PRs to `main`/`dev` that touch `core/`. Run `./mvnw test` before finishing any backend change.
