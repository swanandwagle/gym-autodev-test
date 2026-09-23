package com.studio.booking.catalog.application;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InstructorDeactivationGuard {

    private final FutureSessionCommitmentChecker commitmentChecker;

    public InstructorDeactivationGuard(FutureSessionCommitmentChecker commitmentChecker) {
        this.commitmentChecker = commitmentChecker;
    }

    public void checkCanDeactivate(UUID instructorId) {
        FutureCommitmentResult result = commitmentChecker.forInstructor(instructorId);

        if (result.count() > 0) {
            throw new ApiException(ErrorCode.INSTRUCTOR_HAS_FUTURE_SESSIONS,
                    String.format("Instructor has %d future scheduled session(s). Earliest starts at %s.",
                            result.count(), result.earliestStart()));
        }
    }
}
