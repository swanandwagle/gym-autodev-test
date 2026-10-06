-- V2.1: Add specialties to instructor table

ALTER TABLE instructor
    ADD COLUMN specialties jsonb DEFAULT '[]'::jsonb NOT NULL;

CREATE INDEX idx_instructor_active ON instructor(active);
CREATE INDEX idx_instructor_email_lower ON instructor(lower(email));
