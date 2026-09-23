package com.studio.booking.catalog.application;

import com.studio.booking.catalog.api.request.CreateInstructorRequest;
import com.studio.booking.catalog.api.request.PatchInstructorRequest;
import com.studio.booking.catalog.api.response.InstructorResponse;
import com.studio.booking.catalog.domain.Instructor;
import com.studio.booking.catalog.infrastructure.InstructorRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InstructorService {

    private final InstructorRepository repository;
    private final InstructorDeactivationGuard deactivationGuard;

    public InstructorService(InstructorRepository repository, InstructorDeactivationGuard deactivationGuard) {
        this.repository = repository;
        this.deactivationGuard = deactivationGuard;
    }

    @Transactional
    public InstructorResponse create(CreateInstructorRequest request) {
        repository.findByEmailCaseInsensitive(request.email()).ifPresent(existing -> {
            throw new ApiException(ErrorCode.INSTRUCTOR_EMAIL_ALREADY_EXISTS,
                    "An instructor with this email already exists");
        });

        Instructor instructor = new Instructor(
                request.email(),
                request.name(),
                request.bio(),
                request.specialties()
        );
        Instructor saved = repository.save(instructor);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InstructorResponse getById(UUID id) {
        Instructor instructor = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND,
                        "Instructor not found"));
        return toResponse(instructor);
    }

    @Transactional(readOnly = true)
    public PageResponse<InstructorResponse> list(Pageable pageable, boolean includeInactive, String q, String specialty) {
        Page<Instructor> page;

        if (q != null && !q.isBlank()) {
            page = repository.searchByNameOrEmail(q, pageable);
        } else if (specialty != null && !specialty.isBlank()) {
            page = repository.findBySpecialty(specialty, pageable);
        } else if (includeInactive) {
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
    public InstructorResponse update(UUID id, PatchInstructorRequest request) {
        if (!request.hasAnyUpdate() && request.getVersion().isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Request body must contain at least one field to update");
        }

        Instructor instructor = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND,
                        "Instructor not found"));

        if (request.getVersion().isPresent()) {
            long requestVersion = request.getVersion().get();
            if (requestVersion != instructor.getVersion()) {
                throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                        "Version mismatch");
            }
        }

        if (request.getEmail().isPresent()) {
            String newEmail = request.getEmail().get();
            repository.findByEmailCaseInsensitive(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(instructor.getId())) {
                    throw new ApiException(ErrorCode.INSTRUCTOR_EMAIL_ALREADY_EXISTS,
                            "An instructor with this email already exists");
                }
            });
            instructor.setEmail(newEmail);
        }

        if (request.getName().isPresent()) {
            instructor.setName(request.getName().get());
        }

        if (request.getBio().isPresent()) {
            instructor.setBio(request.getBio().get());
        }

        if (request.getSpecialties().isPresent()) {
            instructor.setSpecialties(request.getSpecialties().get());
        }

        Instructor updated = repository.save(instructor);
        return toResponse(updated);
    }

    @Transactional
    public InstructorResponse deactivate(UUID id) {
        Instructor instructor = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.INSTRUCTOR_NOT_FOUND,
                        "Instructor not found"));

        if (!instructor.isActive()) {
            throw new ApiException(ErrorCode.INSTRUCTOR_INACTIVE,
                    "Instructor is already inactive");
        }

        deactivationGuard.checkCanDeactivate(id);
        instructor.deactivate();
        Instructor updated = repository.save(instructor);
        return toResponse(updated);
    }

    private InstructorResponse toResponse(Instructor instructor) {
        return new InstructorResponse(
                instructor.getId(),
                instructor.getEmail(),
                instructor.getName(),
                instructor.getBio(),
                instructor.isActive(),
                instructor.getSpecialties(),
                instructor.getVersion(),
                instructor.getCreatedAt(),
                instructor.getUpdatedAt()
        );
    }
}
