# Current user-requested adjustments

The original Home photo banner and Nova pelada action, pelada-overview area shortcuts and future member-activity (Em breve) panel have been restored. The Home pelada cards again use their original horizontal swipe/snap behavior on mobile, while retaining the compact section creation button. Attendance ordering, grouped mobile navigation and the simplified creation form remain. The earlier review below documents the first iteration; current rollback validation is recorded under output/playwright/rollback/.

# Hick-law refinements — validation

The app remains in its existing Futspring visual system: shadcn/Radix components, semantic tokens, Inter and Barlow Condensed. These are Operate surfaces. Cal.com's Awesome DESIGN.md reference informed restrained hierarchy; NN/g progressive-disclosure guidance informed the short default creation form. No new fonts, imagery, dependencies, API contracts or permissions were introduced.

## Outcomes

- Home answers “Which session needs my response?” before showing invitations, statistics, groups and device prompts. Unanswered sessions lead; attendance still uses the existing availability, priority and subscription rules. Invitations sit beside sessions on desktop. Pelada cards stack within the phone's margins; Nova pelada is a compact section action rather than an empty card.
- Mobile workspace navigation has four direct links plus Mais, grouped by Pelada/Gestão. Members see Prêmios/Financeiro, admins also see Convites/Configurações, and creators also see Assinatura. The duplicate overview shortcuts and unavailable member-activity panel were removed.
- Creation answers “What is the pelada, and when do we play?” with three initial controls. Existing 2-team, 5-player, 1-hour defaults stay visible as a summary. Details remain editable, survive collapsing, and reopen on client/server errors. Invalid numbers never render NaN; duration uses pt-BR formatting. Photo failure still completes creation and reports the warning.

## Proof

- Frontend lint and production build passed. Lint retains the documented, pre-existing TanStack Table compiler warning.
- 609 unit tests passed; regressions cover unanswered-first order, source-array preservation and access-safe primary navigation.
- Official Docker Playwright image: 50 affected checks passed, with 2 expected desktop exclusions. Home/create were refreshed and rerun after the final Home refinement. Screenshot refreshes used a temporary copy with the original sidebar, to avoid incorporating unrelated pre-existing working-tree edits into baselines.
- Playwright CLI used isolated Brave profiles with repository fake fixtures, pt-BR locale and America/Sao_Paulo timezone. Final contexts blocked service workers to avoid stale production PWA caches. No production accounts or data were used.
- Browser flows passed: attendance confirmation; empty form errors; hidden numeric error reopen/focus; NaN fallback; collapsed-value retention; server field error reopen/focus; successful payload with duration, team defaults, location, automation and timezone; all three navigation roles; ArrowDown/Escape focus handling; secondary route indicator and browser back.
- Independent Impeccable assessments: A scored 32/40, with no blocking issue; its numeric-summary finding was fixed and browser-verified. B source detector found zero findings across seven files. The later Home/card refinement was scanned separately with zero findings.
- Design-taste source lint: zero failures/warnings. Its rendered probe, layout and app measurements were run through authenticated Playwright on the production build at 375/768/1440 in both themes because the standalone fresh-profile inspector cannot inherit the app's mocked authenticated session. Final combined findings are in taste-report.json: zero failures. The remaining warnings are recorded below.
- New creation buttons have 44px targets; navigation/menu items are at least 44px high. Browser matrices show no document horizontal overflow in either theme.

## Visual critique and remaining warnings

Task hierarchy, navigation and progressive disclosure pass the app-screen critique. Futspring's soccer vocabulary, actual session date/capacity and pelada identities provide the product character. No new decorative system or direction draw was needed for this scoped refinement.

- `ragged-row` on desktop Home: attendance and invitation queues are independent tasks with different amounts of content. Their top edges align; their cards retain natural heights to avoid padded empty bands.
- `micro-label` on the overview: the existing calendar tile's short weekday (QUI) is meaningful date notation.
- `size-count` on wider overviews: existing shared date/status/sidebar typography remains intact; there are no new typography tokens.

The optional Impeccable live overlay was blocked by production CSP. Source detection, rendered measurements, screenshots and accessibility/keyboard inspection supply the evidence; no CSP change was made. Assessment B's complete run notes record the cleanup approval retry; its browser and temporary server were closed successfully.

The user's existing AppSidebar.tsx and .gitignore edits were preserved. The sidebar's pre-existing removal of Nova pelada and stray literal 2 remain outside this change.
