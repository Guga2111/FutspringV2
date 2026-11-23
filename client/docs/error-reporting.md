# Error reporting

> How uncaught browser errors reach the API and what is redacted first. Part of the [frontend guide](README.md).

## Rules

1. Only uncaught errors and CSP violations are reported; errors the code catches show a toast or an error state.
2. Every field passes `redactErrorText` before `sendClientError`; `path` is the pathname only.
3. No third-party SDK.

## How reports are sent

`lib/errorReporting.ts` sends uncaught errors to `POST /api/v1/client-errors`, which writes one log line (Loki, `event=client_error`; see [Observability](../../core/docs/observability.md)). No third party, no SDK. Sources: window `error` and `unhandledrejection` (listeners added by `installErrorReporting()` in `main.tsx`) every route's `ErrorBoundary` (`componentDidCatch`) and Content-Security-Policy violations (a `securitypolicyviolation` listener on `document`, source `CSP`: "CSP enforce script-src-elem: inline" with `at file:line:column` as the stack; violations from browser extensions are ignored, and so is Google Fonts (`fonts.googleapis.com`, `fonts.gstatic.com`), which the app never loads but extensions inject into the page itself, so the violation doesn't name the extension; `buildViolationReport`). Errors the code catches (toasts, error states) are not reported.
- `path` is `location.pathname` only; the query and hash of every URL inside the message and the stack are removed too (`stripUrlQueries`, `http(s)` and `ws(s)` URLs; a reset link carries its token in the query), keeping a frame's `:line:column`.
- Each field is cut to the API's limit; `release` is `__APP_VERSION__`.
- Each message + stack is sent once and at most 10 per page load; "ResizeObserver loop…" and "Script error." without a stack (cross-origin scripts) are ignored; a failed send is ignored.
- In the visual tests a report is a POST without a fixture, so an uncaught error on a screen fails the test.

## Credential redaction

`redactErrorText` sanitizes error messages, stacks and pathname before `sendClientError`: invitation token path segments, URL queries, Bearer values, named secrets and JWTs. Reports from `/join/:token` contain `/join/[REDACTED]`, never the invitation credential. The server independently redacts every logged field. The privacy notice now describes telemetry retention/redaction; frontend `LEGAL_VERSION` and backend `LegalTerms.CURRENT_VERSION` are 2026-11-13.
