<<<<<<< HEAD
-- Trigger function: sets updated_at = now() on every UPDATE
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
=======
-- V5: updated_at trigger applied to all mutable tables

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
>>>>>>> 6708a22 (GYM-21: Database constraint violation translator (#13))
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
<<<<<<< HEAD
$$;

-- Apply trigger to every mutable table (append-only tables are excluded)
CREATE TRIGGER trg_member_updated_at
    BEFORE UPDATE ON member
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_membership_plan_updated_at
    BEFORE UPDATE ON membership_plan
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_membership_updated_at
    BEFORE UPDATE ON membership
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_instructor_updated_at
    BEFORE UPDATE ON instructor
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_room_updated_at
    BEFORE UPDATE ON room
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_class_type_updated_at
    BEFORE UPDATE ON class_type
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_class_session_updated_at
    BEFORE UPDATE ON class_session
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_booking_updated_at
    BEFORE UPDATE ON booking
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_waitlist_entry_updated_at
    BEFORE UPDATE ON waitlist_entry
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_job_run_updated_at
    BEFORE UPDATE ON job_run
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
=======
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'member', 'membership_plan', 'membership',
        'instructor', 'room', 'class_type', 'class_session',
        'booking', 'waitlist_entry'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            tbl, tbl
        );
    END LOOP;
END;
$$;
>>>>>>> 6708a22 (GYM-21: Database constraint violation translator (#13))
