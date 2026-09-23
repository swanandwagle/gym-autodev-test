-- V6: Add case-insensitive unique constraint on room name

ALTER TABLE room
    ADD CONSTRAINT ux_room_name_case_insensitive UNIQUE (lower(name));
