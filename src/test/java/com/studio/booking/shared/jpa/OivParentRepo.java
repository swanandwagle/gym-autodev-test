package com.studio.booking.shared.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OivParentRepo extends JpaRepository<OivParent, UUID> {
}
