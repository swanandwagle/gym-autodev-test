package com.studio.booking.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;
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

    /**
     * Patches the ErrorEnvelope.errors property description so it explicitly
     * names "422". springdoc drops descriptions on ArraySchema items when
     * serialising, so we apply the description as a post-processing step.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public OpenApiCustomizer errorsDescriptionCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            Schema<?> envelope = openApi.getComponents().getSchemas().get("ErrorEnvelope");
            if (envelope == null || envelope.getProperties() == null) return;
            Schema<?> errors = (Schema<?>) envelope.getProperties().get("errors");
            if (errors != null && (errors.getDescription() == null || !errors.getDescription().contains("422"))) {
                errors.setDescription("Per-field validation errors. Present ONLY on 422 responses.");
            }
        };
    }

    /**
     * Injects standard error responses (500, and 400/422 for mutating methods)
     * into every operation that does not already document them.
     */
    @Bean
    public OpenApiCustomizer globalErrorResponsesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            Schema<?> errorRef = new Schema<>().$ref("#/components/schemas/ErrorEnvelope");
            Content errorContent = new Content().addMediaType(
                    "application/json", new MediaType().schema(errorRef));

            ApiResponse response500 = new ApiResponse()
                    .description("Internal server error")
                    .content(errorContent);
            ApiResponse response400 = new ApiResponse()
                    .description("Malformed or unreadable request body")
                    .content(errorContent);
            ApiResponse response422 = new ApiResponse()
                    .description("Input validation failed")
                    .content(errorContent);

            Set<String> mutatingMethods = Set.of("post", "put", "patch");

            openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    if (operation == null) return;
                    ApiResponses responses = operation.getResponses();
                    if (responses == null) {
                        responses = new ApiResponses();
                        operation.setResponses(responses);
                    }
                    if (!responses.containsKey("500")) {
                        responses.addApiResponse("500", response500);
                    }
                    String method = httpMethod.toString().toLowerCase();
                    if (mutatingMethods.contains(method)) {
                        if (!responses.containsKey("400")) {
                            responses.addApiResponse("400", response400);
                        }
                        if (!responses.containsKey("422")) {
                            responses.addApiResponse("422", response422);
                        }
                    }
                })
            );
        };
    }
}
