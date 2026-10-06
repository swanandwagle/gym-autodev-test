-- notification_log (append-only; no updated_at trigger)
CREATE TABLE notification_log (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       uuid        NOT NULL REFERENCES member(id),
    event_type      varchar(40) NOT NULL
                        CONSTRAINT ck_notification_log_event_type
                        CHECK (event_type IN (
                            'BOOKING_CONFIRMED',
                            'BOOKING_CANCELLED',
                            'WAITLIST_PROMOTED',
                            'SESSION_CANCELLED',
                            'MEMBER_SUSPENDED_NO_SHOW'
                        )),
    channel         varchar(20) NOT NULL
                        CONSTRAINT ck_notification_log_channel
                        CHECK (channel IN ('EMAIL', 'SMS')),
    payload         jsonb       NOT NULL,
    triggered_by    varchar(20) NOT NULL
                        CONSTRAINT ck_notification_log_triggered_by
                        CHECK (triggered_by IN ('SYSTEM', 'STAFF')),
    sent_at         timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- job_run
CREATE TABLE job_run (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name    varchar(60) NOT NULL,
    status      varchar(20) NOT NULL DEFAULT 'RUNNING'
                    CONSTRAINT ck_job_run_status
                    CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    error       text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    version     bigint      NOT NULL DEFAULT 0
);

-- Single-instance guard: at most one RUNNING row per job_name
CREATE UNIQUE INDEX ux_job_run_single_active
    ON job_run (job_name)
    WHERE status = 'RUNNING';
