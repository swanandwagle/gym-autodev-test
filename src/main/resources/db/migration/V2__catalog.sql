-- V2: instructor, room, class_type, class_session

-- -------------------------------------------------------------------------
-- instructor
-- -------------------------------------------------------------------------
CREATE TABLE instructor (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(255) NOT NULL,
    email       varchar(255) NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_instructor_email UNIQUE (email)
);

-- -------------------------------------------------------------------------
-- room
-- -------------------------------------------------------------------------
CREATE TABLE room (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(255) NOT NULL,
    capacity    int          NOT NULL CHECK (capacity > 0),
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0
);

-- -------------------------------------------------------------------------
-- class_type
-- -------------------------------------------------------------------------
CREATE TABLE class_type (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(255) NOT NULL,
    description         text,
    duration_minutes    int          NOT NULL CONSTRAINT ck_class_type_duration_range CHECK (duration_minutes >= 5 AND duration_minutes <= 480),
    default_capacity    int          NOT NULL CONSTRAINT ck_class_type_default_capacity_range CHECK (default_capacity >= 1 AND default_capacity <= 500),
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ux_class_type_name_case_insensitive UNIQUE (lower(name))
);

-- -------------------------------------------------------------------------
-- class_session
-- -------------------------------------------------------------------------
CREATE TABLE class_session (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    class_type_id   uuid         NOT NULL REFERENCES class_type(id),
    instructor_id   uuid         NOT NULL REFERENCES instructor(id),
    room_id         uuid         NOT NULL REFERENCES room(id),
    starts_at       timestamptz  NOT NULL,
    ends_at         timestamptz  NOT NULL,
    capacity        int          NOT NULL CHECK (capacity > 0),
    booked_count    int          NOT NULL DEFAULT 0 CHECK (booked_count >= 0),
    status          varchar(32)  NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT chk_session_dates CHECK (ends_at > starts_at),
    CONSTRAINT chk_session_capacity CHECK (booked_count <= capacity)
);

-- I6: no two non-cancelled sessions overlap for the same instructor
ALTER TABLE class_session
    ADD CONSTRAINT excl_session_instructor_overlap
    EXCLUDE USING gist (
        instructor_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status != 'CANCELLED')
    DEFERRABLE INITIALLY DEFERRED;

-- I7: no two non-cancelled sessions overlap in the same room
ALTER TABLE class_session
    ADD CONSTRAINT excl_session_room_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status != 'CANCELLED')
    DEFERRABLE INITIALLY DEFERRED;
