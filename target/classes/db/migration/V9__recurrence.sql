-- V9: add recurrence_id to class_session for bulk recurring sessions

ALTER TABLE class_session ADD COLUMN recurrence_id uuid;
