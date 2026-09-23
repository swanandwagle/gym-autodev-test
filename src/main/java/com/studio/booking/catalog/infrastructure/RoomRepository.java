package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.Room;
import com.studio.booking.catalog.domain.ClassSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Query("SELECT r FROM Room r WHERE LOWER(r.name) = LOWER(:name)")
    Optional<Room> findByNameCaseInsensitive(@Param("name") String name);

    @Query("SELECT r FROM Room r WHERE r.active = true ORDER BY r.createdAt ASC")
    Page<Room> findAllActive(Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.active = true AND r.capacity >= :minCapacity ORDER BY r.createdAt ASC")
    Page<Room> findAllActiveWithMinCapacity(@Param("minCapacity") Integer minCapacity, Pageable pageable);

    @Query("SELECT r FROM Room r ORDER BY r.createdAt ASC")
    Page<Room> findAllWithoutFilters(Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.capacity >= :minCapacity ORDER BY r.createdAt ASC")
    Page<Room> findAllWithMinCapacity(@Param("minCapacity") Integer minCapacity, Pageable pageable);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.roomId = :roomId AND cs.startsAt > :now AND cs.status = 'SCHEDULED' ORDER BY cs.startsAt ASC")
    List<ClassSession> findFutureScheduledSessions(@Param("roomId") UUID roomId, @Param("now") Instant now);
}
