# Product

<!-- impeccable:product-schema 1 -->

Product record read by design skills (impeccable, design-taste-frontend). It holds product truth only: the visual system lives in [client/docs/design-system.md](client/docs/design-system.md) and `client/src/index.css`, which act as this project's `DESIGN.md`. Written from the repository (landing copy, docs, [market analysis](docs/analise-de-mercado.md)); facts marked *(inferred)* were not confirmed by the owner.

## Platform

web

Installable PWA, used mostly on phones (375 px is the floor), also on desktop. See [client/docs/pwa.md](client/docs/pwa.md).

## Users

- **Organizer / admin** of a recurring amateur soccer group ("pelada"): runs the weekly session, the list, the team draw, the results and, often, the money. Today they do it with a WhatsApp group, a copied list, Pix to their own account and a spreadsheet.
- **Player** (member): confirms attendance, checks the teams, follows the ranking, their stats and the awards. Mensalistas (monthly) and diaristas (per game) have different confirmation rights.
- **Finance managers** (responsáveis): members appointed to charge, receive and keep the pelada's cash.
- **Platform admin**: the owner, managing subscriptions and payments in `/admin`.

## Product Purpose

Replace the "WhatsApp + list + Pix + spreadsheet" system of a pelada with one app: who plays today, balanced teams, live results, the table of the day, awards, a pelada-wide ranking and the pelada's money. Success is a group that runs its weekly game entirely in the app and players who open it for the fun part (ranking, awards, comparisons), not only the organizer.

## Positioning

Competitors are "pretty spreadsheets" (list + draw + finance). Futspring's edge is the sporting and social layer built on real match data: per-session table, awards (Artilheiro, Garçom, Puskás, Bola Murcha), champion photo, pelada ranking, player stats and head-to-head comparison, on top of the organizer's tools.

## Operating Context

- A pelada has a fixed day, time and place; the weekly session ("daily") is created automatically.
- Match day: confirmations during the week (diaristas from 24 h before; a mensalista confirming a full session bumps the last diarista), the draw by stars (1–5 per pelada), results with goals and assists, finalize → ranking, stats and awards update.
- Communication still happens on WhatsApp: the app produces shareable links and messages (invite link valid 7 days, finance charge message with a "Já pagou? Avise aqui" link) and in-app, e-mail and Web Push notifications.
- Money moves by Pix between people; the app records it and never moves money.

## Capabilities and Constraints

- Peladas with creator, admins and members; invitations by user or share link; chat; 2–10 teams of 2–20 players.
- Finance: collections with Pix key / QR, payment reports with receipt, review, cash statement, audit history.
- Subscription per pelada: first pelada of an account gets 30 days free with up to 30 members; then R$ 19,90 / month or R$ 199,00 / year (above 40 members R$ 29,90 / R$ 299,00), paid by Pix; an unpaid pelada becomes read-only, never deleted.
- Terminology (keep it in pt-BR in the UI): pelada, sessão, mensalista, diarista, admin, responsável financeiro, cobrança, caixa, sorteio, artilheiro, garçom.
- User-facing text is Brazilian Portuguese; times follow the pelada's time zone.

## Brand Commitments

- Name: Futspring. Voice: Brazilian pelada talk, direct and informal, playful about the competition ("Bola fora", "Bola Murcha", "resenha"), serious and exact about money and permissions *(inferred from the shipped copy)*.
- The Sport-Tech identity already exists (spec `docs/plans/2026-11-01-design-system-sport-tech-spec.md`); design work refines it unless the owner asks for a redesign.

## Evidence on Hand

- Real photos of the owner's own pelada in `client/public/landing/` (champion photos), captioned only with what they show.
- Landing fragments use sample names, dates and scores. There are **no** real user counts, testimonials, reviews, press or logos: never invent them.

## Product Principles

1. The weekly game runs without the organizer chasing people: automatic sessions, confirmation rules and notices do the work.
2. Fairness is visible: the draw, the bump rule and the money are explained by the app, not argued in the group.
3. The fun is the hook: every result feeds ranking, stats and awards that players want to check.
4. Money is recorded exactly and never moved; every financial change is traceable.
5. Phone first, works on a weak connection, in pt-BR.

## Accessibility & Inclusion

WCAG AA contrast in both themes (checked by `client/src/lib/contrast.test.ts`), keyboard reachable controls, light and dark mode, 375 px without horizontal scroll.
