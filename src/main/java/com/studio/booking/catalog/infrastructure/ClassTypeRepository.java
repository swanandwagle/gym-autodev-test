package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.ClassType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClassTypeRepository extends JpaRepository<ClassType, UUID> {
}
