# Release P1 fixes

Approved scope: stable JWT account identity, concurrent user updates and refresh/logout ordering.

Use the existing JWT verifier and indexed repository query, checking both userId and email. JWTs without an immutable userId are rejected; no response contract changes.

Use JPA optimistic locking on User with additive V25 and HTTP 409 for stale writes. Every bulk User update also increments the persistence version; subscription trial creation reloads its managed creator after the conditional update. Keep credential and preference counters independent.

Use one Redis Lua operation for session issuance. Opening may create; rotating requires the existing session and old token. Logout deletion and rotation have a definite ordering; no rotation recreates a deleted session. Preserve the one-minute grace and sliding/absolute lifetimes.

Regression tests cover recreated email accounts, stale user saves, trial updates, migration defaults and logout interleavings against real Redis.

Validation: `./mvnw -o test` on Java 25 with Docker: 1,235 tests passed, no failures/errors/skips (including PostgreSQL migrations, Redis and STOMP).
