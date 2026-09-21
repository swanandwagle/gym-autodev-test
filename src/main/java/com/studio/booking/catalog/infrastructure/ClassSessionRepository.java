package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {
}
