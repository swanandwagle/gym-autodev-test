-- V8: Add credits_initial column to membership table for snapshot isolation
ALTER TABLE membership
    ADD COLUMN credits_initial int;

-- Update existing rows: for unlimited (classCredits IS NULL), set credits_initial to NULL
-- For credit-based, use credits_remaining as best guess of original value
UPDATE membership m
SET credits_initial = (
    SELECT p.class_credits
    FROM membership_plan p
    WHERE p.id = m.plan_id
);
