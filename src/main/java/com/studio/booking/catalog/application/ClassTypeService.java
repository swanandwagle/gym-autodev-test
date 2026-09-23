package com.studio.booking.catalog.application;

import com.studio.booking.catalog.api.request.CreateClassTypeRequest;
import com.studio.booking.catalog.api.request.PatchClassTypeRequest;
import com.studio.booking.catalog.api.response.ClassTypeResponse;
import com.studio.booking.catalog.domain.ClassType;
import com.studio.booking.catalog.infrastructure.ClassTypeRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ClassTypeService {

    private final ClassTypeRepository repository;

    public ClassTypeService(ClassTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ClassTypeResponse create(CreateClassTypeRequest request) {
        repository.findByNameCaseInsensitive(request.name()).ifPresent(existing -> {
            throw new ApiException(ErrorCode.CLASS_TYPE_NAME_ALREADY_EXISTS,
                    "A class type with this name already exists");
        });

        ClassType classType = new ClassType(
                request.name(),
                request.description(),
                request.durationMinutes(),
                request.defaultCapacity()
        );
        ClassType saved = repository.save(classType);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ClassTypeResponse getById(UUID id) {
        ClassType classType = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CLASS_TYPE_NOT_FOUND,
                        "Class type not found"));
        return toResponse(classType);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassTypeResponse> list(Pageable pageable, boolean includeInactive) {
        Page<ClassType> page;
        if (includeInactive) {
            page = repository.findAll(pageable);
        } else {
            page = repository.findAllActive(pageable);
        }
        return PageResponse.of(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    @Transactional
    public ClassTypeResponse update(UUID id, PatchClassTypeRequest request) {
        if (!request.hasAnyUpdate() && request.getVersion().isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Request body must contain at least one field to update");
        }

        ClassType classType = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CLASS_TYPE_NOT_FOUND,
                        "Class type not found"));

        if (request.getVersion().isPresent()) {
            long requestVersion = request.getVersion().get();
            if (requestVersion != classType.getVersion()) {
                throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                        "Version mismatch");
            }
        }

        if (request.getName().isPresent()) {
            String newName = request.getName().get();
            repository.findByNameCaseInsensitive(newName).ifPresent(existing -> {
                if (!existing.getId().equals(classType.getId())) {
                    throw new ApiException(ErrorCode.CLASS_TYPE_NAME_ALREADY_EXISTS,
                            "A class type with this name already exists");
                }
            });
            classType.setName(newName);
        }

        if (request.getDescription().isPresent()) {
            classType.setDescription(request.getDescription().get());
        }

        if (request.getDurationMinutes().isPresent()) {
            int durationMinutes = request.getDurationMinutes().get();
            if (durationMinutes < 5 || durationMinutes > 480) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Request validation failed. See errors.");
            }
            classType.setDurationMinutes(durationMinutes);
        }

        if (request.getDefaultCapacity().isPresent()) {
            int defaultCapacity = request.getDefaultCapacity().get();
            if (defaultCapacity < 1 || defaultCapacity > 500) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Request validation failed. See errors.");
            }
            classType.setDefaultCapacity(defaultCapacity);
        }

        ClassType updated = repository.save(classType);
        return toResponse(updated);
    }

    @Transactional
    public ClassTypeResponse deactivate(UUID id) {
        ClassType classType = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CLASS_TYPE_NOT_FOUND,
                        "Class type not found"));

        if (!classType.isActive()) {
            throw new ApiException(ErrorCode.CLASS_TYPE_INACTIVE,
                    "Class type is already inactive");
        }

        classType.deactivate();
        ClassType updated = repository.save(classType);
        return toResponse(updated);
    }

    private ClassTypeResponse toResponse(ClassType classType) {
        return new ClassTypeResponse(
                classType.getId(),
                classType.getName(),
                classType.getDescription(),
                classType.getDurationMinutes(),
                classType.getDefaultCapacity(),
                classType.isActive(),
                classType.getVersion(),
                classType.getCreatedAt(),
                classType.getUpdatedAt()
        );
    }
}
