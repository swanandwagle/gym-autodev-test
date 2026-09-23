package com.studio.booking.catalog.api;

import com.studio.booking.catalog.api.request.CreateInstructorRequest;
import com.studio.booking.catalog.api.request.PatchInstructorRequest;
import com.studio.booking.catalog.api.response.InstructorResponse;
import com.studio.booking.catalog.application.InstructorService;
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
@RequestMapping("/api/v1/instructors")
public class InstructorController {

    private final InstructorService service;
    private final PageParamsValidator pageValidator;
    private final SortValidator sortValidator;

    public InstructorController(InstructorService service,
                               PageParamsValidator pageValidator,
                               SortValidator sortValidator) {
        this.service = service;
        this.pageValidator = pageValidator;
        this.sortValidator = sortValidator;
    }

    @PostMapping
    public ResponseEntity<InstructorResponse> create(@Valid @RequestBody CreateInstructorRequest request) {
        InstructorResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/instructors/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstructorResponse> getById(@PathVariable @ValidUuid String id) {
        InstructorResponse response = service.getById(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<InstructorResponse>> list(SortablePageParams params,
                                                                 @RequestParam(defaultValue = "false") boolean includeInactive,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(required = false) String specialty) {
        pageValidator.validate(params);
        sortValidator.validate(params, new String[]{"createdAt", "name", "email"});

        Pageable pageable = PageRequest.of(params.getPage(), params.getSize(),
                params.getSort() != null && !params.getSort().isBlank()
                    ? org.springframework.data.domain.Sort.by(
                        "desc".equalsIgnoreCase(params.sortDirection())
                            ? org.springframework.data.domain.Sort.Order.desc(params.sortField())
                            : org.springframework.data.domain.Sort.Order.asc(params.sortField()))
                    : org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt")));

        PageResponse<InstructorResponse> response = service.list(pageable, includeInactive, q, specialty);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InstructorResponse> update(
            @PathVariable @ValidUuid String id,
            @RequestBody(required = false) PatchInstructorRequest request) {
        if (request == null) {
            request = new PatchInstructorRequest();
        }
        InstructorResponse response = service.update(UUID.fromString(id), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<InstructorResponse> deactivate(@PathVariable @ValidUuid String id) {
        InstructorResponse response = service.deactivate(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }
}
