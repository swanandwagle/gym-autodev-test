-- V4: notification_log, job_run

-- -------------------------------------------------------------------------
-- notification_log
-- -------------------------------------------------------------------------
CREATE TABLE notification_log (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       uuid        NOT NULL REFERENCES member(id),
    event_type      varchar(64) NOT NULL CHECK (event_type IN (
                        'BOOKING_CONFIRMED', 'BOOKING_CANCELLED', 'WAITLIST_PROMOTED',
                        'SESSION_CANCELLED', 'MEMBER_SUSPENDED_NO_SHOW')),
    reference_id    uuid,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------------------
-- job_run
-- -------------------------------------------------------------------------
CREATE TABLE job_run (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name    varchar(64) NOT NULL,
    status      varchar(32) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);

-- At most one RUNNING instance of each job at a time
CREATE UNIQUE INDEX uq_job_run_running
    ON job_run (job_name)
    WHERE status = 'RUNNING';
