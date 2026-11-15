# Release P2 fixes

Approved scope: cancel HTTP refresh/retry work belonging to a replaced session; restore lazy private photos and share identical reads. Resend configuration is excluded.

Retain the existing session generation but attach it to requests, reject obsolete refresh results/errors and check before retrying. A new session has its own refresh flight; an old finalizer cannot clear it. Delayed 401s within the same session reuse the current token.

Observe each existing photo frame with IntersectionObserver (200px prefetch). Hidden desktop-only thumbnails remain unloaded on mobile. Share one in-memory fetch/object URL per account/file while consumers are mounted; cancel and revoke on final release, ignoring late replies. Keep the authenticated API and no-store policy. No persistence or API response changes.

Tests cover logout/login interleavings, delayed 401s, shared fetch ownership, aborts and late replies, plus browser checks for offscreen and hidden photos.

Validation: lint (0 errors; the existing TanStack Table warning), production build and 551 Vitest tests passed. Official Playwright Docker image: 8 private-photo behavior tests and 20 unchanged snapshots of the five affected pages passed across mobile/desktop and light/dark.
