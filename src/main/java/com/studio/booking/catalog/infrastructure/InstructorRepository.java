package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InstructorRepository extends JpaRepository<Instructor, UUID> {
}
