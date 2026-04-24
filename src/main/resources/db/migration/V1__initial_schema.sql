-- PayFlow initial schema.

CREATE TABLE merchants (
    id             UUID PRIMARY KEY,
    name           TEXT NOT NULL,
    api_key_hash   TEXT NOT NULL UNIQUE,
    webhook_secret TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    email       TEXT,
    name        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_customers_merchant ON customers(merchant_id);

CREATE TABLE payment_intents (
    id             UUID PRIMARY KEY,
    merchant_id    UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    customer_id    UUID REFERENCES customers(id),
    amount_cents   BIGINT NOT NULL CHECK (amount_cents > 0),
    currency       CHAR(3) NOT NULL,
    status         TEXT NOT NULL CHECK (status IN (
                     'requires_confirmation','processing','succeeded','failed','canceled')),
    provider       TEXT NOT NULL DEFAULT 'stripe',
    provider_id    TEXT UNIQUE,
    description    TEXT,
    metadata_json  JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_intents_merchant_created ON payment_intents(merchant_id, created_at DESC);
CREATE INDEX idx_intents_status ON payment_intents(status);

CREATE TABLE charges (
    id                  UUID PRIMARY KEY,
    intent_id           UUID NOT NULL REFERENCES payment_intents(id) ON DELETE CASCADE,
    attempt_no          INT  NOT NULL,
    status              TEXT NOT NULL CHECK (status IN ('pending','succeeded','failed')),
    provider_charge_id  TEXT UNIQUE,
    failure_code        TEXT,
    failure_message     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (intent_id, attempt_no)
);
CREATE INDEX idx_charges_intent ON charges(intent_id);

CREATE TABLE refunds (
    id                  UUID PRIMARY KEY,
    intent_id           UUID NOT NULL REFERENCES payment_intents(id) ON DELETE CASCADE,
    amount_cents        BIGINT NOT NULL CHECK (amount_cents > 0),
    status              TEXT NOT NULL CHECK (status IN ('pending','succeeded','failed')),
    reason              TEXT,
    provider_refund_id  TEXT UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_intent ON refunds(intent_id);

CREATE TABLE idempotency_keys (
    merchant_id     UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    key             TEXT NOT NULL,
    request_hash    TEXT NOT NULL,
    response_status INT,
    response_body   JSONB,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (merchant_id, key)
);

CREATE TABLE webhook_events (
    id                  UUID PRIMARY KEY,
    provider            TEXT NOT NULL,
    provider_event_id   TEXT NOT NULL,
    event_type          TEXT NOT NULL,
    payload             JSONB NOT NULL,
    status              TEXT NOT NULL CHECK (status IN ('received','processed','error','duplicate')),
    error               TEXT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_event_id)
);
CREATE INDEX idx_webhook_events_type ON webhook_events(event_type);

CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_type     TEXT NOT NULL,
    actor_id       TEXT,
    action         TEXT NOT NULL,
    resource_type  TEXT NOT NULL,
    resource_id    TEXT,
    request_hash   TEXT,
    result         TEXT NOT NULL CHECK (result IN ('ok','denied','error')),
    detail         JSONB,
    at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_actor ON audit_log(actor_type, actor_id);
