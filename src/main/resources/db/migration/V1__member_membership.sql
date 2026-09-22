-- V1: extensions, member, membership_plan, membership, credit_transaction

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -------------------------------------------------------------------------
-- member
-- -------------------------------------------------------------------------
CREATE TABLE member (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email        varchar(255) NOT NULL,
    name         varchar(255) NOT NULL,
    status       varchar(32)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_member_email UNIQUE (email)
);

-- -------------------------------------------------------------------------
-- membership_plan
-- -------------------------------------------------------------------------
CREATE TABLE membership_plan (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           varchar(255) NOT NULL,
    credits        int          NOT NULL CHECK (credits > 0),
    price          numeric(10,2) NOT NULL,
    currency       varchar(3)   NOT NULL,
    validity_days  int          NOT NULL CHECK (validity_days > 0),
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

-- -------------------------------------------------------------------------
-- membership
-- -------------------------------------------------------------------------
CREATE TABLE membership (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           uuid         NOT NULL REFERENCES member(id),
    plan_id             uuid         NOT NULL REFERENCES membership_plan(id),
    status              varchar(32)  NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    credits_total       int          NOT NULL,
    credits_remaining   int          NOT NULL CHECK (credits_remaining >= 0),
    starts_at           timestamptz  NOT NULL,
    expires_at          timestamptz  NOT NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT chk_membership_dates CHECK (expires_at > starts_at)
);

-- I1: at most one ACTIVE membership per member
CREATE UNIQUE INDEX uq_membership_member_active
    ON membership (member_id)
    WHERE status = 'ACTIVE';

-- I2: at most one PENDING membership per member
CREATE UNIQUE INDEX uq_membership_member_pending
    ON membership (member_id)
    WHERE status = 'PENDING';

-- -------------------------------------------------------------------------
-- credit_transaction (no booking FK yet — added in V3)
-- -------------------------------------------------------------------------
CREATE TABLE credit_transaction (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id   uuid         NOT NULL REFERENCES membership(id),
    booking_id      uuid,
    delta           int          NOT NULL,
    reason          varchar(64)  NOT NULL CHECK (reason IN (
                        'BOOKING', 'WAITLIST_PROMOTION', 'CANCEL_REFUND',
                        'SESSION_CANCELLED_REFUND', 'STAFF_ADJUSTMENT')),
    balance_after   int          NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now()
);
