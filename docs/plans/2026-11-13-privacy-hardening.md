# Privacy hardening and incremental review

The approved changes are split into four independently validated increments targeting `feat/launch-hardening`:

1. `fix/client-error-redaction`: recognized invitation-path, URL-query, Bearer, named-secret and JWT redaction in browser telemetry and every logged server report field. Privacy/legal version: 2026-11-13.
2. `fix/private-photos`: authenticated photo bytes, current self/shared-group authorization for player images and current membership for group/champion images, private profile checks, no-store/nosniff, public invitation previews without photos and frontend authenticated blob/object-URL lifecycle.
3. `feat/verified-email`: V23 email verification, 30-minute random hashed single-use links plus the account password, resend cooldown/limits, after-commit mail, verified HTTP/STOMP/refresh sessions, portability/deletion support. The user explicitly selected verification for existing accounts too; V23 revokes existing sessions. Only explicit dev seeds are preverified. Registration issues no session, protects existing credentials and gives consistent email/username outcomes.
4. `fix/email-preference-links`: V24 revocable per-user link version, 30-day signed unsubscribe tokens, canonical Base64 validation, authenticated settings-only re-enable and revocation after email preference updates. Old permanent links stop working. V24 preserves V23 verification/session state.

Each PR updates both architecture docs alongside its contracts. Most verification test-file changes simply mark existing authenticated fixtures verified. The PRs form a commit chain and are merged in order with merge commits so subsequent comparisons contain only their own increment. No production deploy is authorized here.

## Validation

Full backend suites (Postgres migrations and Redis/Testcontainers enabled, no skips) plus frontend lint/build/tests run at every increment; exact totals are recorded in PR descriptions. The configured CI workflows target main/dev, so no automatic CI run is expected for this integration branch. The pre-existing frontend TanStack incompatible-library warning remains.

Prior production-build browser checks covered explicit verification, replay rejection, login, authenticated private image decoding, cookie restoration, signup without a session cookie and the saved local returnTo on the resend page. Screenshots were inspected at 375px light/dark and 1440px dark, with no horizontal overflow. Browser links were disposable database fixtures; live mailbox delivery was not exercised. Existing Resend delivery must be configured for real verification emails.

## Existing PR #125

PR #125 remains separate and is not included in this series. Its exact head a2b05efe895cdc467552c3f1bb54f7bf40ba08c7 passed 1212 backend tests. An isolated fresh-seed Postgres 17/Redis dev stack on :8090, 1 CPU/1 GiB, accepted large JPEG/PNG/WebP, stored dimensions within 2048px and rejected a 24-megapixel image. Eight/ten simultaneous uploads and subsequent readiness/upload succeeded without OOM/restarts. A 32-request burst exposed database timeouts and unexpected 401 responses; this remains unresolved. The existing decoder unit tests, rather than the HTTP bursts, deterministically exercised permit exhaustion.

## Review policy

Use the installed `/code-review` workflow: eligibility, scoped instructions and summaries, four independent compliance/bug reviews, independent validation of concrete findings, fixes and fresh proof before merge. Reviews remain local unless commenting is explicitly requested. No pre-existing or speculative issues, lint-only findings or style suggestions block these PRs.
