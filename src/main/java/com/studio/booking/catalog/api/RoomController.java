package com.studio.booking.catalog.api;

import com.studio.booking.catalog.api.request.CreateRoomRequest;
import com.studio.booking.catalog.api.request.PatchRoomRequest;
import com.studio.booking.catalog.api.response.RoomResponse;
import com.studio.booking.catalog.application.RoomService;
import com.studio.booking.shared.validation.ValidUuid;
import com.studio.booking.shared.web.PageResponse;
import com.studio.booking.shared.web.SortablePageParams;
import com.studio.booking.shared.web.PageParamsValidator;
import com.studio.booking.shared.web.SortValidator;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService service;
    private final PageParamsValidator pageValidator;
    private final SortValidator sortValidator;

    public RoomController(RoomService service,
                        PageParamsValidator pageValidator,
                        SortValidator sortValidator) {
        this.service = service;
        this.pageValidator = pageValidator;
        this.sortValidator = sortValidator;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/rooms/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getById(@PathVariable @ValidUuid String id) {
        RoomResponse response = service.getById(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<RoomResponse>> list(SortablePageParams params,
                                                          @RequestParam(defaultValue = "false") boolean includeInactive,
                                                          @RequestParam(required = false) Integer minCapacity) {
        pageValidator.validate(params);
        sortValidator.validate(params, new String[]{"createdAt", "name", "capacity"});

        Pageable pageable = PageRequest.of(params.getPage(), params.getSize(),
                params.getSort() != null && !params.getSort().isBlank()
                    ? org.springframework.data.domain.Sort.by(
                        "desc".equalsIgnoreCase(params.sortDirection())
                            ? org.springframework.data.domain.Sort.Order.desc(params.sortField())
                            : org.springframework.data.domain.Sort.Order.asc(params.sortField()))
                    : org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt")));

        PageResponse<RoomResponse> response = service.list(pageable, includeInactive, minCapacity);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RoomResponse> update(
            @PathVariable @ValidUuid String id,
            @RequestBody(required = false) PatchRoomRequest request) {
        if (request == null) {
            request = new PatchRoomRequest(null, null);
        }
        RoomResponse response = service.update(UUID.fromString(id), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<RoomResponse> deactivate(@PathVariable @ValidUuid String id) {
        RoomResponse response = service.deactivate(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }
}
