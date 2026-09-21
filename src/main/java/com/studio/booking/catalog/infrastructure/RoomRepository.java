package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
}
