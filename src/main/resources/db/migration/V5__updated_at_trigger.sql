-- Trigger function: sets updated_at = now() on every UPDATE
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
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
