package com.studio.booking.jobs.infrastructure;

import com.studio.booking.jobs.domain.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobRunRepository extends JpaRepository<JobRun, UUID> {
}
