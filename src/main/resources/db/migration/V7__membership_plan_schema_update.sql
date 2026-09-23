-- V7: Update membership_plan schema to rename columns and add tier
-- Rename credits → class_credits (make nullable for unlimited plans)
-- Rename validity_days → duration_days
-- Add tier column with BASIC, PREMIUM, VIP

ALTER TABLE membership_plan
    RENAME COLUMN credits TO class_credits;

ALTER TABLE membership_plan
    RENAME COLUMN validity_days TO duration_days;

-- Modify class_credits to be nullable and adjust constraint
ALTER TABLE membership_plan
    DROP CONSTRAINT membership_plan_credits_check;

ALTER TABLE membership_plan
    ALTER COLUMN class_credits DROP NOT NULL;

ALTER TABLE membership_plan
    ADD CONSTRAINT chk_class_credits_positive CHECK (class_credits IS NULL OR class_credits > 0);

-- Add tier column
ALTER TABLE membership_plan
    ADD COLUMN tier varchar(32) DEFAULT 'BASIC' NOT NULL
    CONSTRAINT chk_tier CHECK (tier IN ('BASIC', 'PREMIUM', 'VIP'));

-- Add case-insensitive unique constraint on name
ALTER TABLE membership_plan
    ADD CONSTRAINT uq_membership_plan_name_case_insensitive UNIQUE (lower(name));
