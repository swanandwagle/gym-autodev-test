package com.studio.booking.booking.infrastructure;

import com.studio.booking.booking.domain.NoShowRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, UUID> {
}
