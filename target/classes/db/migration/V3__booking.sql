-- booking
CREATE TABLE booking (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           uuid         NOT NULL REFERENCES member(id),
    session_id          uuid         NOT NULL REFERENCES class_session(id),
    status              varchar(20)  NOT NULL DEFAULT 'BOOKED'
                            CONSTRAINT ck_booking_status
                            CHECK (status IN ('BOOKED', 'CANCELLED', 'CHECKED_IN', 'NO_SHOW')),
    source              varchar(30)  NOT NULL DEFAULT 'DIRECT'
                            CONSTRAINT ck_booking_source
                            CHECK (source IN ('DIRECT', 'WAITLIST_PROMOTION')),
    cancellation_type   varchar(20)
                            CONSTRAINT ck_booking_cancellation_type
                            CHECK (cancellation_type IN ('NORMAL', 'LATE', 'SESSION_CANCELLED')),
    checked_in_by       varchar(20)
                            CONSTRAINT ck_booking_checked_in_by
                            CHECK (checked_in_by IN ('MEMBER', 'STAFF')),
    idempotency_key     varchar(64),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);

-- I3: at most one non-cancelled booking per (member, session)
CREATE UNIQUE INDEX ux_booking_one_live_per_member_session
    ON booking (member_id, session_id)
    WHERE status <> 'CANCELLED';

-- idempotency: same member + same key must not produce a second row
CREATE UNIQUE INDEX ux_booking_idempotency_key_per_member
    ON booking (member_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- waitlist_entry
CREATE TABLE waitlist_entry (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      uuid         NOT NULL REFERENCES class_session(id),
    member_id       uuid         NOT NULL REFERENCES member(id),
    status          varchar(20)  NOT NULL DEFAULT 'WAITING'
                        CONSTRAINT ck_waitlist_entry_status
                        CHECK (status IN ('WAITING', 'PROMOTED', 'EXPIRED', 'CANCELLED', 'SKIPPED')),
    sequence_no     int          NOT NULL,
    skip_reason     varchar(50)
                        CONSTRAINT ck_waitlist_entry_skip_reason
                        CHECK (skip_reason IN (
                            'MEMBER_INACTIVE',
                            'NO_ACTIVE_MEMBERSHIP',
                            'INSUFFICIENT_CREDITS',
                            'OVERLAPPING_BOOKING',
                            'ALREADY_BOOKED'
                        )),
    idempotency_key varchar(64),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

-- I4: at most one WAITING entry per (member, session)
CREATE UNIQUE INDEX ux_waitlist_one_waiting_per_member_session
    ON waitlist_entry (member_id, session_id)
    WHERE status = 'WAITING';

-- sequence_no must be unique per session (ordering within waitlist)
CREATE UNIQUE INDEX ux_waitlist_sequence_no_per_session
    ON waitlist_entry (session_id, sequence_no);

-- no_show_record
CREATE TABLE no_show_record (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  uuid        NOT NULL REFERENCES booking(id),
    member_id   uuid        NOT NULL REFERENCES member(id),
    session_id  uuid        NOT NULL REFERENCES class_session(id),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    version     bigint      NOT NULL DEFAULT 0
);

-- sweep idempotency: only one no-show record per booking
CREATE UNIQUE INDEX ux_no_show_record_per_booking
    ON no_show_record (booking_id);

-- Close the deferred FK: credit_transaction.booking_id → booking.id
ALTER TABLE credit_transaction
    ADD COLUMN booking_id uuid REFERENCES booking(id);
