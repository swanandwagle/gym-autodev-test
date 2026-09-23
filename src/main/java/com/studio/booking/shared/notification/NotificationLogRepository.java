package com.studio.booking.shared.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}
