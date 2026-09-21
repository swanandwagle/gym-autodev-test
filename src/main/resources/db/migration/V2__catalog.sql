-- instructor
CREATE TABLE instructor (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          varchar(255) NOT NULL,
    full_name      varchar(255) NOT NULL,
    bio            text,
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_instructor_email ON instructor (lower(email));

-- room
CREATE TABLE room (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           varchar(255) NOT NULL,
    capacity       int          NOT NULL CHECK (capacity > 0),
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_room_name ON room (lower(name));

-- class_type
CREATE TABLE class_type (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(255) NOT NULL,
    description         text,
    duration_minutes    int          NOT NULL
                            CONSTRAINT ck_class_type_duration_range
                            CHECK (duration_minutes BETWEEN 5 AND 480),
    default_capacity    int          NOT NULL
                            CONSTRAINT ck_class_type_default_capacity_range
                            CHECK (default_capacity BETWEEN 1 AND 500),
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);

-- class_session
CREATE TABLE class_session (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    class_type_id   uuid         NOT NULL REFERENCES class_type(id),
    instructor_id   uuid         NOT NULL REFERENCES instructor(id),
    room_id         uuid         NOT NULL REFERENCES room(id),
    starts_at       timestamptz  NOT NULL,
    ends_at         timestamptz  NOT NULL,
    capacity        int          NOT NULL CHECK (capacity > 0),
    booked_count    int          NOT NULL DEFAULT 0
                        CONSTRAINT ck_session_booked_count_nonneg CHECK (booked_count >= 0),
    status          varchar(20)  NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_session_time_order CHECK (ends_at > starts_at),
    CONSTRAINT ck_session_booked_not_exceed_capacity CHECK (booked_count <= capacity)
);

-- I6: no two non-cancelled sessions overlap for the same instructor
-- Uses half-open interval [starts_at, ends_at) via tstzrange with '[)' bounds
ALTER TABLE class_session
    ADD CONSTRAINT ex_session_instructor
    EXCLUDE USING gist (
        instructor_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');

-- I7: no two non-cancelled sessions overlap in the same room
ALTER TABLE class_session
    ADD CONSTRAINT ex_session_room
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');
