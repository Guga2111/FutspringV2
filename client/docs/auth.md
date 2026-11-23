# Auth, invitations and permissions in the UI

> What the client may decide about permissions, invitation flows, auth return destinations, e-mail verification and unsubscribe links. Part of the [frontend guide](README.md).

## Rules

1. UI permission checks only hide controls; the backend decides. Prefer server flags (`daily.isAdmin`, finance `permissions`) to recomputing roles.
2. A 403 from a detail endpoint renders an access-denied state, not a toast loop.
3. Return destinations go through `utils/authReturn.ts` (local paths only).
4. GETs never change state: invitation previews never join, e-mail links act through a POST from the page.
5. No token or credential in browser storage or error reports.

## Auth & permissions in the UI

- Admin checks in the client only decide what is **shown**. The backend enforces every rule (see [Ownership and authorization](../../core/docs/authorization.md)); never rely on hiding a button for security.
- Pelada admin is derived in `PeladaDetailPage` (the pelada layout, shared as `isAdmin` through `usePeladaWorkspace`) from `pelada.members[].isAdmin` / `creatorId`. Session admin uses the server-provided `daily.isAdmin`; prefer server-provided flags when they exist.
- A 403 from a detail endpoint renders an "access denied" state (`accessDenied` in `usePeladaDetail`/`useDailyDetail`), not a toast loop.
- Team color can be edited by a team player or an admin (`TeamsSection`: `canEditColor = isOnTeam || daily.isAdmin`), mirroring the backend rule.

## Invitation contracts and authentication destinations

`api/invitations.ts` mirrors the `/api/v1` invitation endpoints documented in [the backend invitations API](../../core/docs/api-peladas.md#membership-invitations-peladainvitationcontroller): admin POST/GET `/peladas/{id}/invitations` (`pending` filter, `page`, `size=20`), POST cancellation scoped to the pelada, GET/POST/DELETE `/peladas/{id}/invite-link` (GET 204 → null); own GET `/invitations`, GET `/invitations/{id}`, POST accept/decline; public GET `/invite-links/{token}` and authenticated POST `/invite-links/{token}/join`. GETs only read. `invitePlayerSchema` mirrors the positive required `userId`; responses and status labels live in `types/invitation.ts`. Effective expiry comes from the backend; `useNowUntil` also disables an open individual invite at its expiry. The public preview's id is used only for membership/routing; no private detail endpoint is fetched before membership.

Acceptance reloads `MyPeladasContext` and opens the pelada. Link joining lives outside the private shell: navigation mounts the shell and loads the new membership. A link preview never auto-joins, including after login/register. Removed players receive the backend's explanation that an individual invite is required; declined players can still choose to enter by link. `PELADA_INVITATION` notifications carry nullable `invitationId` and link to the dedicated page; `MEMBER_JOINED` is an in-app admin notice. Legacy `ADDED_TO_PELADA` notifications remain readable. Membership e-mail preferences include individual invitations.

`utils/authReturn.ts` preserves a full local destination and rejects external/protocol-relative/backslash redirects. `AuthPage` retains it when switching login/register tabs. A non-auth API 401 logs out and redirects to `/auth?expired=1&returnTo=…`, preserving the full current URL; the expired-session message explains that login is required. Wrong-account invitations offer signing in with another account. Public preview GETs ignore stale backend session headers.

## Email verification

Registration is Promise<void> (204), never logs in or sets a cookie. useRegisterForm opens public `/verification-email` with email in navigation state and the original local returnTo query. This page reuses AuthLayout, shadcn fields, react-hook-form and forgotPasswordSchema, calling requestVerificationEmail(email) / POST `/auth/verification-email`. A generic sent state explains spam and the one-minute cooldown. Login has a verification link and displays the unverified-account 403.

Public `/verify-email?token=` uses verifyEmailSchema `{password}` / VerifyEmailRequest `{token,password}` / verifyEmail(). Verification requires an explicit submission with the account password; success offers login, errors offer resend/password recovery, and missing tokens show an incomplete-link state. No token is stored in browser storage or telemetry. Both new routes use existing semantic tokens and light/dark auth styles. The API request snapshot VerifyEmailRequestDTO.json and account export timestamp match the backend. New and existing users must verify; old sessions are revoked by migration.

## Email preference links

Unsubscribe links remain public, signed and valid for 30 days. useEmailUnsubscribe only turns emails off. NotificationUnsubscribePage links "Entrar para reativar e-mails" to authenticated settings; resubscribeToEmails and the public resubscribe endpoint were removed. Changing email preferences revokes older links. Invalid/expired/legacy links still offer settings. Privacy text documents expiry and authenticated re-enable.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 17 | Client-only permission checks treated as security | Backend checks; UI only hides | — |
