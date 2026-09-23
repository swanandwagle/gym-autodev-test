package com.studio.booking.catalog.infrastructure;

import com.studio.booking.catalog.domain.ClassType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClassTypeRepository extends JpaRepository<ClassType, UUID> {
    @Query("SELECT ct FROM ClassType ct WHERE lower(ct.name) = lower(:name)")
    Optional<ClassType> findByNameCaseInsensitive(@Param("name") String name);

    @Query("SELECT ct FROM ClassType ct WHERE ct.active = true")
    Page<ClassType> findAllActive(Pageable pageable);
}
