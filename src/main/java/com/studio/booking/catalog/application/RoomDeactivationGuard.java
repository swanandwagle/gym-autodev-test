package com.studio.booking.catalog.application;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RoomDeactivationGuard {

    private final FutureSessionCommitmentChecker commitmentChecker;

    public RoomDeactivationGuard(FutureSessionCommitmentChecker commitmentChecker) {
        this.commitmentChecker = commitmentChecker;
    }

    public void checkCanDeactivate(UUID roomId) {
        FutureCommitmentResult result = commitmentChecker.forRoom(roomId);

        if (result.count() > 0) {
            throw new ApiException(ErrorCode.ROOM_HAS_FUTURE_SESSIONS,
                    String.format("Room has %d future scheduled session(s). Earliest starts at %s.",
                            result.count(), result.earliestStart()));
        }
    }

    public FutureCommitmentResult checkCapacityReduction(UUID roomId, int proposedCapacity) {
        return commitmentChecker.forRoomExceedingCapacity(roomId, proposedCapacity);
    }
}
