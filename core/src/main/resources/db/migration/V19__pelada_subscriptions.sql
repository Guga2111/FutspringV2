-- Subscription per pelada (spec docs/plans/2026-11-07-subscriptions-platform-admin-spec.md). Additive: the previous
-- binary reads none of it. The effective state (trial, active, past due, read-only…) is computed by
-- SubscriptionState from kind + valid_until; no job changes these rows.

-- Operator of the platform; turned on by SQL only, no route writes it
ALTER TABLE users ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT false;
-- One trial per creating account; transferring a pelada doesn't give or reset it
ALTER TABLE users ADD COLUMN trial_used_at TIMESTAMPTZ NULL;

CREATE TABLE pelada_subscriptions (
    id             BIGSERIAL PRIMARY KEY,
    pelada_id      BIGINT       NOT NULL REFERENCES peladas (id) ON DELETE CASCADE,
    kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('TRIAL', 'PENDING', 'PAID', 'EXEMPT', 'CANCELED')),
    valid_until    TIMESTAMPTZ  NULL,
    plan           VARCHAR(20)  NULL CHECK (plan IN ('MONTHLY', 'ANNUAL')),
    founder        BOOLEAN      NOT NULL DEFAULT false,
    pix_claimed_at TIMESTAMPTZ  NULL,
    claimed_plan   VARCHAR(20)  NULL CHECK (claimed_plan IN ('MONTHLY', 'ANNUAL')),
    note           VARCHAR(500) NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_pelada_subscriptions_pelada UNIQUE (pelada_id),
    -- a trial or a paid subscription always ends somewhere
    CONSTRAINT ck_pelada_subscriptions_valid_until CHECK (kind NOT IN ('TRIAL', 'PAID') OR valid_until IS NOT NULL)
);
CREATE INDEX idx_pelada_subscriptions_kind_valid_until ON pelada_subscriptions (kind, valid_until);

-- Payments recorded by the platform admin (the carnê-leão total). Kept when the pelada is deleted: the pelada and
-- payer names are snapshots
CREATE TABLE subscription_payments (
    id             BIGSERIAL PRIMARY KEY,
    pelada_id      BIGINT        NULL REFERENCES peladas (id) ON DELETE SET NULL,
    pelada_name    VARCHAR(255)  NOT NULL,
    payer_id       BIGINT        NULL REFERENCES users (id),
    payer_name     VARCHAR(255)  NOT NULL,
    amount         NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    paid_on        DATE          NOT NULL,
    plan           VARCHAR(20)   NOT NULL CHECK (plan IN ('MONTHLY', 'ANNUAL')),
    period_start   TIMESTAMPTZ   NOT NULL,
    period_end     TIMESTAMPTZ   NOT NULL,
    recorded_by_id BIGINT        NOT NULL REFERENCES users (id),
    note           VARCHAR(500)  NULL,
    voided_at      TIMESTAMPTZ   NULL,
    void_reason    VARCHAR(500)  NULL,
    created_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_subscription_payments_pelada_id ON subscription_payments (pelada_id);
CREATE INDEX idx_subscription_payments_paid_on ON subscription_payments (paid_on);
CREATE INDEX idx_subscription_payments_payer_id ON subscription_payments (payer_id);
CREATE INDEX idx_subscription_payments_recorded_by_id ON subscription_payments (recorded_by_id);

-- History of every change to a pelada's subscription; actor null = the system (migration, scheduler)
CREATE TABLE subscription_events (
    id                 BIGSERIAL PRIMARY KEY,
    pelada_id          BIGINT       NOT NULL REFERENCES peladas (id) ON DELETE CASCADE,
    actor_id           BIGINT       NULL REFERENCES users (id),
    type               VARCHAR(30)  NOT NULL CHECK (type IN ('CREATED', 'PAYMENT_RECORDED', 'PAYMENT_VOIDED',
                           'VALID_UNTIL_SET', 'DAYS_EXTENDED', 'EXEMPTED', 'CANCELED', 'FOUNDER_CHANGED', 'PIX_CLAIMED')),
    kind_before        VARCHAR(20)  NULL CHECK (kind_before IN ('TRIAL', 'PENDING', 'PAID', 'EXEMPT', 'CANCELED')),
    kind_after         VARCHAR(20)  NULL CHECK (kind_after IN ('TRIAL', 'PENDING', 'PAID', 'EXEMPT', 'CANCELED')),
    valid_until_before TIMESTAMPTZ  NULL,
    valid_until_after  TIMESTAMPTZ  NULL,
    note               VARCHAR(500) NULL,
    created_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_subscription_events_pelada_created ON subscription_events (pelada_id, created_at DESC);
CREATE INDEX idx_subscription_events_actor_id ON subscription_events (actor_id);

-- Backfill: every existing pelada gets a 30-day trial from now, and its creator has used theirs
INSERT INTO pelada_subscriptions (pelada_id, kind, valid_until, created_at, updated_at)
SELECT id, 'TRIAL', now() + INTERVAL '30 days', now(), now() FROM peladas;

INSERT INTO subscription_events (pelada_id, type, kind_after, valid_until_after, created_at)
SELECT pelada_id, 'CREATED', kind, valid_until, now() FROM pelada_subscriptions;

UPDATE users SET trial_used_at = now() WHERE id IN (SELECT creator_id FROM peladas);
