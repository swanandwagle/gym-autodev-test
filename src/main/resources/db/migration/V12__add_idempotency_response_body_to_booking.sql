-- V12: add idempotency_response_body to booking table

ALTER TABLE booking
    ADD COLUMN idempotency_response_body text;
