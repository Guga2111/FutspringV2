# Testing

> Which kind of test each change needs, the helpers to use and where each area is already tested. Part of the [backend guide](README.md).

## Rules

1. A test lives in the package of the class it tests; an integration test of a whole domain sits at the domain root; shared helpers in `support/`, migration tests at the root.
2. A new endpoint gets a controller test (happy path, validation 400, 403 for non-member / non-admin, 404, a child id from another parent) and service tests for its rules.
3. N+1 tests compare `QueryCounter` reads for a small and a large batch (e.g. 2 and 20), never an absolute number.
4. Contract snapshots are regenerated only for an intended change (`-Dcontract.update=true`), and the diff is checked against `client/src/types`.
5. Mockito strict stubs are on: remove a stub the test doesn't use (`UnnecessaryStubbingException`).
6. Anything that needs real PostgreSQL or Redis behavior (row locks, native SQL, constraints, races) uses Testcontainers; those tests skip without Docker and always run in CI.
7. The suite stays green: CI (`./mvnw -B verify`) blocks the deploy.

## Test kinds

| Kind | How | Reference |
|---|---|---|
| Service unit | JUnit Jupiter 6 + Mockito + AssertJ, one class per service. Mock the repositories and `UserAuthenticationHelper`; build a real `PeladaAccessHelper` over the mocked `PeladaRepository` and call `MembershipStubs.stubMembership(peladaRepository, pelada…)` so the exists queries answer from the entities | `PeladaServiceTest`, `DailyAttendanceServiceTest` |
| Controller integration | Extend `BaseIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@Transactional`, real JWTs from `JwtService`), H2 | `PeladaInvitationTest`, `NotificationControllerTest` |
| Repository | `@DataJpaTest` on H2 | `DailyAttendanceRepositoryTest`; native queries: `DailyAwardRepositoryTest` |
| Job / transaction boundary | `@DataJpaTest` **without** a test transaction and `Clock.fixed`, so commits, rollbacks and after-commit delivery are real | `ScheduledNotificationJobTest`, `DailySchedulerTransactionTest` |
| Concurrency | Testcontainers `postgres:17-alpine`, no test transaction, virtual threads released by one latch | `AttendanceConcurrencyTest`, `FinanceConcurrencyTest` |
| Real server | Behavior that lives in Tomcat, filters or transports: a real socket on `RANDOM_PORT` (MockMvc doesn't decode paths or run the valve) | `AuthRateLimitFilterTest`, `ClientErrorBodyLimitFilterTest`, `StompRevocationTest`, `VirtualThreadsTest` |
| Query count | `support/QueryCounter`: `reads(block)` (queries + entity/collection loads) or `statements(block)` (prepared statements, when `reads` counts every hydrated row); warm-up run first, inserts left out (one per row with `IDENTITY`) | `FinanceServiceTest.transitionsReadAConstantNumberOfQueries`, `WebPushDispatcherQueryTest` |
| Contract | `contract/ContractSnapshotTest` (`@JsonTest`, the application's `JsonMapper`): each response DTO filled by `ContractFixtures` must match `src/test/resources/contract/<Dto>.json`; each request body the client sends (`contract/requests/`) must deserialize and keep every value | `ContractSnapshotTest`, `RecordContractTest` |
| Migration | `FlywayMigrationTest` applies every migration to Postgres 17 and starts with `validate`; `MigrationDataTest` migrates to a version, inserts production-shaped rows and checks the next migration | both, at the test root |
| Architecture | ArchUnit | `DomainLayoutTest` |
| Configuration | A minimal context or the main `application.properties` | `StructuredLoggingTest`, `ActuatorExposureTest`, `RedisConnectionTest` |

## Test configuration

- `src/test/resources/application.properties` **replaces** the main file: H2 in PostgreSQL mode, `create-drop`, Flyway off, a fixed JWT secret, 1 h TTL, uploads in a temp dir, the same management settings on a random port, Hibernate statistics on (for `QueryCounter`). A property a test needs is repeated there.
- Time comes from the injected `Clock`: tests fix or mock it (`Clock.fixed`, `@MockitoBean Clock`).
- Tests keep plain-text logs.

## Helpers

| Helper | Use |
|---|---|
| `support/BaseIntegrationTest` | base of controller integration tests |
| `support/MembershipStubs` | membership exists-queries answered from the entities in service tests |
| `support/QueryCounter` | constant-query assertions |
| `support/InMemoryAttendance` | `DailyAttendanceRepository` backed by an in-memory list (lenient stubs) |
| `support/TestImages` | real JPEG / PNG / WebP images (WebP fixtures in `src/test/resources/images`, made with `cwebp`) |
| `contract/ContractFixtures` | fills every field of a DTO from its name and type |

## Where each area is tested

Start from these classes when changing an area; add the new case next to the existing ones.

| Area | Classes |
|---|---|
| Auth inputs, login, register | `AuthControllerTest`, `AuthServiceTest`, `PasswordResetControllerTest`, `PasswordResetServiceTest` |
| Tokens, refresh sessions, Redis | `JwtServiceTest`, `AccessTokenVerifierTest`, `RefreshSessionTest`, `RefreshWithoutRedisTest`, `RedisConnectionTest`, `UserConcurrencyTest` |
| WebSocket | `JwtChannelInterceptorTest`, `StompSessionsTest`, `StompRevocationTest` |
| Account settings, deletion, export | `AccountSettingsTest`, `AccountDeletionConcurrencyTest`, `AccountExportTest` |
| Pelada read model | `PeladaFinanceManagerFlagTest`, `SubscriptionReadModelTest` |
| Invitations | `PeladaInvitationTest`, `InviteLinkTokensTest`, `InvitationConcurrencyTest` |
| Attendance | `DailyAttendanceServiceTest`, `AttendanceWindowTest`, `DailyAttendanceRepositoryTest`, `AttendanceConcurrencyTest` |
| Notifications | `NotificationServiceTest`, `NotificationPushServiceTest`, `NotificationControllerTest`, `NotificationContentTest`, `NotificationEmailDispatcherTest`, `EmailPacerTest`, `UnsubscribeTokensTest`, `NotificationRepositoryTest`, `ScheduledNotificationJobTest`, `NotificationRetentionJobTest`, `NotificationDeliveryTest` |
| Web Push | `PushEndpointPolicyTest`, `WebPushConfigTest`, `PushControllerTest`, `WebPushDispatcherTest`, `WebPushDispatcherQueryTest` |
| Subscriptions | `SubscriptionStateTest`, `SubscriptionGuardTest`, `SubscriptionReadOnlyTest`, `SubscriptionCreationTest`, `SubscriptionNoticeJobTest`, `PixBrCodeTest`, `BillingPixConfigTest`, `PeladaSubscriptionControllerTest`, `PeladaSubscriptionServiceTest` |
| Platform admin | `PlatformControllerTest` |
| Finance | `FinanceServiceTest`, `FinanceConcurrencyTest` |
| Uploads and files | `ImageDecoderTest`, `FileUploadServiceTest`, `FileControllerTest` |
| Client errors | `ClientErrorControllerTest`, `ClientErrorBodyLimitFilterTest`, `ClientErrorRedactionTest` |
| Tours | `TourControllerTest` |
| Scheduler | `DailySchedulerTransactionTest` |
| Contracts and DTO records | `ContractSnapshotTest`, `RecordContractTest`, `OpenApiDocsTest` |
| Runtime, logs, actuator | `VirtualThreadsTest`, `StructuredLoggingTest`, `ActuatorExposureTest` |
| Schema | `FlywayMigrationTest`, `MigrationDataTest` |

## What a change needs

| Change | Tests |
|---|---|
| New `{id}` route | the cases of rule 2, plus those in [Rules for new code](authorization.md#rules-for-new-code) |
| A write to a pelada's sporting or financial data | a 402 case in `SubscriptionReadOnlyTest` |
| A new read path or batch command | a `QueryCounter` comparison (rule 3) |
| A new or changed DTO | the contract snapshot, regenerated on purpose |
| A migration that changes data | a `MigrationDataTest` case with production-shaped rows |
| A new notification type | recipients in `NotificationServiceTest`, text in `NotificationContentTest`, delivery matrix in `NotificationEmailDispatcherTest` / `WebPushDispatcherTest` |
| A race (two requests for the last spot, a retry, a revocation) | a Testcontainers concurrency test |
| A new rate-limited or encoded path | a real-server test, encoded variant included |
| A JJWT upgrade | `JwtServiceTest` keeps validating the token signed by the old code (HS256, expires in 2100), so nobody is logged out |
