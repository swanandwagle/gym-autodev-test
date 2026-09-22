package com.studio.booking.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.TreeMap;

/**
 * Configures springdoc-openapi for the Fitness Class Booking System.
 *
 * Key concerns:
 *  - Registers shared component schemas so endpoints reference them rather than inlining.
 *  - Ensures errors[] is documented as present only on 422 responses (enforced by convention;
 *    see docs/openapi-conventions.md).
 *  - Sorts operations and schemas for deterministic YAML output (AC-2).
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Fitness Class Booking System",
                version = "1.0",
                description = "REST API for managing gym class sessions, memberships, bookings and waitlists."
        ),
        servers = @Server(url = "/", description = "Default server")
)
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        Schema<Object> fieldErrorSchema = new Schema<>()
                .type("object")
                .description("Per-field validation error detail (present only on 422 responses)")
                .addProperty("field", new StringSchema().description("The field that failed validation"))
                .addProperty("code", new StringSchema().description("Machine-readable error code for the field"))
                .addProperty("message", new StringSchema().description("Human-readable description of the failure"))
                .addProperty("rejectedValue", new Schema<>().description("The value that was rejected (omitted when null)"));

        Schema<Object> errorEnvelopeSchema = new Schema<>()
                .type("object")
                .description("RFC 9457 Problem Details extended with code, traceId, and errors. "
                        + "The errors[] array is present ONLY on 422 responses.")
                .addProperty("type", new StringSchema()
                        .description("URI that identifies the error type")
                        .example("https://api.studio.example/errors/validation-failed"))
                .addProperty("title", new StringSchema()
                        .description("Human-readable summary of the error type")
                        .example("Validation Failed"))
                .addProperty("status", new Schema<Integer>().type("integer")
                        .description("HTTP status code")
                        .example(422))
                .addProperty("code", new StringSchema()
                        .description("Machine-readable error code from the catalogue")
                        .example("VALIDATION_FAILED"))
                .addProperty("detail", new StringSchema()
                        .description("Human-readable explanation of this specific occurrence")
                        .example("Request validation failed. See errors."))
                .addProperty("instance", new StringSchema()
                        .description("The request path that triggered the error")
                        .example("/api/v1/members"))
                .addProperty("timestamp", new StringSchema()
                        .type("string")
                        .format("date-time")
                        .description("UTC timestamp of when the error occurred"))
                .addProperty("traceId", new StringSchema()
                        .description("Correlation ID — echoed from X-Request-Id or generated"))
                .addProperty("errors", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/FieldError"))
                        .description("Per-field validation errors. Present ONLY on 422 responses."));

        return new OpenAPI()
                .components(new Components()
                        .addSchemas("FieldError", fieldErrorSchema)
                        .addSchemas("ErrorEnvelope", errorEnvelopeSchema));
    }

    /**
     * Ensures that schema and path keys are sorted alphabetically so that
     * repeated generation produces byte-identical output regardless of JVM
     * map iteration order.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public OpenApiCustomizer sortedOutputCustomizer() {
        return openApi -> {
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                Map<String, Schema> sorted = new TreeMap<>(openApi.getComponents().getSchemas());
                openApi.getComponents().setSchemas(sorted);
            }
            if (openApi.getPaths() != null) {
                Paths sortedPaths = new Paths();
                new TreeMap<>(openApi.getPaths()).forEach(sortedPaths::addPathItem);
                openApi.setPaths(sortedPaths);
            }
        };
    }
}
