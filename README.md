# PayFlow

[![ci](https://github.com/SAY-5/payflow/actions/workflows/ci.yml/badge.svg)](https://github.com/SAY-5/payflow/actions/workflows/ci.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)
[![java](https://img.shields.io/badge/java-21-007396)](#)
[![spring boot](https://img.shields.io/badge/spring--boot-3.3-6DB33F)](#)

A payment-processing API platform with **idempotent transactions**, **Stripe
webhook ingestion**, and a **full audit trail** — backed by PostgreSQL, JPA,
Spring Boot, and a React/TypeScript operator console.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the design writeup (domain
model, idempotency guarantees, webhook ingest flow, security posture).

## Quick start

Requires JDK 21 + Maven 3.9+ + Node 22+ + Docker.

```bash
# 1. Start Postgres + the API via compose
docker compose up -d db
PAYFLOW_JWT_SECRET="change-me-in-prod-at-least-sixteen-chars" \
  mvn spring-boot:run

# 2. Start the operator console
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

A pre-seeded demo merchant lets you log in with API key `demo-api-key`.
The demo's in-memory gateway fails charges over $10,000 (maps to
`amount_too_large`) and any charge to an email containing `@fail.` — so
you can exercise the success AND failure paths without a real Stripe
account.

## What's in the box

| Piece                    | Behaviour                                                   |
|--------------------------|-------------------------------------------------------------|
| `/v1/payment-intents`    | Create + confirm + list + cancel, all idempotent            |
| `/v1/refunds`            | Partial + full refunds with the same idempotency flow       |
| `/webhooks/stripe`       | HMAC-SHA256 verified, 5-min replay window, at-least-once    |
| Idempotency keys         | Per-(merchant, key), request-hash matched, 7-day retention  |
| Audit log                | Append-only, `REQUIRES_NEW` tx so it survives rollbacks     |
| JPA domain               | Merchants, Customers, PaymentIntents, Charges, Refunds      |
| Flyway migrations        | V1 schema + V2 demo seed                                    |
| Auth                     | API key → short-lived JWT (HS256)                           |
| Rate limiting            | Token-bucket per merchant (not shipped by default; easy hook) |

## Idempotency semantics

Every mutating POST requires an `Idempotency-Key: <opaque>` header.

- Same key + same body → replay the original 201/200/4xx response.
- Same key + **different** body → `422 Unprocessable Entity`.
- Same key while a first call is still in-flight → `409 Conflict` with
  `Retry-After: 5`.

The key is scoped per-merchant so two tenants can't collide.

## Webhook verification

`POST /webhooks/stripe` reads the raw bytes (not the parsed body!) and
verifies `Stripe-Signature` via HMAC-SHA256 against each merchant's
webhook secret. The `t=…` timestamp in the header must be within
`payflow.webhook.max-age-seconds` (default 300) to block replay.

An event with a previously-seen `provider_event_id` returns 200 with
`duplicate: true` — so provider retries never double-apply a side
effect on our side.

## Tests

```bash
mvn test   # 20+ JUnit 5 tests
```

Coverage:

- **Hashing / signature** — 9 tests covering known SHA-256 + HMAC-SHA256
  vectors and constant-time compare.
- **Stripe signature** — sign→verify round trip, tamper rejection,
  replay-window enforcement, bad-key rejection, malformed-header cases.
- **Payment flow** — create, retrieve, list, cancel, failure path;
  idempotency replay (same body), rejection (different body), missing
  key, missing auth.
- **Webhook ingest** — invalid signature is rejected; valid signature
  ingests and returns 200; same event id twice reports `duplicate`.

## Configuration

Environment variables (all optional; defaults in `application.yml`):

| Var                       | Default                         | Notes                      |
|---------------------------|---------------------------------|----------------------------|
| `PAYFLOW_DB_URL`          | `jdbc:postgresql://…/payflow`   |                            |
| `PAYFLOW_DB_USER/PASSWORD`| `payflow`                       |                            |
| `PAYFLOW_JWT_SECRET`      | — (required ≥16 chars in prod)  | HS256 HMAC secret          |
| `PAYFLOW_JWT_TTL`         | `60`                            | minutes                    |
| `PAYFLOW_PORT`            | `8080`                          |                            |
| `PAYFLOW_CORS_ORIGINS`    | `http://localhost:5173`         | comma-separated            |

## Docker

```bash
docker compose up -d
# API on :8080, Postgres on :5432
```

The image runs as a non-root user and exposes a `/healthz` HTTP
healthcheck. See [docs/DEPLOY.md](./docs/DEPLOY.md) for the full
deploy guidance.

## Companion projects

Part of a five-repo set:

- **[canvaslive](https://github.com/SAY-5/canvaslive)** — real-time multiplayer whiteboard (OT, WebSocket, React)
- **[pluginforge](https://github.com/SAY-5/pluginforge)** — Web Worker plugin sandbox with capability-based permissions
- **[agentlab](https://github.com/SAY-5/agentlab)** — multi-model AI coding agent evaluation harness
- **[payflow](https://github.com/SAY-5/payflow)** — you're here. Payments API.
- **[queryflow](https://github.com/SAY-5/queryflow)** — natural-language SQL engine with pgvector RAG

## License

MIT — see [LICENSE](./LICENSE).
