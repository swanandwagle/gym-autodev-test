-- V11: add membership_id to booking table

ALTER TABLE booking
    ADD COLUMN membership_id uuid NOT NULL REFERENCES membership(id);
