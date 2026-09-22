# Database Constraint → Error Code Mapping

Every named constraint that can surface as a `DataIntegrityViolationException` must have
an entry in `ConstraintViolationTranslator.MAPPING`. When you add a new constraint to a
migration, add the corresponding row here and in the mapping table.

**Adding a new constraint:**
1. Name the constraint explicitly in the migration SQL (e.g. `CONSTRAINT my_constraint ...`).
2. Add a row to `ConstraintViolationTranslator.MAPPING`.
3. Add a test in `ConstraintViolationTranslatorTest` that triggers the constraint directly
   and asserts the mapped `ErrorCode`.
4. Add a row to this table.

---

## Mapping Table

| Constraint name | Table | Type | ErrorCode | HTTP |
|---|---|---|---|---|
| `uq_booking_member_session` | `booking` | partial unique index | `DUPLICATE_BOOKING` | 409 |
| `uq_membership_member_active` | `membership` | partial unique index | `MEMBERSHIP_ALREADY_ACTIVE` | 409 |
| `uq_membership_member_pending` | `membership` | partial unique index | `MEMBERSHIP_ALREADY_ACTIVE` | 409 |
| `uq_waitlist_member_session` | `waitlist_entry` | partial unique index | `WAITLIST_ALREADY_JOINED` | 409 |
| `excl_session_instructor_overlap` | `class_session` | GiST exclusion | `INSTRUCTOR_SCHEDULE_CONFLICT` | 409 |
| `excl_session_room_overlap` | `class_session` | GiST exclusion | `ROOM_SCHEDULE_CONFLICT` | 409 |
| `uq_no_show_booking` | `no_show_record` | unique | *(swallowed by sweep)* | — |

## Fallback Behaviour

Any constraint **not** in the table above returns `409 CONCURRENT_MODIFICATION` with the
generic detail message. The constraint name is logged at `ERROR` so it can be found and
mapped if it turns out to be a business-meaningful conflict.

No Postgres error text, table name, or SQL state is ever included in the API response.

## Constraints Intentionally Not Mapped

| Constraint | Reason not mapped |
|---|---|
| `uq_member_email` | Duplicate email is caught by application pre-check before DB write |
| `uq_instructor_email` | Same — application validates uniqueness before insert |
| `uq_booking_member_idempotency` | Idempotency replay is handled before reaching the DB |
| `uq_waitlist_member_idempotency` | Same |
| `chk_session_capacity` | Enforced by application under pessimistic lock; raw check hit = bug |
| `chk_membership_dates` | Input validated by bean validation layer before persistence |
| `chk_session_dates` | Same |
