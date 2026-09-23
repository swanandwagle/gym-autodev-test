-- V6: Add suspended_at column to member table

ALTER TABLE member ADD COLUMN suspended_at timestamptz;
