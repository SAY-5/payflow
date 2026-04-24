# PayFlow Architecture

## Overview

PayFlow is a payment-processing API. It ingests charge requests, calls a
payment provider (Stripe in this reference), reconciles webhook events,
and exposes a refund workflow. Correctness-critical properties it
guarantees:

1. **Exactly-once charge** — the same client-supplied idempotency key
   always produces the same outcome, even under retry storms.
2. **Reconciled state** — every transaction row's status reflects the
   last authoritative webhook for that payment intent.
3. **Immutable audit trail** — every state-changing request is recorded
   in an append-only `audit_log` table, including the request body hash
   and the actor.

Stack:

| Layer       | Tech                                                  |
|-------------|-------------------------------------------------------|
| Service     | Java 21 · Spring Boot 3.x · Spring Web · Spring Data JPA |
| Persistence | PostgreSQL 15 · Flyway migrations                     |
| Auth        | JWT (HS256) issued by `/auth/token`                   |
| Tests       | JUnit 5 · Testcontainers (PG) · RestAssured           |
| Frontend    | React 18 · Vite · TypeScript                          |
| Ops         | Docker multi-stage · docker-compose (svc + db)        |

## Domain model

```
Merchant      ── issuer of API keys, has many PaymentIntents
Customer      ── end-user; optional PaymentMethod tokens
PaymentIntent ── lifecycle: requires_confirmation → processing → succeeded | failed | canceled
Charge        ── one row per attempt against a provider (1..N per intent)
Refund        ── lifecycle: pending → succeeded | failed
WebhookEvent  ── raw provider events, dedup'd on provider event id
IdempotencyKey── (merchant_id, key) → response_hash + http_status + body
AuditLog      ── append-only audit rows
```

### Why PaymentIntent + Charge split

A single "intent" can span multiple charge attempts (retry after a
timeout, reauthorize, 3DS challenge). We store the intent as the
business-level object clients reason about, and each concrete attempt as
a separate row — so the full history is on disk for dispute resolution.

## Idempotency

Clients supply `Idempotency-Key: <opaque>` on every POST. The first
request with a given key:

1. Opens a transaction. Inserts `(merchant_id, key)` into
   `idempotency_keys` with a placeholder. Unique constraint prevents
   concurrent duplicates.
2. Runs the request handler.
3. Updates the row with the response body, status code, and the SHA-256
   of the canonicalized request body.
4. Commits.

A second request with the same key:
- If the key exists and is complete: returns the stored response.
- If the key exists but is incomplete (in-flight): returns 409 Conflict
  with `Retry-After: 5`.
- If the key exists and the stored body hash differs from the new
  request's hash: returns 422 Unprocessable Entity (catch obvious
  programmer errors — a client re-using the same key for a different
  body).

Keys are scoped per-merchant to avoid cross-tenant collisions.
Retention: 7 days (configurable via `payflow.idempotency.ttl-days`).

## Stripe webhook ingestion

Endpoint: `POST /webhooks/stripe`.

1. Read raw body (kept as bytes for signature verification).
2. Verify `Stripe-Signature` header against the payload + shared secret
   using the HMAC-SHA256 scheme documented by Stripe. Reject 5-min-old
   signatures.
3. Parse the event. Check `webhook_events` for the `provider_event_id`.
   If present, return 200 immediately (replay).
4. Insert the event row, open a transaction, and dispatch to a handler
   by `event.type`:
   - `payment_intent.succeeded` / `.failed` / `.canceled` → update
     `payment_intents.status` + append to `charges`.
   - `charge.refunded` → update the matching `refunds` row.
   - Anything else → insert as "observed but unhandled".
5. If any step after signature verification fails, we still return 200
   (because 4xx/5xx triggers Stripe's retry with exponential backoff),
   but log the event with `status = 'error'` so a reconciliation job can
   retry manually. This preserves at-least-once delivery without
   turning into exponential pile-up on our side.

## HTTP API

```
POST /v1/payment-intents               create + confirm
GET  /v1/payment-intents/:id           fetch one
GET  /v1/payment-intents?status=&...   list
POST /v1/payment-intents/:id/cancel    cancel if not yet processing
POST /v1/refunds                       issue refund (partial or full)
GET  /v1/refunds/:id
POST /webhooks/stripe                  Stripe posts here
POST /auth/token                       exchange API key for short-lived JWT
GET  /healthz
```

All create/update endpoints require `Authorization: Bearer <JWT>` + an
`Idempotency-Key` header. Validation errors return RFC 7807 problem
JSON.

## Schema (Flyway: `V1__initial_schema.sql`)

Abbreviated:

```sql
CREATE TABLE merchants (
  id            UUID PRIMARY KEY,
  name          TEXT NOT NULL,
  api_key_hash  TEXT NOT NULL UNIQUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customers (
  id          UUID PRIMARY KEY,
  merchant_id UUID NOT NULL REFERENCES merchants(id),
  email       TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_intents (
  id               UUID PRIMARY KEY,
  merchant_id      UUID NOT NULL REFERENCES merchants(id),
  customer_id      UUID REFERENCES customers(id),
  amount_cents     BIGINT NOT NULL CHECK (amount_cents > 0),
  currency         CHAR(3) NOT NULL,
  status           TEXT NOT NULL,
  provider         TEXT NOT NULL,
  provider_id      TEXT UNIQUE,
  description      TEXT,
  metadata_json    JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE charges (
  id                UUID PRIMARY KEY,
  intent_id         UUID NOT NULL REFERENCES payment_intents(id),
  attempt_no        INT  NOT NULL,
  status            TEXT NOT NULL,
  provider_charge_id TEXT UNIQUE,
  failure_code      TEXT,
  failure_message   TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (intent_id, attempt_no)
);

CREATE TABLE refunds (
  id                UUID PRIMARY KEY,
  intent_id         UUID NOT NULL REFERENCES payment_intents(id),
  amount_cents      BIGINT NOT NULL CHECK (amount_cents > 0),
  status            TEXT NOT NULL,
  reason            TEXT,
  provider_refund_id TEXT UNIQUE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
  merchant_id     UUID NOT NULL REFERENCES merchants(id),
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
  status              TEXT NOT NULL,
  error               TEXT,
  received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_event_id)
);

CREATE TABLE audit_log (
  id             BIGSERIAL PRIMARY KEY,
  actor_type     TEXT NOT NULL,        -- 'merchant' | 'system' | 'webhook'
  actor_id       TEXT,
  action         TEXT NOT NULL,
  resource_type  TEXT NOT NULL,
  resource_id    TEXT,
  request_hash   TEXT,
  result         TEXT NOT NULL,        -- 'ok' | 'denied' | 'error'
  detail         JSONB,
  at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Indexes on `payment_intents(merchant_id, created_at DESC)`, `charges(intent_id)`,
`audit_log(resource_type, resource_id)`.

## Frontend

A small React + Vite admin console. Not the consumer-facing checkout —
that's scope creep for a reference project. The console shows:

- Transactions list (filter by status, currency, date range)
- Transaction detail (intent + charges + refunds + webhook timeline + audit)
- Issue refund form
- Usage stats panel (total succeeded, failed, refunded this month)

Design language: navy + warm bronze + editorial serif for headings.
Tabular numerics for money.

## Testing

- **Unit**: pure service tests for idempotency key manager, signature
  verifier, state machine transitions. JUnit 5.
- **Integration**: Spring Boot Test with Testcontainers PostgreSQL.
  Full HTTP stack up through the controllers, real DB, mocked Stripe
  client.
- **Property-ish**: random request body + key reuse test asserting same
  key → same response, different key → always executes.
- **Webhook replay**: send the same event twice, assert idempotent.

Target coverage: 85% on service layer, 75% overall.

## Security notes

- API keys are never stored raw — only SHA-256 hashed with a constant-time
  compare at verification.
- JWT secret ≥ 32 bytes, mandatory in prod.
- Webhook signature: HMAC-SHA256 with the shared secret, constant-time
  comparison, 5-minute replay window.
- All DB queries use parameterized JPA/JDBC — no string concatenation.
- Amounts are stored as `BIGINT cents`. Never float, never decimal
  with ambiguous precision.

## Non-goals

- Real Stripe integration in this reference — a mocked Stripe client
  with injectable fixtures. The webhook verifier is real Stripe-compatible.
- Card data handling / PCI scope — we never see raw PANs; the frontend
  uses Stripe Elements for tokenization (documented but not hooked up
  in the demo).
- Multi-currency FX.
