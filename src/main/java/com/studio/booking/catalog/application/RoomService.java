package com.studio.booking.catalog.application;

import com.studio.booking.catalog.api.request.CreateRoomRequest;
import com.studio.booking.catalog.api.request.PatchRoomRequest;
import com.studio.booking.catalog.api.response.RoomResponse;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.domain.Room;
import com.studio.booking.catalog.infrastructure.RoomRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository repository;
    private final RoomDeactivationGuard deactivationGuard;
    private final Clock clock;

    public RoomService(RoomRepository repository, RoomDeactivationGuard deactivationGuard, Clock clock) {
        this.repository = repository;
        this.deactivationGuard = deactivationGuard;
        this.clock = clock;
    }

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        repository.findByNameCaseInsensitive(request.name()).ifPresent(existing -> {
            throw new ApiException(ErrorCode.ROOM_NAME_ALREADY_EXISTS,
                    "A room with this name already exists");
        });

        Room room = new Room(request.name(), request.capacity());
        Room saved = repository.save(room);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RoomResponse getById(UUID id) {
        Room room = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND,
                        "Room not found"));
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> list(Pageable pageable, boolean includeInactive, Integer minCapacity) {
        Page<Room> page;

        if (includeInactive) {
            if (minCapacity != null) {
                page = repository.findAllWithMinCapacity(minCapacity, pageable);
            } else {
                page = repository.findAllWithoutFilters(pageable);
            }
        } else {
            if (minCapacity != null) {
                page = repository.findAllActiveWithMinCapacity(minCapacity, pageable);
            } else {
                page = repository.findAllActive(pageable);
            }
        }

        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    @Transactional
    public RoomResponse update(UUID id, PatchRoomRequest request) {
        if (!request.hasAnyUpdate() && request.getVersion().isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Request body must contain at least one field to update");
        }

        Room room = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND,
                        "Room not found"));

        if (request.getVersion().isPresent()) {
            long requestVersion = request.getVersion().get();
            if (requestVersion != room.getVersion()) {
                throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                        "Version mismatch");
            }
        }

        if (request.getCapacity().isPresent()) {
            int newCapacity = request.getCapacity().get();
            int currentCapacity = room.getCapacity();

            if (newCapacity < currentCapacity) {
                Instant now = clock.instant();
                List<ClassSession> futureSessions = repository.findFutureScheduledSessions(id, now);

                if (!futureSessions.isEmpty()) {
                    ClassSession overCapacity = futureSessions.stream()
                            .filter(session -> session.getCapacity() > newCapacity)
                            .findFirst()
                            .orElse(null);

                    if (overCapacity != null) {
                        throw new ApiException(ErrorCode.ROOM_HAS_FUTURE_SESSIONS,
                                String.format("Cannot reduce capacity to %d; session %s has capacity %d and starts at %s",
                                        newCapacity, overCapacity.getId(), overCapacity.getCapacity(), overCapacity.getStartsAt()));
                    }
                }
            }

            room.setCapacity(newCapacity);
        }

        Room updated = repository.save(room);
        return toResponse(updated);
    }

    @Transactional
    public RoomResponse deactivate(UUID id) {
        Room room = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND,
                        "Room not found"));

        if (!room.isActive()) {
            throw new ApiException(ErrorCode.ROOM_INACTIVE,
                    "Room is already inactive");
        }

        deactivationGuard.checkCanDeactivate(id);
        room.deactivate();
        Room updated = repository.save(room);
        return toResponse(updated);
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.isActive(),
                room.getVersion(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
