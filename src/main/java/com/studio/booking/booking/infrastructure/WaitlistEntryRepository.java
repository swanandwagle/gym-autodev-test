package com.studio.booking.booking.infrastructure;

import com.studio.booking.booking.domain.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {
}
