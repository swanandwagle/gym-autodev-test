package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.Instructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    @Query("SELECT i FROM Instructor i WHERE LOWER(i.email) = LOWER(:email)")
    Optional<Instructor> findByEmailCaseInsensitive(@Param("email") String email);

    @Query("SELECT i FROM Instructor i WHERE i.active = true ORDER BY i.createdAt ASC")
    Page<Instructor> findAllActive(Pageable pageable);

    @Query("SELECT i FROM Instructor i WHERE i.active = true AND (LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(i.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Instructor> searchByNameOrEmail(@Param("q") String q, Pageable pageable);

    @Query(value = "SELECT DISTINCT i.* FROM instructor i WHERE i.active = true AND (SELECT COUNT(*) FROM jsonb_array_elements_text(i.specialties) elem WHERE LOWER(elem) = LOWER(:specialty)) > 0", nativeQuery = true)
    Page<Instructor> findBySpecialty(@Param("specialty") String specialty, Pageable pageable);

    @Query("SELECT cs FROM ClassSession cs WHERE cs.instructorId = :instructorId AND cs.startsAt > :now AND cs.status = 'SCHEDULED' ORDER BY cs.startsAt ASC")
    List<ClassSession> findFutureScheduledSessions(@Param("instructorId") UUID instructorId, @Param("now") Instant now);
}
