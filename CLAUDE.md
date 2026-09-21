# Fitness Class Booking System — CLAUDE.md

Backend-only REST API for a gym fitness class booking system. One Spring Boot service, one PostgreSQL database, no frontend.

## Stack

- Java 21
- Spring Boot 3.x (latest compatible with Java 21)
- Spring Data JPA (Hibernate)
- PostgreSQL 16
- Flyway (migrations)
- Jakarta Bean Validation
- Testcontainers (integration/concurrency tests)
- springdoc-openapi (OpenAPI spec generation)
- ArchUnit (architecture enforcement tests)

## Package Structure

Root package: `com.studio.booking`

```
com.studio.booking
├── shared/
│   ├── error/          # ApiException, ErrorCode enum, GlobalExceptionHandler
│   ├── time/           # ClockProvider, StudioTimeZone
│   └── web/            # PageResponse, common DTOs
├── member/
│   ├── api/            # controllers + request/response DTOs
│   ├── application/    # use-case services (@Transactional boundary)
│   ├── domain/         # entities, value objects, domain rules
│   └── infrastructure/ # JPA repositories, adapters
├── membership/         # plans, memberships, credit ledger
├── catalog/            # class types, instructors, sessions, recurrence
├── booking/            # bookings, waitlist, check-in, no-show
├── reporting/          # read-only query services (native SQL)
└── jobs/               # scheduled jobs + on-demand trigger endpoints
```

Dependency rule (enforced by ArchUnit): `api → application → domain ← infrastructure`. Modules communicate through application-layer interfaces only. No cross-module JPA entity references — only IDs cross module boundaries.

## Architecture Rules

- `@Transactional` belongs **only** in `application` packages — never in controllers or repositories
- Controllers are thin: validate input shape, call one application service, return the response DTO
- Repositories are never `@Transactional` on their own
- No JPA entity references across module boundaries — only UUID foreign keys
- ArchUnit tests enforce the above at CI time

## API Conventions

- Base path: `/api/v1`
- Content-Type: `application/json; charset=utf-8`
- Timestamps: input accepts any ISO-8601 offset; output always returns UTC with `Z`
- Dates (reports, recurrence): `YYYY-MM-DD`, interpreted in `studio.timezone` config property
- IDs: UUID strings everywhere
- Unknown JSON fields: rejected with `422 VALIDATION_FAILED`
- Create responses: `201 Created` with `Location` header and full resource body
- Update responses: `200 OK` with full resource body
- Deactivate/status-flip responses: `200 OK` with resource body (never `204`)
- Lists: `200 OK` with page envelope `{ content, page: { number, size, totalElements, totalPages } }`
- Pagination: `page` (0-based), `size` (default 20, max 100), `sort` (`field,asc|desc`)

## Error Envelope

Based on RFC 9457 Problem Details, extended with `code` and `errors`:

```json
{
  "type": "https://api.studio.example/errors/<kebab-code>",
  "title": "Human label",
  "status": 422,
  "code": "VALIDATION_FAILED",
  "detail": "Request validation failed. See errors.",
  "instance": "/api/v1/...",
  "timestamp": "2026-09-12T10:15:30.123Z",
  "traceId": "4c1a9e7b3f0d2a11",
  "errors": [{ "field": "email", "code": "INVALID_FORMAT", "message": "...", "rejectedValue": "..." }]
}
```

HTTP status rules:
- `400` — malformed JSON / unreadable body
- `403` — domain-level "not permitted" (e.g. suspended member booking)
- `404` — path-referenced resource does not exist; body IDs that don't exist → `422` with field code `NOT_FOUND`
- `409` — business rule conflict / state conflict / uniqueness / concurrency
- `422` — input validation failure; always includes `errors[]`
- `500` — unhandled; same envelope, `code=INTERNAL_ERROR`, no stack trace

Rule of thumb: **422 = request wrong regardless of state; 409 = well-formed but current state forbids it**.

## Concurrency & Locking

- Pessimistic row lock (`SELECT … FOR UPDATE`) on `class_session` for any capacity-affecting operation
- `@Version` (optimistic) on all mutable aggregates for stale-update detection
- Lock acquisition order (always follow to prevent deadlocks): `class_session → member → membership`
- When locking multiple members in one transaction, lock in ascending `member_id` order
- Set `jakarta.persistence.lock.timeout` (~3 s) so a stuck lock surfaces as `CONCURRENT_MODIFICATION 409`

## Time Handling

- All DB persistence in `timestamptz` (UTC)
- All "now" reads via an injectable `Clock` bean — never `Instant.now()` or `LocalDateTime.now()` directly
- Tests use `Clock.fixed` to freeze time — essential for the 4-hour and 15-minute window rules
- Studio timezone (`studio.timezone`, IANA string e.g. `Asia/Kolkata`) used only for recurrence generation and report date-bucketing

Time rules:
- Bookable: `session.startsAt > now`
- Refund window: `Duration.between(now, startsAt) >= 4h` → refund; strictly less → late cancel, no refund
- Check-in window: `[startsAt − 15min, endsAt]` inclusive on both ends
- No-show rolling window: `recorded_at > now − 30 days` (exclusive lower bound)
- Overlap: half-open `[start, end)` — back-to-back sessions do NOT overlap
- Membership active: `startsAt ≤ now < expiresAt`

## Database Conventions

- `varchar` + `CHECK` for status columns, not native `ENUM` (easier migrations)
- All mutable tables: `created_at timestamptz`, `updated_at timestamptz`, `version bigint`
- `updated_at` maintained by a trigger function (V5 migration)
- IDs: `uuid`, default `gen_random_uuid()` (pgcrypto extension)
- Money: `numeric(10,2)`, serialised as decimal string with separate ISO-4217 `currency` field
- Soft delete: none — entities deactivated via `status`/`active` flags only
- No physical deletes through the API

Required extensions: `pgcrypto` (uuid generation), `btree_gist` (instructor/room exclusion constraints).

## Flyway Migrations

Forward-only. Naming: `V{n}__{description}.sql`.

| Version | Content |
|---|---|
| V1 | extensions, `member`, `membership_plan`, `membership`, `credit_transaction` (without booking FK) |
| V2 | `instructor`, `room`, `class_type`, `class_session` |
| V3 | `booking`, `waitlist_entry`, `no_show_record`, credit_transaction→booking FK |
| V4 | `notification_log`, `job_run` |
| V5 | `updated_at` trigger applied to all mutable tables |

## Key Domain Invariants

These must hold at all times; most are enforced at DB level:

- I1: At most one `ACTIVE` membership per member (partial unique index)
- I2: At most one `PENDING` membership per member (partial unique index)
- I3: At most one non-cancelled booking per (member, session) (partial unique index)
- I4: At most one `WAITING` waitlist entry per (member, session) (partial unique index)
- I5: `booked_count <= capacity` on a session (check constraint + row lock)
- I6: No two non-cancelled sessions overlap for the same instructor (exclusion constraint, btree_gist)
- I7: No two non-cancelled sessions overlap in the same room (exclusion constraint, btree_gist)
- I8: Credit deduction/refund is atomic with booking state change (same transaction)
- I9: `credits_remaining >= 0` (check constraint)
- I10: `ends_at > starts_at` on sessions; `expires_at > starts_at` on memberships (check constraints)
- I11: Member overlapping bookings use half-open interval `[start, end)` (application check under lock)

## Credit Ledger

`credit_transaction` is append-only. `membership.credits_remaining` is the materialised balance. Every credit change writes:
- a `credit_transaction` row (delta, reason, balance_after) in the same transaction
- a guarded UPDATE: `SET credits_remaining = credits_remaining - 1 WHERE id = ? AND credits_remaining >= 1`; check affected-row count

Valid reasons: `BOOKING`, `WAITLIST_PROMOTION`, `CANCEL_REFUND`, `SESSION_CANCELLED_REFUND`, `STAFF_ADJUSTMENT`.

## Waitlist Promotion Routine

Runs inside the same transaction that frees a spot (no window where a spot is free but waitlist unserved). Uses `FOR UPDATE SKIP LOCKED` on waitlist entries. Eligibility checks in order: member ACTIVE → active membership → credits available → no overlapping booking → no existing booking on this session. Failed eligibility → `SKIPPED` (terminal, not moved to back). Successful → `PROMOTED`, booking created with `source=WAITLIST_PROMOTION`.

## Scheduled Jobs

All jobs:
- Single-instance guarded by a `job_run` row in `RUNNING` status
- Idempotent; each unit of work (session/membership) in its own transaction
- Disabled in `test` profile; driven by on-demand endpoint `POST /api/v1/jobs/{jobName}/run` with optional `asOf` override (non-prod only)

| Job | Default cadence |
|---|---|
| `membership-lifecycle` | every 5 min |
| `waitlist-expiry` | every 1 min |
| `no-show-sweep` | every 5 min |
| `waitlist-promotion` | every 5 min (defensive sweep) |
| `booked-count-reconcile` | nightly |

Cadences are config properties: `jobs.<name>.cron`.

## Idempotency

`POST /api/v1/bookings` and `POST /api/v1/sessions/{sessionId}/waitlist` accept `Idempotency-Key` header (≤64 chars). Same member + same key → return original response with `200`. Key stored on the row — no separate store.

## Notifications

`notification_log` table is log-only — no actual email/SMS delivery. Every notifiable event writes a row. Event types: `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `WAITLIST_PROMOTED`, `SESSION_CANCELLED`, `MEMBER_SUSPENDED_NO_SHOW`.

## Testing Requirements

- Domain unit tests: JUnit 5, AssertJ, `Clock.fixed`. Test every time-window boundary: 3h59m59s / 4h / 4h01s for cancel refund; 14m59s / 15m / 15m01s for check-in; 29d / 30d / 31d for no-show rolling window.
- Repository tests: Testcontainers PostgreSQL + Flyway. Cover all constraint behaviours (partial unique indexes, exclusion constraints, check constraints).
- Application/service tests: Spring context + Testcontainers. Cover transactional atomicity and promotion skip logic.
- Concurrency tests: `ExecutorService` + Testcontainers. N threads booking the last spot → exactly one `201`, rest `409`.
- API integration tests: MockMvc or RestAssured + Testcontainers. One test per endpoint: happy path + each documented error code + error envelope shape + unknown-field rejection.
- ArchUnit tests: enforce module dependency rule and `@Transactional` placement rule.
- Test data builders (`aMember()`, `aSession().startingIn(hours(5))`) in a `testFixtures` source set.
- OpenAPI spec generated by springdoc-openapi, checked into repo; CI fails on spec drift.

## What Is Out of Scope

Authentication/authorization, payments, mobile app, real email/SMS delivery, social features, personal training, retail. The `X-Actor-Type` / `X-Actor-Id` headers are recorded but not enforced until authz is added.
