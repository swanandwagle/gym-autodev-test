-- V10: add cancellation fields to class_session; add resolution fields to waitlist_entry

ALTER TABLE class_session
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancel_reason varchar(255);

ALTER TABLE waitlist_entry
    ADD COLUMN resolved_at timestamptz;
