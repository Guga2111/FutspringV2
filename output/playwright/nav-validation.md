# Floating mobile navigation — validation

The approved design extends `BottomTabBar`: a solid 64 px capsule with 12 px side margins, a 12 px bottom gap plus the phone's safe area, an inset rounded active destination and the existing central creation action. Existing routes, unread count, avatar, chat/keyboard visibility and desktop navigation remain owned by the existing implementation. `--mobile-tabbar-h` now reserves the complete occupied footprint. The frontend documentation describes the new layout.

## Evidence

- `bun run lint`: passed, with the documented pre-existing TanStack Table compiler warning.
- `bun run build`: passed. Existing Browserslist, bundle-size and service-worker bundler warnings remain.
- `bun run test`: 61 files, 609 tests passed.
- Official Docker Playwright image, complete mobile suite in both themes: 220 passed, 2 expected skips. Updated 89 affected mobile snapshots and visually inspected their navigation crops in `nav-baselines-1.jpg` through `nav-baselines-4.jpg`; the index maps each crop to its snapshot.
- A second Docker run compared page snapshots without updating: 100 passed, 56 failed. Every mobile page comparison passed. The failures concern the pre-existing desktop sidebar baselines, which still show the old sidebar creation button and spacing. No desktop baselines were updated.
- Reproduced the desktop Home failure in an isolated temporary copy with the original `BottomTabBar.tsx` and `index.css` from `3e4919f` (the approved-design commit, before implementation). Its actual screenshot is byte-for-byte identical to the current actual screenshot: SHA-256 `e54fb52b0331d3f898daeadf7e4358e279c1548d49d104424d5fc35e2201f6b0`. The sidebar discrepancy therefore precedes this change. No unrelated sidebar fix was made.
- `git diff --check`: passed.

## Browser and accessibility

Playwright CLI used an isolated Brave profile and existing fictitious e2e fixtures against the production preview. The service worker was bypassed to prevent a cached release from obscuring the current build. No production account or data was used.

Opened and inspected captures at 375, 600, 768 and 1440 px in both themes, all four selected destinations, keyboard focus, the page end and the finance selection toolbar. `nav-measurements.json` records zero horizontal overflow at every width. At 375 px the capsule is x=12, y=736, width=351 and height=64 in an 812 px viewport. Link targets measure approximately 68.6 × 56 px; the creation target is 48 × 48 px. At 768 px and above the navigation is hidden and its footprint is zero.

The page's last pelada card ends at y=711.6, above the capsule at y=736. The selection toolbar ends at y=668.5. Simulating an open visual keyboard removes the navigation and sets its footprint to zero, then restores it after closing. The existing Docker suite exercises destination changes, creation by role, chat hiding and gestures. Focus is visible in `nav-focus-375-dark.png`.

Design-taste source lint: zero failures or warnings. Its rendered `probe` ran through the authenticated Playwright session, because the standalone inspector's fresh profile cannot inherit mocked authentication. This was a component-scoped measurement, not a claim that the entire pre-existing app passed the standalone inspector. Measured dark-theme destination text contrast ranges from 5.26:1 to 5.83:1 (`nav-probe.json`). The Impeccable detector reported only the inherited Inter family, retained as required by the existing project system.

Physical-device safe-area behavior was not independently exercised: this installed Brave version lacks CDP's safe-area emulation command. The existing `viewport-fit=cover` and CSS `env(safe-area-inset-bottom)` integration are retained; the complete footprint includes the same safe-area value.

## Visual review

The user supplied and approved the direction, so no random style draw or redraw was used. The Awesome DESIGN.md Apple reference was consulted for restrained controls and capsule geometry; the WhatsApp reference and Futspring's own semantic tokens remain authoritative. No fonts, imagery, sample content or dependencies were added.

Independent Impeccable finish reviewer disposition: **ship**, with no material findings in the changed navigation or supplied overlap evidence. The reviewer opened all 13 supplied viewport/state captures plus the original home captures and checked the source, footprint and documentation. It did not independently exercise a browser or a physical device. The later keyboard-focus capture was inspected in the implementation thread.

Component critique: design 8/10, usability 8/10, creativity 7/10, content 8/10; weighted score 7.8/10. The known familiar capsule is intentional, and the inherited stronger light-theme elevation remains visible. No material correction was identified. Existing system documentation is preserved; no replacement `DESIGN.md` is needed.

Security assessment: presentation/layout only. Authentication, authorization, storage, private-image delivery, API contracts and creation permissions were not changed. No PR was opened or merged.
