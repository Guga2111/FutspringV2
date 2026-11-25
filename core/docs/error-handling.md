# Error handling

> How exceptions become HTTP responses and the rules for new errors. Part of the [backend guide](README.md).

## Error handling (`S/shared/exception/GlobalExceptionHandler.java`)

| Exception | Status | Body |
|-----------|--------|------|
| `MethodArgumentNotValidException`, `ConstraintViolationException` (list bodies on `@Validated` controllers), `HandlerMethodValidationException` | 400 | `{status, message: "Dados inválidos", timestamp, errors: {field: message}}` (list items as `results[0].team1Score`) |
| `HttpMessageNotReadableException` (malformed JSON, unknown enum value) | 400 | `ErrorResponse` "Corpo da requisição inválido" |
| `MissingServletRequestParameterException`, `MissingServletRequestPartException`, `MethodArgumentTypeMismatchException` | 400 | `ErrorResponse` |
| `MaxUploadSizeExceededException` | 413 | `ErrorResponse` |
| `OptimisticLockingFailureException` | 409 | `ErrorResponse` "Os dados foram alterados em outra operação. Recarregue e tente novamente."; no retry that could replay stale credentials |
| `DataIntegrityViolationException` | 409 | `ErrorResponse` (logged as warning) |
| `HttpRequestMethodNotSupportedException` / `NoResourceFoundException` | 405 / 404 | `ErrorResponse` |
| `AppException` | its status | `ErrorResponse {status, message, timestamp}`; `AppException.field(field, message)` answers the validation 400 body with that one field (a wrong current password); 402 is the read-only pelada of `SubscriptionGuard` |
| any other `Exception` | 500 | `ErrorResponse` "Ocorreu um erro inesperado", logged with `log.error("Unhandled", ex)` |

The 401s from the security entry point and `JwtAuthFilter`, and the 429 from `AuthRateLimitFilter`, are hand-built maps with the same `{status, message, timestamp}` keys. The frontend reads `message` (and `errors` for validation). All messages are in Portuguese.

Rules:
- Throw `AppException` for every expected failure (message in Portuguese); never let a `NullPointerException` or FK violation be the error.
- Add handlers to `GlobalExceptionHandler` for framework exceptions instead of try/catch in services.

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 7 | Catch-all handler that swallows the exception without logging | `log.error` in the catch-all; specific handlers for framework exceptions | — |
