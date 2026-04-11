# ADR 001 — DailyService Split into Sub-Services

**Date:** 2026-04-11  
**Status:** Accepted

## Context

`DailyService` had grown to over 800 lines and owned every concern related to a daily football session: lifecycle management (create, list, status), attendance confirmation, team sorting and swapping, match result submission, finalization, stats computation, and image upload. This made the class hard to test, hard to reason about, and a frequent source of merge conflicts as multiple features touched the same file.

The project also had duplicated user-lookup boilerplate (`userRepository.findByEmail(...).orElseThrow(...)`) repeated over 30 times across 6 services, and duplicated file-upload logic in 3 services.

## Decision

We split the responsibilities of `DailyService` across five focused classes:

| Class | Responsibility |
|---|---|
| `FileUploadService` | Image upload/delete with MIME/size validation |
| `UserAuthenticationHelper` | Single `getAuthenticatedUser(email)` lookup |
| `DailyAttendanceService` | Confirm/disconfirm attendance (player and admin) |
| `DailyTeamManagementService` | Sort teams, swap players, update team name/color |
| `DailyResultsService` | Submit results, finalize daily, upload champion image, populate from message |
| `DailyService` | Thin orchestrator: createDaily, getDailiesForPelada, updateStatus, deleteDaily, getDailyDetail |

`deleteDaily` was refactored to orchestrate teardown via the sub-services in a specific order: clear results → clear teams → clear attendees → delete daily. This ordering ensures referential integrity (match stats before matches, matches before teams, teams before the daily itself).

The `DailyController` was updated to inject and call the sub-services directly for their respective endpoints, rather than routing everything through `DailyService`. Only the five lifecycle endpoints remain on `DailyService`.

## Consequences

**Positive:**
- `DailyService` is under 230 lines and handles only lifecycle management.
- Each sub-service is independently unit-testable with a small constructor footprint.
- `deleteDaily` no longer duplicates the teardown logic that `clearResults`/`clearTeams`/`clearAttendees` already encapsulate.
- Adding new attendance or results behaviour no longer risks touching lifecycle methods.

**Negative / Trade-offs:**
- `DailyController` now injects four beans instead of one. Controllers are thin and this is acceptable.
- `getDailyDetail` in `DailyService` still reads from multiple repositories directly (teams, matches, stats, awards) because it assembles a read-model DTO. Extracting this into a `DailyQueryService` is a possible future step.
- `finalizeDaily` and `populateFromMessage` in `DailyResultsService` return `void` to avoid a circular dependency; the controller calls `getDailyDetail` after them to build the response.
