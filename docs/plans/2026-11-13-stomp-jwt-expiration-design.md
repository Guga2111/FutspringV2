# STOMP JWT expiration: approved design and implementation plan

Status: implemented and locally validated; changes remain in the worktree for review.

Reviewed baseline: `dev` at `dced41aed7ed36a8aefab797c52b8ce5bd5244f4`.

## Problem and intended behavior

`JwtChannelInterceptor` validates the access JWT on CONNECT, then retains only the email principal. Subsequent SEND/SUBSCRIBE frames use that principal. The broker delivers subscribed messages without checking the JWT expiration. `StompSessions` handles explicit revocation and member removal, but does not enforce expiration.

A connection must stop authorizing application traffic when the JWT used to establish it expires. The server should close an expired connection even when it is idle. The browser can reconnect using the existing refresh flow and restore its subscriptions.

The running-server regression was established before the fix: the new idle-expiration test failed because the old connection stayed open. It passes after the fix over native WebSocket and SockJS XHR.

## Approved scope

- Enforce the verified JWT's expiration for each STOMP connection, including chat and notification delivery.
- Reuse `AccessTokenVerifier`, `StompSessions`, the application scheduler, and the existing frontend reconnect flow.
- Preserve current membership checks, account identity/version verification, and after-commit revocation behavior.
- Preserve documented logout semantics. Immediate per-login-session WebSocket revocation is outside this change.
- Keep REST payloads, refresh cookies, Redis session format, database schema, and public STOMP destinations unchanged.

## Alternatives and decision

| Approach | Benefit | Trade-off |
| --- | --- | --- |
| Per-connection expiration task plus inbound/outbound expiration checks | Closes idle connections and denies expired traffic even if scheduling is delayed | One scheduled task and a small state entry per authenticated connection |
| Expiration checks on messages only | Prevents expired message authorization | Idle connections remain open; does not meet the closure requirement |
| Periodic sweep plus message checks | Central cleanup task | Adds a sweep interval and delayed idle cleanup |

Approved approach: per-connection expiration task plus inbound/outbound checks. Use the existing scheduler rather than a new executor, configurable sweep interval, or additional service hierarchy.

The comparison follows Pattern 7A, Build-Within-Constraint Formula, from the installed brainstorming skill's `references/pattern-categories-and-documentation.md`:

```text
Generate [number] ideas to [solve challenge] with these hard constraints:
- Constraint 1: [specific limitation that cannot be violated]
- Constraint 2: [specific limitation that cannot be violated]
- Constraint 3: [specific limitation that cannot be violated]

These constraints are MANDATORY, not negotiable.

For each idea generated, explicitly show: How does this work WITHIN these constraints?

Format: Idea | How It Works Within Constraints | What It Enables | Trade-offs
```

For this change, the constraints are server-side enforcement of both directions, reuse of existing session/reconnect ownership, and preservation of the selected logout/API behavior.

## Security invariants

1. Read expiration only from claims whose signature, account id/email, verification status, and token version passed the existing verifier. Do not decode a second unverified copy of the payload.
2. Require a usable expiration for STOMP authentication. Reject expired or missing-expiration credentials; do not assign an unlimited connection lifetime.
3. Treat `now >= expiresAt` as expired. Use the existing injected `Clock` for new lifetime decisions. Review consistency with the JWT parser's clock and timestamp precision.
4. Associate authentication and expiration with the server-established session id. Client-supplied native headers cannot overwrite the deadline or trusted session identity.
5. Authorize SEND/SUBSCRIBE and outbound application delivery only before expiration. Unknown or expired authentication state fails closed. CONNECT/CONNECTED, error and disconnect lifecycle handling must still allow establishment and cleanup without accidentally allowing application traffic.
6. A delayed or failed close must not reopen authorization: message checks remain authoritative while transport cleanup completes.
7. Expiring one connection must not close another connection of the same account that authenticated with a later-valid JWT.
8. Keep at most one expiration task per connection. Disconnect, revocation, setup failure and shutdown release state and cancel outstanding tasks. A stale callback cannot affect a replacement connection.
9. After expiry the browser must authenticate a new connection with a valid token. Refreshing an HTTP token does not silently extend the already-established connection's deadline.

Checks define the authorization point; they cannot retract bytes already transmitted or undo work authorized before the deadline. Place checks at actual channel execution/delivery boundaries rather than relying solely on a check before an asynchronous queue. Document that limit in tests and the final review.

## Ownership and integration

### Verified authentication result

Extend `AccessTokenVerifier` with a small internal value containing verified identity and `Instant expiresAt`. Preserve the existing email-returning HTTP entry point if that avoids unnecessary caller changes; both entry points must share the same parse and account checks. Parse once per authentication attempt and retain no raw JWT in connection state or logs.

`JwtChannelInterceptor` obtains this verified result on CONNECT and registers the lifetime before allowing authenticated application traffic. Continue using the email principal expected by chat and Spring user queues.

### Connection lifetime

Keep lifetime state and transport closure in `StompSessions`, which already owns the transport-session map and revocation behavior. Schedule one expiration callback using the application's named `taskScheduler`; verify lazy resolution if eager injection creates a broker configuration cycle. Do not use the broker heartbeat scheduler accidentally.

Make registration, expiration and cleanup safe when CONNECT, transport close, an immediately-due deadline, or revocation overlap. If lifetime registration/scheduling fails, reject establishment and close the connection. Keep expired traffic blocked if physical closure fails, and log only non-secret diagnostic identifiers.

Use `CloseStatus.POLICY_VIOLATION`, matching existing revocation closure. Cancel scheduled work on normal disconnect and explicit revocation. Application shutdown must clear/cancel remaining work using the existing lifecycle facilities.

### Inbound and outbound enforcement

Wire the shared expiration predicate into the inbound and outbound channels in `WebSocketConfig`. Cover inbound SEND/SUBSCRIBE and outbound application MESSAGE deliveries, including `/user/queue/notifications`. Check the destination session's deadline, not the sender's: the same broadcast may still be valid for other recipients.

Inspect the installed Spring channel/executor behavior before choosing interceptor callbacks. Check at execution time where a frame can otherwise sit in a queue past expiration. Rejecting delivery for one expired recipient must not suppress a valid recipient's broadcast. Prefer extending existing ownership and small private helpers over duplicating maps or parsing logic.

### Browser behavior

`RealtimeProvider.beforeConnect` already uses `freshAccessToken()`, and `onConnect` restores subscriptions. Retain this flow and the current retry/backoff behavior. Prove it works when the server closes a connection for expiration. Make a frontend behavior change only if verification identifies a concrete gap.

## Implementation sequence

### 1. Establish the regression and test seams

- Add a focused running-server STOMP regression with synthetic users and a deliberately short-lived JWT. Show that the current server accepts application traffic after expiration.
- Keep bounded waits only for transport integration checks. Use a controllable clock and scheduler in unit-level lifecycle tests rather than long sleeps or a production TTL change.
- Inspect the local Spring APIs, transport decoration, and channel execution order. Verify both native WebSocket and a supported SockJS HTTP fallback; report a transport-specific gap before relying on closure behavior.

### 2. Expose verified expiration

- Update `AccessTokenVerifier` and its tests to return validated identity/deadline for STOMP while preserving HTTP verification behavior.
- Cover malformed, expired and missing-expiration tokens, stale account ids, deleted/unverified accounts and token-version mismatch.
- Keep request/response contracts unchanged. Confirm existing HTTP and JWT compatibility tests still apply.

### 3. Implement connection deadlines and channel guards

- Extend `StompSessions` with minimal per-connection deadline/task state and idempotent cleanup.
- Register the deadline from the authenticated CONNECT flow in `JwtChannelInterceptor`.
- Add inbound/outbound execution-time checks through `WebSocketConfig`, reusing the same expiry predicate.
- Cover scheduler rejection, delayed callbacks, concurrent close/registration, repeated cleanup, and closure failure without authorizing expired traffic.

### 4. Verify end-to-end recovery and existing controls

- An idle connection closes when its JWT expires, without receiving or sending another application frame first.
- After expiry no new chat send is persisted; subscriptions cannot be added; chat broadcasts and private notification pushes are not delivered to that connection.
- A delayed scheduler still leaves expired inbound/outbound application traffic denied.
- A valid sibling connection of the same account continues working; other broadcast recipients receive their messages.
- Reconnect with an expired token fails; reconnect with a fresh token succeeds and restores authorized subscriptions.
- The browser's normal refresh/reconnect path recovers automatically. Failed refresh never restores access with an expired JWT, and retry attempts remain bounded.
- Existing password-change/reset/account-deletion revocation and member-removal tests remain green, including rollback behavior.
- Pending expiration tasks/state are removed on disconnect, failed setup and application shutdown.

### 5. Update documentation and run required checks

- Update `core/docs/BACKEND.md`: WebSocket lifetime, authorization points, reconnect behavior, and regression coverage.
- Update `client/docs/FRONTEND.md`: expected expiration closure and renewal/reconnection of chat and notifications.
- Run focused authentication/STOMP tests, then `./mvnw test` in `core/`.
- If frontend code changes, run `bun run lint && bun run build && bun run test` in `client/`, plus the focused reconnect check.
- Record actual results, including unavailable environments and skipped tests. Do not describe unexecuted tests as passing. Use only synthetic identities and local services; do not probe production. Follow any invoked review skill's execution restrictions.

### 6. Perform the required security review

- Before opening or marking ready a security-relevant PR, and again before merge, use Trail of Bits `differential-review` as required by `AGENTS.md` and `docs/SECURITY_REVIEW.md`.
- Review the final diff against the then-current `dev`; record base/merge-base/head SHAs, skill revision, report reference and regression evidence.
- Inspect per-recipient outbound authorization, trusted session ids, missing metadata handling, scheduler/resource cleanup, transport coverage and races with existing revocation.
- Resolve confirmed in-scope findings before merge. Keep the full report outside tracked source or in ignored `.security-reviews/` and use a sanitized PR summary.

## Acceptance and rollout

Done means expiration closes idle connections, application traffic is denied after the deadline even if cleanup is delayed, valid browser sessions recover with fresh credentials, unrelated sessions remain usable, and required checks/review have evidence.

No migration or token/cookie-format rollout is planned. Existing connections on an old process retain its behavior until that process is replaced. Deploy using the normal application restart, which drops existing connections so they reconnect through the corrected authentication path. Assess existing reconnect behavior with the production TTL; do not introduce a new lifetime setting or alter the configured TTL for this fix.

Rolling back the application would restore the expiration flaw. Retain the additive internal design and prefer a forward fix if deployment uncovers an issue. This plan does not authorize deployment, PR publication or merge.


## Implementation evidence

- `AccessTokenVerifier.verifyConnection` returns verified email/expiration while the HTTP verifier retains its existing behavior.
- `StompSessions` owns per-transport deadlines, one scheduled callback, inbound/outbound enqueue and execution checks, and cleanup. `WebSocketConfig` registers it on both client channels.
- The existing frontend runtime required no change. A new browser regression exercises renewal, subscription restoration and bounded failure retries against a simulated SockJS/STOMP server.
- Java 25: `./mvnw -o test` passed: 1,254 tests, zero failures/errors/skips, including PostgreSQL/Redis Testcontainers and STOMP transport tests. The focused authentication/STOMP run passed 48 tests.
- `bun run lint`, `bun run build`, and `bun run test` passed; 551 unit tests. Lint retains the documented TanStack React Compiler warning. Build retains existing chunk-size/deprecation warnings.
- The two new browser regressions passed on desktop-light using the installed Chromium (Playwright cache revision 1254), selected through a temporary configuration because the default expected browser revision 1248 is not installed. No browser/dependency download or committed runner configuration change was needed.
- Differential security review evidence is stored locally in ignored `.security-reviews/FUTSPRING_DIFFERENTIAL_REVIEW_2026-11-13.md`. Recheck recorded source hashes and target/head refs before publishing or merging a PR.
