package com.studio.booking.catalog.api;

import com.studio.booking.catalog.api.request.CreateClassTypeRequest;
import com.studio.booking.catalog.api.request.PatchClassTypeRequest;
import com.studio.booking.catalog.api.response.ClassTypeResponse;
import com.studio.booking.catalog.application.ClassTypeService;
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
@RequestMapping("/api/v1/class-types")
public class ClassTypeController {

    private final ClassTypeService service;
    private final PageParamsValidator pageValidator;
    private final SortValidator sortValidator;

    public ClassTypeController(ClassTypeService service,
                             PageParamsValidator pageValidator,
                             SortValidator sortValidator) {
        this.service = service;
        this.pageValidator = pageValidator;
        this.sortValidator = sortValidator;
    }

    @PostMapping
    public ResponseEntity<ClassTypeResponse> create(@Valid @RequestBody CreateClassTypeRequest request) {
        ClassTypeResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/class-types/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassTypeResponse> getById(@PathVariable @ValidUuid String id) {
        ClassTypeResponse response = service.getById(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClassTypeResponse>> list(SortablePageParams params,
                                                                @RequestParam(defaultValue = "false") boolean includeInactive) {
        pageValidator.validate(params);
        sortValidator.validate(params, new String[]{"createdAt", "name"});

        Pageable pageable = PageRequest.of(params.getPage(), params.getSize(),
                params.getSort() != null && !params.getSort().isBlank()
                    ? org.springframework.data.domain.Sort.by(
                        "desc".equalsIgnoreCase(params.sortDirection())
                            ? org.springframework.data.domain.Sort.Order.desc(params.sortField())
                            : org.springframework.data.domain.Sort.Order.asc(params.sortField()))
                    : org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt")));
        PageResponse<ClassTypeResponse> response = service.list(pageable, includeInactive);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ClassTypeResponse> update(
            @PathVariable @ValidUuid String id,
            @RequestBody(required = false) PatchClassTypeRequest request) {
        if (request == null) {
            request = new PatchClassTypeRequest();
        }
        ClassTypeResponse response = service.update(UUID.fromString(id), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ClassTypeResponse> deactivate(@PathVariable @ValidUuid String id) {
        ClassTypeResponse response = service.deactivate(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }
}
