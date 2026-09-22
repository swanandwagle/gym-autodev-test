-- V3: booking, waitlist_entry, no_show_record, credit_transaction→booking FK

-- -------------------------------------------------------------------------
-- booking
-- -------------------------------------------------------------------------
CREATE TABLE booking (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           uuid         NOT NULL REFERENCES member(id),
    session_id          uuid         NOT NULL REFERENCES class_session(id),
    membership_id       uuid         NOT NULL REFERENCES membership(id),
    status              varchar(32)  NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED', 'CHECKED_IN', 'NO_SHOW')),
    source              varchar(32)  NOT NULL CHECK (source IN ('DIRECT', 'WAITLIST_PROMOTION')),
    idempotency_key     varchar(64),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);

-- I3: at most one non-cancelled booking per (member, session)
CREATE UNIQUE INDEX uq_booking_member_session
    ON booking (member_id, session_id)
    WHERE status != 'CANCELLED';

-- Idempotency key uniqueness per member
CREATE UNIQUE INDEX uq_booking_member_idempotency
    ON booking (member_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- -------------------------------------------------------------------------
-- waitlist_entry
-- -------------------------------------------------------------------------
CREATE TABLE waitlist_entry (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           uuid         NOT NULL REFERENCES member(id),
    session_id          uuid         NOT NULL REFERENCES class_session(id),
    membership_id       uuid         NOT NULL REFERENCES membership(id),
    status              varchar(32)  NOT NULL CHECK (status IN ('WAITING', 'PROMOTED', 'EXPIRED', 'SKIPPED', 'CANCELLED')),
    idempotency_key     varchar(64),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);

-- I4: at most one WAITING waitlist entry per (member, session)
CREATE UNIQUE INDEX uq_waitlist_member_session
    ON waitlist_entry (member_id, session_id)
    WHERE status = 'WAITING';

-- Idempotency key uniqueness per member
CREATE UNIQUE INDEX uq_waitlist_member_idempotency
    ON waitlist_entry (member_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- -------------------------------------------------------------------------
-- no_show_record
-- -------------------------------------------------------------------------
CREATE TABLE no_show_record (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       uuid        NOT NULL REFERENCES member(id),
    booking_id      uuid        NOT NULL REFERENCES booking(id),
    session_id      uuid        NOT NULL REFERENCES class_session(id),
    recorded_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_no_show_booking UNIQUE (booking_id)
);

-- -------------------------------------------------------------------------
-- Add booking FK to credit_transaction (deferred from V1)
-- -------------------------------------------------------------------------
ALTER TABLE credit_transaction
    ADD CONSTRAINT fk_credit_transaction_booking
    FOREIGN KEY (booking_id) REFERENCES booking(id);
