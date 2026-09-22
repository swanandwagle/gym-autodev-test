# Error Status Semantics

All error responses from this API use the RFC 9457 Problem Details format, extended with
`code`, `traceId`, and (on 422 only) `errors[]`.

## Envelope Shape

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

`errors` is **only** present on 422 responses. It is omitted entirely (not `null`, not `[]`) on all
other status codes.

## Status Code Table

| HTTP | `code`                  | Origin / meaning                                                      |
|------|-------------------------|-----------------------------------------------------------------------|
| 400  | `MALFORMED_REQUEST`     | Unreadable body / malformed JSON                                      |
| 403  | `NOT_PERMITTED`         | Domain-level "not allowed" (e.g. suspended member attempting booking) |
| 404  | `NOT_FOUND`             | Path-referenced resource does not exist                               |
| 409  | `CONCURRENT_MODIFICATION` | Optimistic-lock failure / stale-update detected (`@Version` conflict) |
| 409  | `CONFLICT`              | Business-rule / state conflict / DB uniqueness violation              |
| 422  | `VALIDATION_FAILED`     | Bean-validation failure; always includes `errors[]`                   |
| 500  | `INTERNAL_ERROR`        | Unhandled exception; no internal detail, class name, or stack exposed |

**Rule of thumb:** `422` = the request would be wrong regardless of system state;
`409` = the request is well-formed but the current state forbids it.

## Type URI

Every `code` maps to a stable URI:

```
https://api.studio.example/errors/<kebab-code>
```

Examples:

| Code                    | URI                                                                    |
|-------------------------|------------------------------------------------------------------------|
| `MALFORMED_REQUEST`     | `https://api.studio.example/errors/malformed-request`                 |
| `NOT_FOUND`             | `https://api.studio.example/errors/not-found`                         |
| `CONCURRENT_MODIFICATION` | `https://api.studio.example/errors/concurrent-modification`          |
| `VALIDATION_FAILED`     | `https://api.studio.example/errors/validation-failed`                 |
| `INTERNAL_ERROR`        | `https://api.studio.example/errors/internal-error`                    |

The base URL is configurable via `studio.api.base-url` (default `https://api.studio.example`).

## Correlation / Trace IDs

Every request and response carries a trace ID for log correlation:

- If the client supplies `X-Request-Id` header, that value is used as the traceId.
- If no header is supplied, a UUID (without dashes, 32 hex chars) is generated.
- The traceId appears in:
  1. The `traceId` field of the error envelope.
  2. The `X-Request-Id` response header (echoed back).
  3. Every server-side log line for that request (via MDC key `traceId`).
  4. The ERROR log line emitted by `GlobalExceptionHandler` for each error.

## Five Error Origins

`GlobalExceptionHandler` handles errors from all five origins with identical envelope shape:

1. **Bean-validation** (`@Valid` / `ConstraintViolationException`) → 422 `VALIDATION_FAILED`
2. **`ApiException`** (application / domain layer) → status/code carried by the exception
3. **`DataIntegrityViolationException`** (DB constraint) → 409 `CONFLICT`
4. **`OptimisticLockingFailureException`** (`@Version` conflict) → 409 `CONCURRENT_MODIFICATION`
5. **Unhandled `Exception`** (catch-all) → 500 `INTERNAL_ERROR` with generic detail only

## Validation Layer — Constraint-to-Field-Code Mapping

All `errors[]` entries in a 422 response carry a `code` derived from the violated constraint:

| Constraint annotation | Emitted `code`   | Notes                                                     |
|-----------------------|------------------|-----------------------------------------------------------|
| `@NotBlank`           | `NOTBLANK`       |                                                           |
| `@NotNull`            | `NOTNULL`        |                                                           |
| `@NotEmpty`           | `NOTEMPTY`       |                                                           |
| `@Min`                | `MIN`            |                                                           |
| `@Max`                | `MAX`            |                                                           |
| `@Size` (too short)   | `TOO_SHORT`      | Rejected value length < `min`                             |
| `@Size` (too long)    | `TOO_LONG`       | Rejected value length > `max`                             |
| `@Email`              | `EMAIL`          |                                                           |
| `@Pattern`            | `PATTERN`        |                                                           |
| `@ValidUuid`          | `INVALID_FORMAT` | Custom constraint for UUID path variables                 |
| Unknown JSON field    | `UNKNOWN_FIELD`  | 422 with `errors[]`; distinguishable from malformed JSON  |
| Invalid enum value    | `INVALID_ENUM`   | Message lists all accepted enum values                    |
| Malformed JSON body   | _(none)_         | 400 `MALFORMED_REQUEST`; no `errors[]`                    |

**Accumulation guarantee:** all constraint violations on a single request are collected and returned
together. A request with five invalid fields returns one 422 with five `errors[]` entries.

**Nested path:** for nested objects and collections, the `field` uses the full JSON path including
collection index, e.g. `items[1].label`.

**`rejectedValue` safety:** string rejected values are truncated at 200 characters to prevent
attacker-controlled content from bloating error responses.

## Timestamp Binding Policy

- Input: accepts any ISO-8601 timestamp with any UTC offset (e.g. `2026-09-22T10:00:00+05:30`).
- Storage: always persisted as UTC (`timestamptz`).
- Output: always serialised as UTC with `Z` suffix (e.g. `2026-09-22T04:30:00Z`).

Use `Instant` for all timestamp fields in request/response DTOs. Jackson's `JavaTimeModule`
handles the ISO-8601 offset → UTC conversion automatically.
