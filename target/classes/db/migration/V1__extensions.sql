-- Extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- member
CREATE TABLE member (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email          varchar(255) NOT NULL,
    full_name      varchar(255) NOT NULL,
    phone          varchar(50),
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    suspension_reason  varchar(255),
    joined_at      timestamptz  NOT NULL DEFAULT now(),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_member_email ON member (lower(email));

-- membership_plan
CREATE TABLE membership_plan (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           varchar(255) NOT NULL,
    class_credits  int          CONSTRAINT ck_membership_plan_class_credits_positive CHECK (class_credits > 0),  -- NULL = unlimited
    duration_days  int          NOT NULL CHECK (duration_days > 0),
    price          numeric(10,2) NOT NULL,
    currency       varchar(3)   NOT NULL DEFAULT 'USD',
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

-- membership
CREATE TABLE membership (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           uuid         NOT NULL REFERENCES member(id),
    plan_id             uuid         NOT NULL REFERENCES membership_plan(id),
    status              varchar(20)  NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    unlimited           boolean      NOT NULL DEFAULT false,
    credits_remaining   int          CONSTRAINT ck_membership_credits_remaining_nonneg CHECK (credits_remaining >= 0),
    starts_at           timestamptz  NOT NULL,
    expires_at          timestamptz  NOT NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_membership_expires_after_starts CHECK (expires_at > starts_at),
    CONSTRAINT ck_membership_credit_shape CHECK (
        (unlimited = true  AND credits_remaining IS NULL) OR
        (unlimited = false AND credits_remaining IS NOT NULL)
    )
);

-- I1: at most one ACTIVE membership per member
CREATE UNIQUE INDEX ux_membership_one_active_per_member
    ON membership (member_id)
    WHERE status = 'ACTIVE';

-- I2: at most one PENDING membership per member
CREATE UNIQUE INDEX ux_membership_one_pending_per_member
    ON membership (member_id)
    WHERE status = 'PENDING';

-- credit_transaction (append-only ledger; booking FK added in V3)
CREATE TABLE credit_transaction (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id  uuid         NOT NULL REFERENCES membership(id),
    delta          int          NOT NULL CONSTRAINT ck_credit_transaction_delta_nonzero CHECK (delta <> 0),
    reason         varchar(50)  NOT NULL
                       CHECK (reason IN (
                           'BOOKING',
                           'WAITLIST_PROMOTION',
                           'CANCEL_REFUND',
                           'SESSION_CANCELLED_REFUND',
                           'STAFF_ADJUSTMENT'
                       )),
    balance_after  int          NOT NULL CHECK (balance_after >= 0),
    recorded_at    timestamptz  NOT NULL DEFAULT now(),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);
