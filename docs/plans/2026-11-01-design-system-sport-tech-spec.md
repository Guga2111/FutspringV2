# Futspring Design System — "Sport-Tech Minimalism" (Phase 1: Discovery + SPEC)

- **Status:** proposal, waiting for approval. Nothing in this document has been implemented.
- **Date:** 2026-11-01
- **Scope:** `client/` only. No API contract, backend or business-logic change.
- **Phase 2 (implementation)** starts only after explicit approval of this SPEC and of the decisions in [§17](#17-open-questions--pending-decisions).

Conventions used below: **[Fact]** = observed in the repository (file referenced), **[Risk]** = possible consequence, **[Rec]** = recommendation. Paths are relative to `client/` unless they start with `docs/` or `.github/`. Contrast ratios were computed with the WCAG 2.x relative-luminance formula (script kept outside the repo; Phase 2 adds it as a test, see [§15](#15-testing--validation-strategy)).

---

## 1. Executive Summary

The Futspring client already has a sound design-system foundation: shadcn/ui on Radix, Tailwind 3 with **semantic HSL tokens** in `src/index.css` (`:root` + `.dark`), `cva` variants in `components/ui/*-variants.ts`, class-based dark mode and documented rules against raw colors (FRONTEND.md antipattern #3). Most screens consume tokens, so **a large part of the new identity can be delivered by re-pointing tokens**, not by touching components.

What blocks a clean switch to the new identity:

1. **No font is loaded.** The app renders the Tailwind default system stack; there is no `fontFamily` in `tailwind.config.js` and no font file or `<link>` anywhere. Barlow Condensed and Inter are entirely new.
2. **The brand is green and hard-wired to `text-white`.** `--brand` (`#149440`) is consumed through `.bg-gradient-primary` / `Button variant="gradient"` (33 files) with `text-white`. White on Electric Blue `#0799D5` is **3.22:1 (fails AA for normal text)**, so every "white on brand" spot must change foreground, not just color.
3. **Name collision:** shadcn's `--accent` is a *neutral hover surface* (used by `ghost`/`outline` buttons, menus, toggles). The Cantaloupe "accent" cannot reuse that token; it needs a new one.
4. **Islands of hardcoded color** that tokens will not reach: `ComparePlayersDialog` (always dark, `zinc-*`), `ComparablePlayerStatsCard`, `StatsOverTimeChart` / `ProfilePieChart` (hex), `ProfilePage` KPI icons, `MatchHistoryTable`, `CreatePeladaModal` (green focus rings), `LandingPage` (51 raw classes + green keyframes), `lib/utils.ts` avatar/pelada gradients, green `rgba` in `index.css`.
5. **Theme bootstrap gaps:** the saved theme is applied in a module script (`main.tsx`) after CSS is parsed (possible light flash for dark users), no `color-scheme`, no system-preference fallback, `localStorage` read unguarded at startup, Sonner `<Toaster>` has no `theme`, and `<html lang="en">` while the UI is pt-BR.
6. **Accessibility debt in the current tokens:** `--faint-foreground` fails AA in both themes (2.56:1 light, 3.19:1 dark), input borders are ~1.2–1.3:1 (WCAG 1.4.11), and there is no `prefers-reduced-motion` handling while the landing page runs infinite animations.

**Recommended architecture (Option A, see §8.1):** keep the existing token mechanism and names; add a thin, theme-independent **primitive layer** (HSL triplets, not exposed to Tailwind) underneath; re-point the semantic tokens (`--background`, `--card`, `--brand`, `--ring`…) to the new primitives; add `--highlight*` for Cantaloupe and `--brand-foreground`/`--brand-text` for accessible brand usage; add `font-sans` (Inter) and `font-display` (Barlow Condensed) plus a small named type scale. Self-host the fonts. Migrate in 7 incremental, independently shippable steps (§13).

---

## 2. Discovery Findings

### 2.1 Stack and tooling [Fact]

| Item | Value | Source |
|---|---|---|
| Framework | React 19.2, TypeScript ~5.9 (strict), Vite 8 | `package.json` |
| Styling | Tailwind CSS **3.4.19** (`tailwind.config.js`, `postcss.config.js`), `tailwindcss-animate`, `darkMode: ["class"]` | `tailwind.config.js:3` |
| Components | shadcn/ui, style `default`, base color `slate`, CSS variables | `components.json` |
| Primitives | Radix (`@radix-ui/react-*`), `vaul` (Drawer), `cmdk` (Command), `sonner` (toasts), `react-day-picker` | `package.json` |
| Variants | `class-variance-authority`, `tailwind-merge` via `cn()` | `src/lib/utils.ts`, `components/ui/*-variants.ts` |
| Charts / tables | recharts 3 through shadcn `ui/chart.tsx`; TanStack Table 8 | `package.json` |
| Icons | lucide-react only | FRONTEND.md |
| Tests | Vitest 2, 28 `*.test.ts` files (pure logic: utils, schemas, api) — no component/visual tests | `find src -name '*.test.*'` |
| CI | `.github/workflows/ci-frontend.yml`: `bun install --frozen-lockfile`, lint, build, test | FRONTEND.md |

### 2.2 Global style files [Fact]

- **One global stylesheet:** `src/index.css` (366 lines). Contents: Tailwind layers; tokens for `:root` and `.dark` (≈80 variables each); base rules (border color, thin scrollbars, `body { bg-background text-foreground }`); composite classes (`.checkbox-gradient`, `.bg-gradient-primary` with hover `brightness(1.12) scale(1.03)`, `.bg-brand-translucent`, `.text-on-brand-muted`, `.text-gradient-primary`, `.bg-gradient-date`, `.bg-gradient-live`, `.bg-gradient-leader`, photo overlays, `.scrollbar-none`, `.page-enter`); landing keyframes and classes (`.landing-*`).
- **No other CSS** (no CSS modules, no styled-components).
- `index.html`: `<html lang="en">`, favicon `/gerrard.png`, no font links, no inline theme script.

### 2.3 Tailwind configuration [Fact] (`tailwind.config.js`)

- `colors`: every token mapped as `hsl(var(--x))` — the bare-triplet convention, so opacity modifiers (`bg-brand/20`) work.
- Custom groups already present: `card-elevated`, `row-hover`, `subtle-foreground`, `faint-foreground`, `team-dot-border`, `star-off`, `avatar.{1..6,fallback}`, `gold.{DEFAULT,muted,foreground}`, `silver`, `bronze`, `brand`, `success.*`, `warning`, `info.*`, `status.{scheduled,live}.*`, `live.*`, `chart.{1,2}`, `sidebar.*`.
- `borderRadius`: `lg = var(--radius)` (0.5rem), `md`, `sm`, plus fixed `tile: 14px`, `hero: 18px`.
- `boxShadow`: `menu`, `panel`, `dialog`, `fab` — **pure black rgb values tuned for dark**, also applied in light mode.
- **No `fontFamily`, no `fontSize` extension.**

### 2.4 Theme mechanism [Fact]

- `src/main.tsx:9-15`: before `createRoot`, reads `localStorage.getItem('theme')`; adds `.dark` only if it equals `"dark"`; default is **light**; system preference is never read; the read is **not** in try/catch.
- `src/hooks/useTheme.ts`: `useSyncExternalStore` over a `MutationObserver` on `<html class>`; `toggleTheme` toggles `.dark` and writes `localStorage.theme` inside try/catch. Toggle lives in `components/layout/UserMenu.tsx`.
- `src/main.tsx:21`: `<Toaster position="top-center" />` — no `theme` prop, so Sonner renders its default (light) toasts in dark mode.
- No `color-scheme` declaration anywhere, so native controls/scrollbars stay light in dark mode.

### 2.5 Typography today [Fact]

- No font loaded → system UI stack (`ui-sans-serif, system-ui…`), confirmed by the redesign handoff (`docs/design/design_handoff_futspring_redesign/README.md:54`).
- Sizes are mostly **arbitrary values**: `text-[13px]` ×47, `text-[15px]` ×23, `text-[11px]` ×18, `text-[10px]` ×9, `text-[28px]` ×5, `text-[22px]` ×4, `text-[17px]` ×4, `text-[0.8rem]` ×4, and single uses of 9/26/32/38/56 px.
- Weights: `font-semibold` ×152, `font-medium` ×65, `font-bold` ×50, `font-normal` ×39, `font-extrabold` ×5, `font-black` ×2.
- `tabular-nums` used 47 times (scores, tables, counters) — good base for numeric display.
- `font-mono` in `ui/chart.tsx:118`, `ComparePlayersDialog.tsx:120` ("VS"), `DeleteDailyDialog.tsx:92`, `ImportFromMessageModal.tsx:151`.

### 2.6 Hardcoded colors [Fact]

Raw Tailwind palette classes per file (`grep` over `src/**/*.{ts,tsx}`):

| File | Count | Nature |
|---|---|---|
| `pages/LandingPage.tsx` | 51 | green/neutral marketing palette, `dark:fill-[#1e1e1e]` |
| `components/pelada/ComparePlayersDialog.tsx` | 36 | forced dark (`bg-zinc-950`, `text-white`, `border-zinc-800`) |
| `components/pelada/ComparablePlayerStatsCard.tsx` | 19 | same family as above |
| `lib/utils.ts` | 16 | `getPeladaGradient` (8 `from-*-500 to-*-700`), `getAvatarColor` |
| `pages/ProfilePage.tsx` | 15 | KPI icon colors `text-red-400`, `text-orange-400`, `text-green-500`, `text-purple-400` |
| `components/CreatePeladaModal.tsx` | 12 | `focus-visible:ring-green-500`, `data-[state=checked]:bg-green-600` |
| `components/profile/MatchHistoryTable.tsx` | 7 | `bg-green-100 text-green-800 dark:…` highlight |
| `components/pelada/PeladaBanner.tsx`, `daily/ChampionHero.tsx`, `home/HomeBanner.tsx`, `home/MyPeladaCard.tsx`, `PlayerAvatar.tsx`, `PeladaAvatar.tsx` | 1–7 | `text-white`/`bg-black/x` **on photos or colored avatars** — legitimate (theme-independent surfaces) |
| `components/ui/{dialog,alert-dialog,sheet,drawer,sidebar}.tsx` | 1 each | overlay `bg-black/60–80` (shadcn default) |
| `components/ui/button-variants.ts:14`, `ui/calendar.tsx:46`, `pages/AuthPage.tsx:171`, `pelada/ChatPanel.tsx:101`, `pelada/NextSessionCard.tsx:50` | 1 each | `text-white` **on brand** — will fail contrast on blue |

Hex / rgb literals:

| File | Nature | Action |
|---|---|---|
| `profile/StatsOverTimeChart.tsx` (12), `profile/ProfilePieChart.tsx` (3) | chart series `#32CD32`, `#99CB0E`, `#f97316`, `#888` | must move to `--chart-*` (already antipattern #27) |
| `index.css` | `.bg-brand-translucent` `rgb(21 128 61 / .35)`, `.text-on-brand-muted` `#bbf7d0`, landing `#22c55e…`, `rgba(74,222,128,…)` | re-point to tokens |
| `pages/AuthPage.tsx:318-330` | Google logo colors | keep (third-party brand mark) |
| `utils/parseSessionMessage.ts:26-31`, `daily/TeamsSection.tsx:127` | **team colors** (data from users/backend) | keep — data, not theme |

### 2.7 Brand token consumers [Fact]

- `Button variant="gradient"`: 33 files (auth, pelada, daily, finance, settings, profile…).
- `.bg-gradient-primary` direct: `ui/calendar.tsx`, `AuthPage.tsx`, `ChatPanel.tsx`, `EditProfileModal.tsx:93`, Progress indicators in `UpcomingSessionCard`, `NextSessionCard`, `AttendanceSummaryCard`, `AttendanceRankingDialog`.
- `.text-gradient-primary` (logo wordmark): `AppSidebar.tsx:38`, `MobileTopBar.tsx:14`, `auth/AuthLayout.tsx:12`.
- `bg-brand` / `text-brand` / `border-brand`: `NotificationItem.tsx:57` (unread dot), `ResetLinkSent.tsx:16`, `NotificationUnsubscribePage.tsx:68-70`, `FinanceCreateForm.tsx:29`, `ui/tabs-variants.ts:22` (underline active).
- `.checkbox-gradient`: `ui/checkbox.tsx`. `.bg-gradient-date` + `.text-on-brand-muted`: `NextSessionCard.tsx:50-53`. `.bg-brand-translucent`: `HomeBanner.tsx:26`.
- `bg-primary`/`text-primary` (the **neutral** inverted primary, not the brand): `MembersGrid`, `MemberCard`, `AttendanceRankingDialog`, `TeamsSection`, `MatchHistoryTable`, `ErrorBoundary`, `AuthPage`, plus `toggle` `chip` on-state and default `Button`.

### 2.8 Accessibility today [Fact]

- Focus: shadcn primitives use `focus-visible:ring-2 ring-ring ring-offset-2` (12 files). Current `--ring` is near-black (light) / `0 0% 68%` (dark).
- 77 `aria-label`, 16 `sr-only`, 3 `aria-live`, 2 `role="status"`.
- **No `prefers-reduced-motion`** handling (`grep` found none, no `motion-safe/reduce` utilities). Infinite animations: `.landing-hero-bg` (8 s), `.landing-float`, `.landing-blob(-2)`, `.landing-pulse-ring`; transforms on `.bg-gradient-primary:hover` (`scale(1.03)`).
- `<html lang="en">` (`index.html:2`) while all text is pt-BR.
- Responsive: mobile-first, `md:` (768 px) breakpoint shared with `useIsMobile`; screens are expected to work at 375 px (CLAUDE.md).

### 2.9 Domain components relevant to the sport identity [Fact]

| Area | Components |
|---|---|
| Scores / matches | `daily/ScoreStepper.tsx` (28 px bold tabular value), `ResultsMatchCard.tsx`, `SavedMatchesList.tsx`, `ResultsDialog.tsx`, `LiveSessionCard.tsx` |
| Tables / ranking | `daily/LiveLeagueTable.tsx`, `daily/FinishedSessionTabs.tsx`, `pelada/RankingTable.tsx` (medals for top 3), `pelada/AttendanceRankingDialog.tsx` |
| Awards / champion | `daily/ChampionHero.tsx`, `daily/DailyAwardsGrid.tsx`, `pelada/AwardsTab.tsx` (`.bg-gradient-leader`) |
| Players / stats | `PlayerAvatar.tsx`, `StarRow.tsx`, `pelada/MemberCard.tsx`, `pelada/PlayerHistoryDialog.tsx` (reference chart), `pelada/ComparePlayersDialog.tsx`, `profile/KpiCard.tsx`, `profile/StatsOverTimeChart.tsx`, `profile/ProfilePieChart.tsx`, `profile/MatchHistoryTable.tsx` |
| Session / status | `daily/DailyStatusBadge.tsx` + `types/daily.ts` (`dailyStatusStyle`), `pelada/NextSessionCard.tsx`, `home/UpcomingSessionCard.tsx`, `MembershipBadge.tsx`, `SelfConfirmButton.tsx` |
| Shell | `layout/AppSidebar.tsx`, `MobileTopBar.tsx`, `UserMenu.tsx`, `SidebarPeladaItem.tsx`, `pelada/PeladaBanner.tsx`, `home/HomeBanner.tsx` |

---

## 3. Current Architecture

```
index.html ──► main.tsx (theme class from localStorage) ──► App.tsx (lazy pages)
                     │
                     └─ import './index.css'
                              │  :root / .dark  →  --token: H S% L%
                              ▼
tailwind.config.js  colors.x = hsl(var(--x))   radius = var(--radius)
                              ▼
components/ui/*  (shadcn, cva variants in *-variants.ts)  ──►  feature components (bg-card, text-muted-foreground…)
```

- **One source of truth per value** (the CSS variable), consumed through Tailwind semantic classes. Theme switching = toggling `.dark` on `<html>`; no React context.
- Composite visual effects live as plain classes in `index.css` (not Tailwind plugins).
- Variants are kept in separate `*-variants.ts` files for fast refresh (`badge-variants.ts`, `button-variants.ts`, `tabs-variants.ts`, `toggle-variants.ts`).
- Charts read `hsl(var(--chart-n))` through `ChartConfig` (reference `PlayerHistoryDialog.tsx`).

**Assessment:** the architecture is appropriate for the change. It already has a semantic layer, both themes, Tailwind opacity support and a documented contract. **No new theme engine is needed.**

---

## 4. Current Design System Audit

| Dimension | State | Verdict |
|---|---|---|
| Color tokens | Complete semantic set + redesign extensions; light = stock shadcn slate, dark = neutral grays (`0 0% x%`, no hue) | Good structure, values to replace |
| Brand | Single `--brand` green; classes named `gradient` but solid since the redesign (FRONTEND.md) | Rename debt; foreground hardcoded `text-white` |
| Accent | `--accent` = neutral hover surface (shadcn semantics) | Must not be repurposed |
| Status / feedback | `success`, `warning`, `info`, `destructive*`, `status-*`, `live-*`, `gold/silver/bronze` | Good; `info` (blue `200 98% 39%`) will look like the new brand |
| Typography | No families, arbitrary px sizes, no scale tokens | Biggest gap |
| Radius | `--radius: .5rem`, `tile 14px`, `hero 18px`, buttons `rounded-full` | Coherent, keep |
| Shadows | Black, dark-tuned, used in light too | Acceptable; tune per theme later |
| Motion | `tailwindcss-animate`, `.page-enter`, landing keyframes; no reduced-motion | Gap |
| Components | 33 shadcn primitives installed; variants via cva | Good |
| Documentation | FRONTEND.md "Styling & design system" + antipatterns table | Good; must be updated with the change |

Contrast of **current** tokens (selected):

| Pair | Ratio | AA normal text (4.5) |
|---|---|---|
| light `--muted-foreground` `#64748B` on card `#FFF` | 4.76 | pass |
| light `--faint-foreground` `#94A3B8` on card | **2.56** | **fail** |
| dark `--subtle-foreground` `#8A8A8A` on card `#262626` | 4.38 | **fail** (borderline) |
| dark `--faint-foreground` `#737373` on card | **3.19** | **fail** |
| white on current `--brand` `#149440` | 3.93 | **fail** (normal text) |
| light `--input` `#E2E8F0` vs card (non-text 3:1) | 1.23 | **fail 1.4.11** |
| dark `--input` `#383838` vs card | 1.29 | **fail 1.4.11** |

---

## 5. Problems & Technical Debt

| # | Problem | Evidence | Impact on this migration |
|---|---|---|---|
| P1 | No font loading strategy | `index.html`, `tailwind.config.js` | New: fonts, preload, fallback metrics |
| P2 | `text-white` hardwired on brand | `button-variants.ts:14`, `calendar.tsx:46`, `AuthPage.tsx:171`, `ChatPanel.tsx:101`, `NextSessionCard.tsx:50` | Fails AA on Electric Blue (3.22:1) |
| P3 | Hardcoded green outside tokens | `index.css` `.bg-brand-translucent`, `.text-on-brand-muted`, landing keyframes; `CreatePeladaModal.tsx` | Would stay green after the switch |
| P4 | Theme-locked component | `ComparePlayersDialog.tsx`, `ComparablePlayerStatsCard.tsx` | Breaks light mode and the new palette |
| P5 | Chart hex colors | `StatsOverTimeChart.tsx`, `ProfilePieChart.tsx` | Off-palette; antipattern #27 |
| P6 | Raw gradient palette for pelada/avatars | `lib/utils.ts:16-23`, `getAvatarColor` | Off-palette next to the new identity |
| P7 | Tertiary text and input borders fail WCAG | §4 table | Must be fixed when re-pointing |
| P8 | Theme applied after CSS, unguarded `localStorage`, no `color-scheme`, no system default | `main.tsx:9-15` | Flash risk, crash risk if storage throws, native controls light in dark |
| P9 | Toaster ignores theme | `main.tsx:21` | Light toasts on Midnight Black |
| P10 | `lang="en"` | `index.html:2` | Screen readers use English phonetics (WCAG 3.1.1) |
| P11 | No reduced-motion | `index.css` | WCAG 2.2.2 / 2.3.3 |
| P12 | Arbitrary type sizes | §2.5 | No scale to map Barlow/Inter roles to |
| P13 | Misleading class names (`gradient` that is solid) | FRONTEND.md, `index.css` | Rename while touching brand |
| P14 | `--info` ≈ new brand hue | `index.css` `--info: 200 98% 39.4%` | Mensalista badge and brand become indistinguishable |

---

## 6. Goals & Non-Goals

**Goals**

1. Apply the Sport-Tech Minimalism identity (palette, Barlow Condensed + Inter) across the app in light and dark.
2. Keep the current token architecture, class names consumed by features, shadcn components and the `.dark` mechanism.
3. Every text/background pair used by tokens meets WCAG 2.2 AA; UI component boundaries and focus indicators meet 3:1.
4. Remove the hardcoded color islands listed in §5 (P2–P6), closing FRONTEND.md antipatterns #3 and #27 entries.
5. No theme flash on load; theme preference kept in `localStorage.theme`; Toaster follows the theme.
6. Fonts self-hosted, subset to Latin, with `font-display: swap` and metric-compatible fallbacks; no layout regression at 375 px.

**Non-Goals**

- Tailwind 4 migration, a new theming library (e.g. `next-themes`), CSS-in-JS.
- Changing layouts, information architecture, routes, copy or business logic.
- Redesigning individual screens beyond what the tokens/typography imply (no new components).
- Changing team colors (user data) or third-party logos.
- Logo/favicon redesign (asset decision, see §17).
- Visual regression infrastructure as a mandatory deliverable (proposed as optional, §15).

---

## 7. Proposed Visual Identity

### 7.1 Roles

| Primitive | Hex | Role in the system |
|---|---|---|
| Midnight Black | `#07070D` | Dark page background; **foreground on brand and highlight fills (both themes)** |
| Dark Surface | `#141820` | Dark cards/popovers |
| Electric Blue | `#0799D5` | Brand fill (CTA, progress, checkbox, active tab, unread dot), focus ring in dark |
| Cantaloupe Orange | `#FF7356` | Highlight: gamification (champion, MVP/award accents, streaks, "ao vivo" emphasis), never for destructive meaning |
| Off-White | `#F6F7F9` | Light page background; dark-mode foreground |
| Muted | `#8793A5` | Dark secondary text (passes AA on dark surfaces) |

Complementary colors derived for accessibility (all computed):

| Name | Hex | Why |
|---|---|---|
| Blue Strong | `#0A6E99` | Blue *as text/ring* on light surfaces (`#0799D5` is only 3.00:1 on Off-White) |
| Blue Bright | `#3BB4E8` | Blue hover fill / blue text on dark elevated surfaces (7.52:1 on `#141820`) |
| Orange Strong | `#B4442B` | Orange *as text* on light (`#FF7356` is 2.68:1 on white) |
| Ink | `#0B0D12` | Light-mode foreground (18.13:1 on Off-White) |
| Slate 600 | `#5B6676` | Light secondary text (5.43:1 on Off-White) |
| Midnight 900 | `#0E1117` | Dark sidebar (one step under the page) |
| Surface 700 | `#1C212B` | Dark elevated (table headers, cards in cards) |
| Surface 600 | `#262C38` | Dark dividers |
| Line light | `#DDE1E8` | Light dividers |

### 7.2 Contrast validation (proposed pairs)

| Foreground | Background | Ratio | Use | Result |
|---|---|---|---|---|
| `#F6F7F9` | `#07070D` | 18.74 | dark body text | AAA |
| `#F6F7F9` | `#141820` | 16.58 | dark card text | AAA |
| `#8793A5` | `#141820` / `#1C212B` | 5.71 / 5.18 | dark muted text | AA |
| `#808A9C` | `#141820` / `#1C212B` | 5.11 / 4.63 | dark tertiary text | AA |
| `#07070D` | `#0799D5` | 6.24 | text on brand fill | AA |
| `#FFFFFF` | `#0799D5` | **3.22** | — | **fail normal text** (AA large only) |
| `#0799D5` | `#141820` | 5.52 | brand text/ring in dark | AA |
| `#0799D5` | `#F6F7F9` | **3.00** | — | **fail text**; OK only as non-text ≥3:1 (borderline) |
| `#0A6E99` | `#F6F7F9` / `#FFF` / `#EDEFF3` | 5.29 / 5.67 / 4.93 | brand text, link, ring in light | AA |
| `#07070D` | `#FF7356` | 7.50 | text on highlight fill | AAA |
| `#FFFFFF` | `#FF7356` | **2.68** | — | **fail** |
| `#FF7356` | `#141820` | 6.64 | highlight text in dark | AA |
| `#B4442B` | `#FFF` / `#F6F7F9` / `#EDEFF3` | 5.52 / 5.15 / 4.80 | highlight text in light | AA |
| `#0B0D12` | `#F6F7F9` | 18.13 | light body text | AAA |
| `#5B6676` | `#FFF` / `#F6F7F9` / `#EDEFF3` | 5.82 / 5.43 / 5.06 | light muted text | AA |
| `#626D7E` | `#FFF` / `#F6F7F9` / `#EDEFF3` | 5.24 / 4.89 / 4.55 | light tertiary text | AA |
| `#646F82` | `#141820` / `#1C212B` | 3.50 / 3.18 | dark input border (non-text) | ≥3:1 |
| `#7A8699` | `#FFF` / `#F6F7F9` | 3.69 / 3.44 | light input border (non-text) | ≥3:1 |
| `#B4442B` | `#FFF1EE` | 5.01 | light highlight badge | AA |
| `#FF7356` | `#2A1612` | 6.41 | dark highlight badge | AA |
| `#0A6E99` | `#E6F5FC` | 5.09 | light brand-muted badge | AA |
| `#3BB4E8` | `#0B2633` | 6.63 | dark brand-muted badge | AA |
| `#F87171` / `#DC2626` | dark card / white | 6.43 / 4.83 | destructive text | AA |
| `#4ADE80` / `#15803D` | dark card / white | 10.20 / 5.02 | success text | AA |
| `#FACC15` / `#A16207` | dark card / white | 11.61 / 4.92 | gold text | AA |

**Rule derived from this table:** brand and highlight *fills* always carry Midnight text (`--brand-foreground`, `--highlight-foreground`), in both themes. Brand/highlight *as text* uses the theme-specific `--brand-text` / `--highlight-text`.

---

## 8. Design Tokens Architecture

### 8.1 Options considered

| Option | Description | Pros | Cons |
|---|---|---|---|
| **A. Re-point existing semantic tokens + primitive layer (recommended)** | Add theme-independent primitives (`--p-*` triplets) in `:root`; semantic tokens in `:root`/`.dark` reference them; feature classes unchanged | Minimal component churn; keeps shadcn compatibility, Tailwind opacity modifiers, docs; primitives document the palette | One extra indirection in `index.css` |
| B. Re-point semantic tokens with literal values (no primitives) | Only change the triplets | Smallest diff | Palette repeated in two themes; harder to audit/test contrast |
| C. New token system (Tailwind 4 `@theme`, or a JS token package / Style Dictionary) | Rebuild | Modern pipeline | Tailwind 4 upgrade + shadcn regeneration; large risk; out of scope |

### 8.2 Layers

```
Primitive (theme-independent, :root only, NOT in tailwind.config)
   --p-midnight-950  --p-blue-500  --p-orange-400 …
        ▼ referenced by
Semantic (per theme, :root and .dark; mapped in tailwind.config as today)
   --background  --card  --brand  --brand-foreground  --highlight  --ring …
        ▼ consumed by
Component variants (cva) and feature classes
   bg-card  text-muted-foreground  Button variant="brand"
```

Primitives are deliberately **not exposed** to Tailwind, so components cannot bypass semantics (enforces antipattern #3). CSS custom properties substitute triplets before `hsl()` evaluation, so `--brand: var(--p-blue-500)` keeps `hsl(var(--brand) / 0.2)` working.

Example (documentation only — not applied):

```css
:root {
  /* primitives */
  --p-midnight-950: 240 30% 3.9%;   /* #07070D */
  --p-blue-500:     197 94% 43.1%;  /* #0799D5 */
  --p-blue-700:     198 88% 32%;    /* #0A6E99 */
  --p-orange-400:   10 100% 66.9%;  /* #FF7356 */
  /* semantic, light */
  --brand: var(--p-blue-500);
  --brand-foreground: var(--p-midnight-950);
  --brand-text: var(--p-blue-700);
  --ring: var(--p-blue-700);
}
.dark {
  --brand-text: var(--p-blue-500);
  --ring: var(--p-blue-500);
}
```

### 8.3 Primitive tokens

| Token | Hex | HSL triplet |
|---|---|---|
| `--p-midnight-950` | `#07070D` | `240 30% 3.9%` |
| `--p-midnight-900` | `#0E1117` | `220 24% 7.3%` |
| `--p-surface-800` | `#141820` | `220 23% 10.2%` |
| `--p-surface-700` | `#1C212B` | `220 21% 13.9%` |
| `--p-surface-600` | `#262C38` | `220 19% 18.4%` |
| `--p-slate-550` | `#646F82` | `218 13% 45.1%` |
| `--p-slate-500` | `#8793A5` | `216 14% 58.8%` |
| `--p-slate-520` | `#808A9C` | `219 12% 55.7%` |
| `--p-slate-450` | `#7A8699` | `217 13% 53.9%` |
| `--p-slate-650` | `#626D7E` | `216 12% 43.9%` |
| `--p-slate-700` | `#5B6676` | `216 13% 41%` |
| `--p-line-200` | `#DDE1E8` | `218 19% 88.8%` |
| `--p-mist-100` | `#EDEFF3` | `220 20% 94.1%` |
| `--p-offwhite-50` | `#F6F7F9` | `220 20% 97.1%` |
| `--p-white` | `#FFFFFF` | `0 0% 100%` |
| `--p-ink-950` | `#0B0D12` | `223 24% 5.7%` |
| `--p-blue-300` | `#3BB4E8` | `198 79% 57.1%` |
| `--p-blue-500` | `#0799D5` | `197 94% 43.1%` |
| `--p-blue-700` | `#0A6E99` | `198 88% 32%` |
| `--p-blue-50` | `#E6F5FC` | `199 79% 94.5%` |
| `--p-blue-950` | `#0B2633` | `200 65% 12.2%` |
| `--p-orange-400` | `#FF7356` | `10 100% 66.9%` |
| `--p-orange-700` | `#B4442B` | `11 61% 43.7%` |
| `--p-orange-50` | `#FFF1EE` | `11 100% 96.7%` |
| `--p-orange-950` | `#2A1612` | `10 40% 11.8%` |

Exact numbering is a naming proposal; Phase 2 may rename as long as the table and the contrast test stay in sync.

### 8.4 Semantic tokens (existing names kept)

| Token | Light | Dark | Notes |
|---|---|---|---|
| `--background` | offwhite-50 | midnight-950 | |
| `--foreground` | ink-950 | offwhite-50 | |
| `--card`, `--popover` | white | surface-800 | |
| `--card-foreground`, `--popover-foreground` | ink-950 | offwhite-50 | |
| `--card-elevated` | mist-100 | surface-700 | |
| `--row-hover` | `220 20% 95.5%` | `220 22% 12%` | between card and elevated |
| `--primary` | ink-950 | offwhite-50 | **kept neutral** (default Button, chips on-state); see Q2 |
| `--primary-foreground` | offwhite-50 | midnight-950 | |
| `--secondary`, `--muted`, `--accent` | mist-100 | surface-700 | `--accent` stays the shadcn hover surface |
| `--secondary-foreground`, `--accent-foreground` | ink-950 | offwhite-50 | |
| `--muted-foreground` | slate-700 | slate-500 | 5.43 / 5.71 |
| `--subtle-foreground` | slate-700 | slate-500 | merged with muted (today they differ by <1 %) |
| `--faint-foreground` | slate-650 | slate-520 | fixed to AA (was failing) |
| `--border` | line-200 | surface-600 | decorative dividers (no 3:1 requirement) |
| `--input` | slate-450 | slate-550 | form-control boundary, ≥3:1 |
| `--ring` | blue-700 | blue-500 | focus indicator ≥3:1 vs adjacent colors |
| `--brand` | blue-500 | blue-500 | fill |
| `--brand-foreground` **(new)** | midnight-950 | midnight-950 | text/icons on brand fill |
| `--brand-hover` **(new)** | blue-300 | blue-300 | replaces `filter: brightness()` hover; 8.50:1 with midnight |
| `--brand-text` **(new)** | blue-700 | blue-500 | brand as text/icon on surfaces |
| `--brand-muted` **(new)** | blue-50 | blue-950 | translucent brand pills (replaces `.bg-brand-translucent`) |
| `--highlight` **(new)** | orange-400 | orange-400 | Cantaloupe fill |
| `--highlight-foreground` **(new)** | midnight-950 | midnight-950 | |
| `--highlight-text` **(new)** | orange-700 | orange-400 | |
| `--highlight-muted` **(new)** | orange-50 | orange-950 | badges/tiles |
| `--chart-1` | blue-700 | blue-500 | primary series (goals) |
| `--chart-2` | orange-700 | orange-400 | second series (assists) |
| `--chart-3` **(new)** | slate-700 | slate-500 | third series (matches) |
| `--info` / `--info-muted` | re-tuned to a violet/indigo or neutral-blue distinct from brand | | see Q4 (P14) |
| `--success*`, `--warning`, `--destructive*`, `--gold*`, `--silver`, `--bronze`, `--status-*`, `--live-*` | keep hues; re-tune lightness for the new surfaces; validate each pair in the contrast test | | `--live-*` stays green ("ao vivo") unless Q5 says highlight |
| `--avatar-1…6`, `--avatar-fallback`, `--star-off`, `--team-dot-border` | keep; re-check white-on-avatar ≥4.5 for initials | | |
| `--sidebar-*` | sidebar = offwhite-50 / midnight-900; primary = brand-text; ring = ring | | |
| `--overlay` **(new, optional)** | `240 30% 3.9% / .6` | same | replaces `bg-black/60–80` in `ui/*` overlays (Midnight tint) |

### 8.5 Radius, spacing, shadows, states

- **Radius:** keep `--radius: 0.5rem`, `tile 14px`, `hero 18px`, pill buttons. Sport-tech minimalism is served by flat surfaces and tight borders, not by radius change. (Q7 if a sharper 6 px is wanted.)
- **Spacing:** no new tokens; Tailwind's scale is used everywhere.
- **Shadows:** keep names; in light mode lower opacity via theme variables (`--shadow-color`) — optional, low priority.
- **Interaction states** (documented, implemented in variants):

| State | Rule |
|---|---|
| Hover (brand) | `bg-brand-hover`; no `scale()` (motion-sensitive, causes layout jiggle) |
| Hover (neutral) | existing `hover:bg-accent` / `bg-row-hover` |
| Active/pressed | `brightness-95` or `bg-brand/90`; optional |
| Focus-visible | `ring-2 ring-ring ring-offset-2 ring-offset-background` (unchanged utility; new color) |
| Selected (tabs underline, chips, checkbox) | brand fill or `border-brand` + `brand-foreground` check |
| Disabled | existing `opacity-50` + `pointer-events-none`; disabled text is exempt from contrast |
| Error | `destructive` / `aria-invalid` existing styling |

### 8.6 Component-level tokens

Only justified ones: `--brand-*` (Button, Checkbox, Progress, Tabs underline, Calendar selected), `--highlight-*` (champion/award/streak accents), `--overlay`. No per-component token files.

### 8.7 Class renames (compatibility)

| Today | Proposed | Compatibility |
|---|---|---|
| `Button variant="gradient"` | `variant="brand"` | keep `gradient` as an alias for one release, then codemod the 33 files |
| `.bg-gradient-primary` | Tailwind `bg-brand` + `hover:bg-brand-hover` | keep class temporarily mapped to the new tokens |
| `.text-gradient-primary` | `text-brand-text` | same |
| `.checkbox-gradient` | `border-brand data-[state=checked]:bg-brand` in `ui/checkbox.tsx` | remove class after migration |
| `.bg-gradient-date` | `bg-brand text-brand-foreground` | |
| `.bg-brand-translucent` / `.text-on-brand-muted` | `bg-brand-muted text-brand-text border-brand/30` | |

---

## 9. Typography System

### 9.1 Families

| Token | Stack | Use |
|---|---|---|
| `font-sans` (Tailwind default override) | `"Inter Variable", Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | everything by default |
| `font-display` **(new)** | `"Barlow Condensed", "Arial Narrow", "Roboto Condensed", ui-sans-serif, sans-serif` | scores, rankings positions, big numbers, page/hero titles, date blocks |
| `font-mono` | unchanged | code-like text (`DeleteDailyDialog`, import textarea) |

Rules:

- Barlow Condensed **never below 16 px** and never for paragraphs, labels, form text, table body text or buttons.
- Uppercase display text uses `tracking-wide` (≈0.02–0.04em); Inter uppercase labels keep `0.06em`.
- Numbers in Barlow and Inter use `tabular-nums` where they align (tables, scores, counters). **Phase 2 must verify** Barlow Condensed's `tnum` support in the delivered files; if absent, keep tabular numbers in Inter and use Barlow only for single, non-aligned figures.
- "VS" in `ComparePlayersDialog` moves from `font-mono` to `font-display`.

### 9.2 Scale (named Tailwind `fontSize` tokens)

Mapped from the sizes already in use (§2.5), so migration is a rename, not a redesign.

| Token | Size / line height | Family · weight | Tracking | Replaces | Example usage |
|---|---|---|---|---|---|
| `text-overline` | 11 / 16 | Inter 600, uppercase | 0.06em | `text-[11px]`, `text-[10px]` labels | section labels "MINHAS PELADAS" |
| `text-caption` | 12 / 16 | Inter 400–500 | 0 | `text-xs` | meta lines |
| `text-body-sm` | 13 / 18 | Inter 400–500 | 0 | `text-[13px]` | chips, tables, secondary |
| `text-body` | 14 / 20 | Inter 400 | 0 | `text-sm` | default UI text |
| `text-body-lg` | 15 / 22 | Inter 400–500 | 0 | `text-[15px]` | card lead lines |
| `text-title` | 17 / 24 | Inter 600 | -0.005em | `text-[17px]` | section h2, card titles |
| `text-heading` | 22 → 28 (md) / 1.15 | Barlow Condensed 700 | 0.01em | `text-[22px]`, `text-[28px]` h1 | page h1 (`PeladaPageHeader`), date "Qui, 08 out" |
| `text-display` | 32 → 44 (md) / 1.05 | Barlow Condensed 800 | 0.01em | `text-[32px]`, `text-[38px]` | Home greeting, champion name |
| `text-score` | 28 → 36 (md) / 1 | Barlow Condensed 700, tabular | 0 | `ScoreStepper` lg `text-[28px]` | scores, KPI values, rank numbers |
| `text-hero` | 48 → 56 / 1 | Barlow Condensed 800 | 0 | `text-[56px]` | banner initials, landing hero |

Responsiveness: steps use the existing `md:` breakpoint (no `clamp()` needed; keeps behavior predictable at 375 px). Body text never goes below 13 px; `text-[9px]`/`text-[10px]` content (badges) is reviewed in Phase 2 — WCAG has no minimum size, but 11 px is the proposed floor.

Weights shipped: Inter variable (covers 400–700 in one file); Barlow Condensed static **600, 700, 800** only (≈3 files).

### 9.3 Loading strategy

| Option | Pros | Cons |
|---|---|---|
| **Self-host via `@fontsource-variable/inter` + `@fontsource/barlow-condensed` (recommended)** | Bundled and hashed by Vite, same origin (no third-party request/IP leak, works with the VPS nginx), cacheable, offline dev | Two new dependencies (needs approval — Phase 1 forbids installing) |
| Copy `.woff2` files into `public/fonts` + `@font-face` in `index.css` | No dependency | Manual updates, license file to keep |
| Google Fonts `<link>` | Zero bundle work | Third-party request, privacy (LGPD), extra DNS/TLS, CSP implications |

Common to every option: Latin subset only (pt-BR diacritics are in Basic Latin + Latin-1), `font-display: swap`, `<link rel="preload" as="font" type="font/woff2" crossorigin>` for **Inter regular** only (the most visible on first paint), and a fallback `@font-face` with `size-adjust`/`ascent-override` for Inter → Arial to limit CLS (values to be measured in Phase 2). Budget: ≤ 150 KB woff2 total.

---

## 10. Dark/Light Theme Strategy

Keep: `.dark` class on `<html>`, `darkMode: ["class"]`, `localStorage.theme` (`"dark" | "light"`), `useTheme` (no change to its API).

Changes (all small, in existing files):

1. **Bootstrap before first paint:** move the logic from `main.tsx:9-15` to a tiny inline `<script>` in `index.html` `<head>` (before the stylesheet link Vite injects), wrapped in try/catch:
   ```html
   <script>
     try {
       var t = localStorage.getItem("theme");
       var dark = t ? t === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
       document.documentElement.classList.toggle("dark", dark);
     } catch (e) {}
   </script>
   ```
   `main.tsx` then stops touching the class. (No CSP is defined in this repository; the VPS nginx config is not tracked, so Phase 2 must check it. If a CSP exists or is added, allow the script by hash.)
2. **Default when nothing is saved:** follow the OS (`prefers-color-scheme`) — **pending decision Q1**; alternative: default to dark, the "reference" theme. Explicit user choice always wins. No live OS listener (keeps `useTheme` simple).
3. **`color-scheme`:** `:root { color-scheme: light } .dark { color-scheme: dark }` in `index.css`, so scrollbars, date inputs and autofill follow the theme.
4. **`<meta name="theme-color">`** for both schemes (mobile browser chrome = background token).
5. **Sonner:** pass `theme={isDark ? "dark" : "light"}` to `<Toaster>` (needs the component to live under something that reads `useTheme`, e.g. a tiny `ThemedToaster` in `main.tsx` or `App.tsx`) and style toasts with tokens (`toastOptions.classNames`).
6. **No transition on theme switch** (avoid animating every color); optional `disable-transitions` class during toggle if flicker is observed.
7. Photo overlays (`.bg-*-overlay`) and white text on photos stay theme-independent (already documented).

---

## 11. Component Migration Matrix

Legend — **Auto**: changes through tokens only, no file edit. **Explicit**: file must be edited.

### 11.1 Automatic (tokens only)

| Component | Files | Change | Priority | Risk |
|---|---|---|---|---|
| shadcn surfaces & text | `ui/card, dialog, alert-dialog, sheet, drawer, popover, dropdown-menu, command, select, table, tabs (default/pill), tooltip, alert, skeleton, separator, breadcrumb, field, label, input, textarea, switch, toggle, toggle-group, avatar, progress (track), sidebar` | New neutrals, borders, input border, ring | P0 | Low — verify input border looks heavier (by design, 3:1) |
| Tabs underline active | `ui/tabs-variants.ts:22` (`border-brand`) | Blue underline | P0 | Low |
| Status / feedback badges | `daily/DailyStatusBadge.tsx`, `types/daily.ts`, `MembershipBadge.tsx`, `ui/badge-variants.ts` | Re-tuned status/info tokens | P1 | Medium — `info` vs brand (Q4) |
| Domain lists/tables | `LiveLeagueTable`, `RankingTable`, `FinishedSessionTabs`, `AttendanceList`, `SessionHistoryList`, `MembersGrid`, `MemberCard`, finance components, notifications | Surfaces/text | P1 | Low |
| Charts using tokens | `pelada/PlayerHistoryDialog.tsx`, `daily/ImportFromMessageModal.tsx` | `--chart-1/2` become blue/orange | P1 | Low |
| Awards leader / live card | `AwardsTab.tsx` (`.bg-gradient-leader`), `LiveSessionCard.tsx` (`.bg-gradient-live`) | Token-driven | P2 | Low |
| Progress indicators | `UpcomingSessionCard`, `NextSessionCard`, `AttendanceSummaryCard`, `AttendanceRankingDialog` (`bg-gradient-primary`) | Blue via class remap | P1 | Low |
| Logo wordmark | `AppSidebar.tsx:38`, `MobileTopBar.tsx:14`, `auth/AuthLayout.tsx:12` (`.text-gradient-primary`) | → `--brand-text` | P1 | Low |
| Brand icons/dots | `NotificationItem.tsx:57`, `ResetLinkSent.tsx:16`, `NotificationUnsubscribePage.tsx:68-70`, `FinanceCreateForm.tsx:29` | `text-brand` should become `text-brand-text` (explicit rename recommended) | P1 | Low — `#0799D5` text on Off-White is 3.00:1 |

### 11.2 Explicit edits

| Component | Files | Change | Priority | Risk |
|---|---|---|---|---|
| Tokens & base | `src/index.css` | Primitive layer, semantic re-point, new tokens, `color-scheme`, reduced-motion block, remap/rename brand classes, retint landing classes | P0 | High — every screen; mitigated by contrast test + manual matrix |
| Tailwind config | `tailwind.config.js` | `fontFamily` (sans, display), `fontSize` scale, new colors (`brand.{DEFAULT,foreground,hover,text,muted}`, `highlight.*`, `chart.3`, `overlay`) | P0 | Medium — `brand` changes from string to object (keep `DEFAULT`) |
| Document shell | `index.html`, `src/main.tsx` | `lang="pt-BR"`, inline theme script, font preload, `theme-color`; remove class logic from `main.tsx`; themed Toaster | P0 | Medium — FOUC regressions; test hard reload in both themes |
| Button | `ui/button-variants.ts` | `brand` variant (`bg-brand text-brand-foreground hover:bg-brand-hover`), keep `gradient` alias; optional `highlight` variant | P0 | Medium — 33 consumers; alias avoids a big-bang diff |
| Checkbox | `ui/checkbox.tsx` + `.checkbox-gradient` | Brand border/fill, Midnight check icon | P1 | Low |
| Calendar selected day | `ui/calendar.tsx:46` | `bg-brand text-brand-foreground` | P1 | Low |
| Overlays | `ui/dialog, alert-dialog, sheet, drawer, sidebar` (`bg-black/60–80`) | Optional `bg-overlay` (Midnight tint) | P3 | Low — owned shadcn code, keep backward compatible |
| Auth login button | `pages/AuthPage.tsx:171` | `variant="brand"` instead of class override | P1 | Low |
| Chat own bubble | `pelada/ChatPanel.tsx:101` | `bg-brand text-brand-foreground` | P1 | Low |
| Next-session date block | `pelada/NextSessionCard.tsx:50-53` | `bg-brand text-brand-foreground`, inner labels `text-brand-foreground/80` (validate ≥4.5) and Barlow for the day number | P1 | Low |
| Home banner date pill | `home/HomeBanner.tsx:26` | `bg-brand-muted`… note it sits on a photo overlay: validate against the overlay, may need a solid Midnight pill | P2 | Medium |
| Edit profile header | `profile/EditProfileModal.tsx:93` | `bg-brand` (decorative) | P3 | Low |
| Compare players | `pelada/ComparePlayersDialog.tsx`, `pelada/ComparablePlayerStatsCard.tsx` | Replace `zinc-*`/`white` with tokens; works in light | P1 | Medium — 55 classes, visual review |
| Profile KPIs | `pages/ProfilePage.tsx:107-115`, `profile/KpiCard.tsx` | Icon colors → `brand-text`/`highlight-text`/`success`/`muted-foreground`; value in `text-score font-display` | P2 | Low |
| Profile charts | `profile/StatsOverTimeChart.tsx`, `profile/ProfilePieChart.tsx` | Hex → `hsl(var(--chart-1/2/3))` through `ChartConfig` | P1 | Low — closes antipattern #27 |
| Match history highlight | `profile/MatchHistoryTable.tsx:46-48` | `bg-success-muted text-success` / `bg-gold-muted text-gold` | P2 | Low |
| Create pelada focus rings | `CreatePeladaModal.tsx` (12) | Remove `ring-green-*` overrides (default ring is correct), checkbox override → default | P2 | Low |
| Pelada/avatar gradients | `lib/utils.ts` `getPeladaGradient`, `getAvatarColor` | Map to `bg-avatar-1…6` tokens (gradients optional via token pairs); re-check white initials ≥4.5 | P2 | Medium — visible identity of every pelada; deterministic by name/id stays |
| Typography roles | `ScoreStepper`, `ResultsMatchCard`, `SavedMatchesList`, `LiveLeagueTable`, `RankingTable`, `ChampionHero`, `DailyAwardsGrid`, `HomeBanner`, `PeladaBanner`, `PeladaPageHeader`, `DailyHeader`, `NextSessionCard`, `UpcomingSessionCard`, `KpiCard` | Apply `font-display` + scale tokens to scores/positions/titles | P1 | Medium — condensed glyph widths change truncation; check 375 px |
| Arbitrary sizes elsewhere | ~120 occurrences (`text-[13px]` etc.) | Replace with scale tokens opportunistically (same px) | P3 | Low (pixel-identical) |
| Highlight usage | `ChampionHero`, `DailyAwardsGrid`, `AwardsTab`, `RankingTable` medals?, `LiveSessionCard`? | Introduce Cantaloupe where the product decides (Q5) | P2 | Medium — semantic overlap with gold/live |
| Landing | `pages/LandingPage.tsx` (51), `.landing-*` in `index.css` | Re-palette, Barlow hero, reduced motion | P3 | Medium — largest single file; marketing look |
| Logo / favicon | `public/gerrard.png` | Out of scope unless Q6 | — | — |

---

## 12. Accessibility & Responsiveness

Target: **WCAG 2.2 AA**.

| Criterion | Requirement for this change | How it is verified |
|---|---|---|
| 1.4.3 Contrast (text) | Every semantic text/background pair ≥ 4.5:1 (≥ 3:1 for ≥ 24 px or ≥ 18.66 px bold) | automated contrast test (§15) over the token pairs in §7.2 |
| 1.4.11 Non-text contrast | `--input` vs surfaces ≥ 3:1; `--ring` vs background ≥ 3:1; checkbox/switch borders ≥ 3:1; icons that carry meaning ≥ 3:1 | same test |
| 1.4.1 Use of color | Status never by color only (badges keep text; medals keep position number; unread dot keeps `aria-label`) | review |
| 2.4.7 / 2.4.11 / 2.4.13 Focus | Visible `focus-visible` ring, 2 px, offset, ≥3:1; not hidden by sticky `MemberSelectionBar`, chat FAB or `MobileTopBar` (`scroll-margin` where needed) | keyboard pass |
| 2.1.1 Keyboard | No change to Radix behavior; new variants keep focusability | keyboard pass |
| 2.5.8 Target size (min 24 px) | Existing 32–40 px controls unchanged; check chip `h-8` and 26 px "+" in sidebar | review |
| 2.2.2 / 2.3.3 Motion | Global `@media (prefers-reduced-motion: reduce)` disabling infinite landing animations, `scale` hovers and `animate-in` durations (≈0.01 ms) | OS setting toggle |
| 3.1.1 Language | `<html lang="pt-BR">` | DOM check |
| 4.1.3 Status messages | Toasts keep Sonner's live region; themed | review |
| Typography | Body ≥ 13 px; Barlow ≥ 16 px; line heights per §9.2; text zoom 200 % without loss | browser zoom |
| Responsive | Every touched screen at 375 px, 768 px, 1280 px in both themes; condensed font must not introduce horizontal scroll | manual matrix |

---

## 13. Proposed Implementation Plan (Phase 2 — not executed)

Each step is a separate PR, shippable on its own, with `bun run lint && bun run build && bun run test` green and FRONTEND.md updated in the same PR.

### Step 1 — Foundations: document shell & theme bootstrap
- **Scope:** `lang="pt-BR"`, inline theme script, `color-scheme`, `theme-color`, guarded storage, themed Toaster, reduced-motion base rule.
- **Files:** `index.html`, `src/main.tsx`, `src/index.css` (only `color-scheme` + reduced-motion), maybe `src/App.tsx`.
- **Dependencies:** none.
- **Risks:** FOUC/regression in theme persistence.
- **Validation:** hard reload with `theme=dark`, `theme=light`, none (OS light/dark), storage blocked; Lighthouse a11y "lang".
- **Acceptance:** no white frame on reload in dark (DevTools performance filmstrip); toasts dark in dark mode; existing toggle unchanged.

### Step 2 — Tokens: primitives + semantic re-point (color only)
- **Scope:** primitive layer, new values for existing tokens, new tokens (`brand-*`, `highlight-*`, `chart-3`, `overlay`), Tailwind color mappings, contrast test.
- **Files:** `src/index.css`, `tailwind.config.js`, new `src/lib/contrast.ts` + `contrast.test.ts` (or under `src/utils/`).
- **Dependencies:** Q1–Q4 decided.
- **Risks:** app-wide visual shift; `brand` mapping change in Tailwind breaking `bg-brand`.
- **Validation:** contrast test; screenshot pass of Home, Pelada overview/members/ranking, Daily (scheduled, live, finished), Profile, Settings, Finance, Auth in both themes at 375/1280.
- **Acceptance:** all pairs in §7.2 pass in the test; no raw value outside the primitive layer in `:root`/`.dark`; build green.

### Step 3 — Brand consumers & foregrounds
- **Scope:** `brand` Button variant (+ `gradient` alias), checkbox, calendar, AuthPage, ChatPanel, NextSessionCard, HomeBanner pill, EditProfileModal, `text-brand` → `text-brand-text`, rename/remap `.bg-gradient-*`/`.text-gradient-primary`/`.checkbox-gradient`/`.bg-brand-translucent`/`.text-on-brand-muted`.
- **Files:** §11.2 rows marked "brand".
- **Dependencies:** Step 2.
- **Risks:** missed `text-white` on brand.
- **Validation:** `grep -rn "text-white" src` reviewed line by line — only photo/avatar contexts remain; contrast test covers `brand/brand-foreground`.
- **Acceptance:** no `text-white` on a brand fill; no green value left in `index.css` except success/live tokens.

### Step 4 — Typography
- **Scope:** font packages (after approval), `@font-face`/imports, preload, fallback metrics, `fontFamily`/`fontSize` tokens, apply `font-display` to the components in §11.2 "Typography roles".
- **Files:** `package.json`/`bun.lock` (approved deps), `src/main.tsx` or `index.css` imports, `index.html`, `tailwind.config.js`, the domain components listed.
- **Dependencies:** Step 2 (independent of 3); Q3.
- **Risks:** CLS, truncation with condensed widths, bundle size, `tnum` support.
- **Validation:** Lighthouse CLS < 0.1 on `/home`; network panel shows only self-hosted woff2 ≤ 150 KB; 375 px pass on score/ranking screens.
- **Acceptance:** Inter is the computed font of `body`; Barlow only where §9.1 allows (spot-check via DevTools "Rendered fonts").

### Step 5 — Hardcoded islands
- **Scope:** ComparePlayersDialog + ComparablePlayerStatsCard, ProfilePage KPIs, profile charts, MatchHistoryTable, CreatePeladaModal, `lib/utils.ts` gradients/avatars.
- **Dependencies:** Step 2.
- **Risks:** pelada avatar identity change noticed by users.
- **Validation:** grep for raw palette classes (only Landing, photo overlays, Google logo, team colors allowed); both themes.
- **Acceptance:** FRONTEND.md antipattern #3 and #27 "Found in" entries removed for those files.

### Step 6 — Highlight (Cantaloupe) rollout
- **Scope:** apply `highlight-*` to the gamification spots chosen in Q5.
- **Dependencies:** Steps 2, 4; Q5.
- **Risks:** confusion with gold/destructive.
- **Acceptance:** highlight used only on the approved list; never on destructive or error UI.

### Step 7 — Landing & cleanup
- **Scope:** Landing re-palette and typography, remove deprecated aliases (`gradient` variant after codemod of 33 files, old class names), optional `bg-overlay` in shadcn overlays, scale tokens replacing remaining arbitrary sizes, docs.
- **Dependencies:** all previous.
- **Acceptance:** `grep` for `gradient` variant/classes returns nothing; FRONTEND.md "Styling & design system" fully describes the new system.

Dependency graph: `1 → 2 → {3, 4, 5} → 6 → 7` (1 is independent but unblocks clean visual checks).

---

## 14. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| White text left on blue/orange fills (3.22 / 2.68) | High | A11y failure | `brand-foreground`/`highlight-foreground` tokens; Step 3 grep audit; contrast test |
| `info` and brand indistinguishable (Mensalista badge vs CTA) | High | Meaning loss | Q4: re-hue `info` |
| Theme flash after moving bootstrap | Medium | Visual glitch | Inline head script; filmstrip check |
| Condensed font breaks truncation/alignment at 375 px | Medium | Layout bugs | Limit Barlow to listed roles; 375 px matrix |
| Font CLS / bundle growth | Medium | Perf | Latin subset, 3 Barlow weights, variable Inter, preload one file, fallback metrics |
| Barlow tabular figures absent | Unknown | Misaligned scores | Verify in Step 4; fallback to Inter for aligned columns |
| Big-bang diff on 33 `variant="gradient"` files | Medium | Review burden | Alias first, codemod last (Step 7) |
| Heavier input borders change look | Medium | Perceived noise | Required by 1.4.11; restricted to `--input` (dividers stay light) |
| Uncommitted local work on finance/MemberCard/tabs-variants collides with Phase 2 | Medium | Merge conflicts | Land or stash the current working-tree changes before Step 2 |
| shadcn CLI updates overwrite customized `ui/*` | Low | Regression | Existing rule: `--dry-run`, answer "no" |
| Dark-only shadows look heavy in light | Low | Polish | Optional `--shadow-color` per theme |

---

## 15. Testing & Validation Strategy

1. **Automated contrast test (Vitest, pure logic — matches the project's testing convention):** a small `contrast(fg, bg)` helper and a test that parses `src/index.css` (`:root`, `.dark`), resolves `var(--p-*)` references, and asserts the pair list of §7.2 (`foreground/background`, `card-foreground/card`, `muted-foreground/{card,card-elevated,background}`, `faint-foreground/*`, `brand-foreground/brand`, `brand-text/{background,card,card-elevated}`, `highlight-*`, `input/{card,background}` ≥ 3, `ring/background` ≥ 3, status/badge pairs, `primary-foreground/primary`). Fails the build on regression.
2. **Existing gates:** `bun run lint && bun run build && bun run test` per PR (CI already enforces).
3. **Grep gates per step:** raw palette classes, `text-white`, hex in `src/components`, `gradient` names (expected allow-list documented in the PR).
4. **Manual visual matrix:** routes × {light, dark} × {375, 768, 1280}: `/`, `/auth`, `/home`, `/pelada/:id` (overview, membros, sessoes, ranking, premios, financeiro, convites, settings), `/daily/:id` (scheduled, live, finished), `/profile/:id`, `/settings/notifications`, dialogs (results, compare players, player history, attendance ranking), toasts.
5. **Keyboard & reduced motion pass** on the same routes (Tab order, visible ring, Esc, OS reduce-motion).
6. **Performance:** Lighthouse (mobile) on `/home` and `/daily/:id`: CLS < 0.1, no regression > 10 % in LCP; font bytes ≤ 150 KB.
7. **Optional:** Playwright screenshot suite — would add a dependency; propose only if the manual matrix proves too costly (Q8).

---

## 16. Acceptance Criteria

Phase 2 is complete when all of the following are verifiable:

1. `src/index.css` defines the primitive layer and every semantic token in §8.4 for `:root` and `.dark`; no hex/rgb literals remain in `index.css` outside primitives and photo overlays.
2. The contrast test passes for every pair listed in §15.1.
3. `grep` finds no raw Tailwind palette classes in `src/` except: photo overlays/white-on-photo (`PeladaBanner`, `ChampionHero`, `HomeBanner`, `MyPeladaCard`), colored avatars' initials, Google logo, team colors, and Landing if Step 7 is deferred.
4. No `text-white` on a brand or highlight fill.
5. `body` computed font is Inter; `font-display` is applied only to the roles of §9.1; fonts are served from the app origin.
6. Reload in dark mode shows no light frame; with no saved preference the behavior matches the Q1 decision; Toaster follows the theme; `<html lang="pt-BR">`.
7. With `prefers-reduced-motion: reduce`, no infinite animation runs and no hover scale occurs.
8. Every route of §15.4 works at 375 px without horizontal scroll in both themes.
9. Lint, build and tests are green; CLS < 0.1 on `/home`.
10. FRONTEND.md "Styling & design system", token table, Antipatterns "Found in" entries and the folder description are updated; the redesign handoff note about the system font stack is marked superseded.

---

## 17. Open Questions & Pending Decisions

| # | Question | Options | Recommendation |
|---|---|---|---|
| Q1 | Default theme when the user never chose one (today: light) | a) follow OS · b) dark (reference) · c) keep light | **a** — respects the user, still honours explicit choice |
| Q2 | Should `--primary` (default Button, active chips) become Electric Blue? | a) keep neutral, brand separate · b) primary = brand | **a** — avoids blue everywhere and keeps CTA hierarchy (`brand` variant = main action) |
| Q3 | Font delivery | a) `@fontsource` packages (2 deps) · b) woff2 in `public/` · c) Google Fonts | **a** |
| Q4 | `--info` (Mensalista badge, info alerts) is the same hue as the new brand | a) re-hue info to indigo/violet · b) make Mensalista badge neutral + icon | **a** |
| Q5 | Where Cantaloupe appears | candidate list: champion hero accent, award leader row, MVP/top-scorer badges, ranking #1 marker, live "ao vivo" dot | Award/champion/ranking leader only; keep gold for medals and green for live |
| Q6 | Logo/favicon (`gerrard.png`, green wordmark) | a) recolor wordmark only (token) · b) new logo asset | **a** in this project; asset change separately |
| Q7 | Radius | keep 8/14/18 + pill buttons · sharpen to 6 px | keep |
| Q8 | Add Playwright visual regression | yes (new dep) · no (manual matrix) | no for now |
| Q9 | Text on brand fill: Midnight (6.24:1, both themes) vs white on a darker blue `#0A6E99` (5.67:1) in light only | Midnight everywhere · split per theme | **Midnight everywhere** — one brand look, passes AA in both themes |
| Q10 | Pelada/avatar gradients (`lib/utils.ts`) | map to 6 avatar tokens · keep 8 raw gradients | map to tokens |
| Q11 | Scope of Landing in this effort | Step 7 · separate project | Step 7, lowest priority |
| Q12 | SPEC language | English (CLAUDE.md docs rule) · Portuguese | English, as written |

---

### Appendix A — Files inspected

`client/package.json`, `tailwind.config.js`, `components.json`, `index.html`, `src/index.css`, `src/main.tsx`, `src/hooks/useTheme.ts`, `src/components/ui/{button,badge,tabs,toggle}-variants.ts`, `ui/{card,checkbox,calendar,dialog,alert-dialog,sheet,drawer,sidebar,chart}.tsx`, `src/lib/utils.ts`, and the components cited in §2.6, §2.7, §2.9 (grep-level inspection), `docs/design/design_handoff_futspring_redesign/README.md`, `client/docs/FRONTEND.md`.

### Appendix B — Working tree at the time of discovery

Uncommitted changes existed (not made by this task) in `client/docs/FRONTEND.md`, `components/finance/*`, `components/pelada/MemberCard.tsx`, `components/ui/tabs-variants.ts`. Phase 2 should start after they are committed.
