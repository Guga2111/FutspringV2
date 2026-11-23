# Backlog

Unscheduled ideas preserved from the handwritten notes (`docs/melhorias-escritas-a-mao.md`,
"Futspring — rumo à v1 comercial"). These are not approved implementation plans or
release requirements. Items already shipped (subscriptions, PWA + Web Push, manual
teams, account settings, terms, refresh-token sessions, client error reporting,
founder peladas) were left out.

## Product

- **Year-end recap, Spotify Wrapped style**, computed in the pelada's time zone. The
  owner's pelada (exempt, data since January) is the showcase.
- **Seasons:** split each year into a season (needs a migration), plus a mid-season
  recap (or one every 3 months) to keep players engaged.
- **Profile:** users preferred the old view, with every stat on one screen; today the
  profile is split into tabs.
- **Mobile-specific redesign:** not just responsive; follow the patterns of apps people
  already use (Jakob's law).
- **Referee payment:** session and monthly charges are covered by the
  [finance module](../core/docs/finance.md) (payments stay
  outside the platform), but the referee has no dedicated flow: today it is an "Outro"
  charge for the members plus an expense in the cash.
- **Automatic Pix confirmation** (Asaas, Efí or Mercado Pago): the system issues a Pix
  charge with an identifier, the provider calls a webhook when it is paid and the charge
  settles by itself, removing "Informar pagamento". Today finance deliberately integrates
  no bank. Each pelada would need its own provider account with credentials stored in
  the backend; a large integration (authenticated webhook + reconciliation).
- **WhatsApp group bot:** commands such as `/ranking`, `/attendance`, `/daily` (open
  list), and posting the teams and next-session details to the group whenever the
  pelada's important notifications fire. Research first: the official API historically
  has no group support, and unofficial libraries risk a ban.

## Launch and business

- Landing page: pricing section.
- Support channel for the founder peladas.
- Open a CNPJ (ME in the Simples; software doesn't fit the MEI) and integrate Asaas when
  a trigger is hit: revenue around R$ 1,000–1,500/month (~50–75 peladas), manual billing
  becoming a chore, or a customer asking for an invoice. Open it before Pix Automático is
  needed, since that requires a CNPJ at least 6 months old.
- Asaas phase: card as the default for the monthly plan, Pix for the annual one;
  idempotent webhooks (confirmed / overdue / refunded) driving the subscription state and
  a daily reconciliation job.
- One paid consultation with an accountant: invoice obligations as a self-employed
  person in the municipality and INSS as an individual contributor.

## Operations

- Scheduled database backup with a restore test (today `pg_dump` only runs on deploy).
- Move image storage to blob storage (today on the VPS disk, outside the backup).
- Product analytics.
- React Query (or similar) on the client: each hook reimplements loading, cancellation
  and "latest response wins".

## Technical debt

- **Database connections:** the noted bottleneck is Hikari's 10 connections plus the
  Supabase pooler. The load test is fine at the current scale; measure before choosing
  a change.
- **Horizontal scaling** (several API instances behind a load balancer). Today the chat
  broker, rate limits, e-mail queue and scheduled jobs break or duplicate with two
  copies of the API:
  - Rate limits → Bucket4j on Redis (Redis already runs for the sessions); today each
    instance counts on its own, so N instances allow N× the limit.
  - Scheduled jobs → ShedLock (lock table in Postgres).
  - E-mail queue → RabbitMQ: today in memory (lost on restart) and paced per instance
    (two instances exceed Resend's 2/s). Publish after commit, one consumer with shared
    pacing, ack after sending, DLQ on failure.
  - SockJS → native WebSocket: SockJS fallbacks are several HTTP requests per session
    that a load balancer spreads across instances. Client `brokerURL` instead of
    `new SockJS("/ws")`, drop `sockjs-client` and Vite's `global: 'globalThis'`; server
    without `.withSockJS()`; nginx already upgrades `/ws`.
  - (Lower priority) STOMP broker relay on RabbitMQ with `userDestinationBroadcast` /
    `userRegistryBroadcast`, so chat and `/user/queue/notifications` reach users
    connected to another instance.
