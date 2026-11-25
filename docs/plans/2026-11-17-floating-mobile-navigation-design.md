# Floating mobile navigation

Status: approved by the user on 2026-11-17.

## Scope and decisions

Replace the mobile bottom bar's edge-to-edge surface with a solid floating capsule. Keep Início, Peladas, the central create action, Avisos and Perfil in their existing order. The user explicitly chose to keep the create action inside the capsule.

Options considered: a solid capsule with a rounded active destination (selected, closest to the supplied reference); a solid capsule with text/icon selection only (quieter but less faithful); a translucent capsule (contrast depends on underlying content).

Extend the existing `BottomTabBar`, which owns navigation and visibility. No new dependency, router, endpoint or permission rule is needed. Real route links remain links rather than an ARIA tab widget. Compose existing shadcn actions and avatars with semantic HTML navigation.

## Direction contract

- **Thesis:** familiar mobile destinations live in one floating capsule, with an inset rounded active destination matching the supplied screenshot.
- **Own-world:** inherit Futspring's Inter, Hugeicons, semantic surface/foreground tokens, blue creation action and player avatar. Use the existing elevation token on the floating surface; no glass or new palette.
- **Story:** the user recognizes their current destination, reaches the four existing destinations and opens creation from the center.
- **First viewport:** below 768 px, a 64 px capsule sits 12 px from both sides and 12 px above the bottom safe area. Four labeled destinations surround the 44 px creation circle. Touch targets remain at least 48 px.
- **Form and behavior:** preserve route grouping, unread labels and badge, profile identity, active-tab reselect, chat/keyboard hiding and desktop navigation. `--mobile-tabbar-h` represents the complete occupied footprint, including the lower gap and safe area, so page ends and selection toolbars clear the capsule.
- **Signature interaction:** a neutral rounded selected surface and the existing heavier icon/brand text identify the active destination. Brief color/press feedback respects reduced motion and keeps a visible keyboard focus ring.

## Validation

Run frontend lint, production build and unit tests. Verify the production app with mocked existing e2e fixtures in Playwright at 375 px, an intermediate mobile width and the desktop breakpoint, in light and dark themes. Exercise every destination, creation, tab reselect, keyboard/chat hiding, unread badge, scrolling to the last content and selection-toolbar clearance. Preserve desktop behavior. Inspect before/after captures and update affected mobile visual baselines with the repository's official Playwright image when available.

Security impact: presentation and layout only; preserve existing authentication, permission checks, data requests and private-image handling.
