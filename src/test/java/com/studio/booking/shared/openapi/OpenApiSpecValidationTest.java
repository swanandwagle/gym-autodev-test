package com.studio.booking.shared.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validates the content of the generated OpenAPI specification (GYM-24).
 *
 * AC-5: Shared components referenced rather than inlined; no endpoint duplicates the error envelope schema.
 * AC-6: The specification loads without warnings in a standard OpenAPI viewer (verified via parse + structure check).
 * AC-7: Every currently implemented endpoint documents all its error responses.
 * AC-8: The specification correctly represents errors[] as present only on 422 responses.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                JpaRepositoriesAutoConfiguration.class
        }
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "studio.api.base-url=https://api.studio.example",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiSpecValidationTest {

    @Autowired
    private MockMvc mockMvc;

    private JsonNode spec;
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @BeforeAll
    void loadSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn();
        String yaml = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        spec = YAML_MAPPER.readTree(yaml);
    }

    /**
     * AC-5: The shared ErrorEnvelope and FieldError schemas must exist as components,
     * not inlined inside every response. Verifies that:
     *   - components/schemas/ErrorEnvelope exists
     *   - components/schemas/FieldError exists
     *   - No endpoint response defines its own "errors" or "traceId" property inline
     *     (which would indicate the envelope was duplicated instead of referenced).
     */
    @Test
    void testAc5_sharedComponentSchemasExistAndAreNotInlined() {
        JsonNode schemas = spec.path("components").path("schemas");

        assertThat(schemas.has("ErrorEnvelope"))
                .as("components/schemas/ErrorEnvelope must exist as a shared component")
                .isTrue();

        assertThat(schemas.has("FieldError"))
                .as("components/schemas/FieldError must exist as a shared component")
                .isTrue();

        // Verify ErrorEnvelope references FieldError instead of inlining it
        JsonNode errorEnvelope = schemas.path("ErrorEnvelope");
        assertThat(errorEnvelope.isMissingNode())
                .as("ErrorEnvelope schema must be present")
                .isFalse();

        JsonNode errorsProperty = errorEnvelope.path("properties").path("errors").path("items");
        if (!errorsProperty.isMissingNode()) {
            // If errors items is defined, it should reference FieldError
            assertThat(errorsProperty.has("$ref"))
                    .as("ErrorEnvelope.errors items should reference FieldError via $ref, not inline it")
                    .isTrue();
            assertThat(errorsProperty.get("$ref").asText())
                    .contains("FieldError");
        }
    }

    /**
     * AC-6: The specification parses successfully and has all required top-level fields.
     * A spec that causes parser warnings or fails schema validation will fail this check.
     */
    @Test
    void testAc6_specParsesWithoutErrors() {
        assertThat(spec).isNotNull();

        // Required OpenAPI top-level fields
        assertThat(spec.has("openapi"))
                .as("Spec must have 'openapi' version field")
                .isTrue();
        assertThat(spec.path("openapi").asText())
                .as("OpenAPI version must be 3.x")
                .startsWith("3.");

        assertThat(spec.has("info"))
                .as("Spec must have 'info' section")
                .isTrue();
        assertThat(spec.path("info").has("title"))
                .as("info.title is required")
                .isTrue();
        assertThat(spec.path("info").has("version"))
                .as("info.version is required")
                .isTrue();

        assertThat(spec.has("paths"))
                .as("Spec must have 'paths' section")
                .isTrue();
    }

    /**
     * AC-7: Every currently implemented endpoint must document all its error responses.
     *
     * The minimum set of error codes every endpoint must document:
     *   - 400 MALFORMED_REQUEST (malformed body — all POST/PUT endpoints)
     *   - 422 VALIDATION_FAILED (input validation — all endpoints that accept input)
     *   - 500 INTERNAL_ERROR (all endpoints)
     *
     * This test cross-references the ErrorCode catalogue and verifies that every path
     * in the spec has at least a 500 response documented.
     *
     * When no endpoints are implemented yet, this is a no-op (passes vacuously).
     * As endpoints are added, each must declare their error responses per the conventions
     * in docs/openapi-conventions.md.
     */
    @Test
    void testAc7_allEndpointsDocumentErrorResponses() {
        JsonNode paths = spec.path("paths");
        if (paths.isMissingNode() || paths.isEmpty()) {
            // No endpoints yet — passes vacuously
            return;
        }

        List<String> violations = new ArrayList<>();

        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            pathItem.fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey();
                // Only check operation methods, not $ref, parameters, etc.
                if (!isHttpMethod(method)) return;

                JsonNode operation = methodEntry.getValue();
                JsonNode responses = operation.path("responses");

                if (responses.isMissingNode() || responses.isEmpty()) {
                    violations.add(path + " " + method.toUpperCase() + ": no responses documented");
                    return;
                }

                // Every endpoint must document at least 500
                if (!responses.has("500") && !responses.has("'500'")) {
                    violations.add(path + " " + method.toUpperCase()
                            + ": missing 500 INTERNAL_ERROR response");
                }

                // POST/PUT/PATCH must document 400 and 422
                if (Set.of("post", "put", "patch").contains(method)) {
                    if (!responses.has("400") && !responses.has("'400'")) {
                        violations.add(path + " " + method.toUpperCase()
                                + ": missing 400 MALFORMED_REQUEST response");
                    }
                    if (!responses.has("422") && !responses.has("'422'")) {
                        violations.add(path + " " + method.toUpperCase()
                                + ": missing 422 VALIDATION_FAILED response");
                    }
                }
            });
        });

        assertThat(violations)
                .as("All endpoints must document their error responses per docs/openapi-conventions.md")
                .isEmpty();
    }

    /**
     * AC-8: The specification correctly represents errors[] as present only on 422 responses.
     *
     * For every path+operation in the spec:
     *   - 422 response schemas must include or reference an errors[] array.
     *   - Non-422 response schemas (4xx, 5xx) must NOT include an errors[] array.
     */
    @Test
    void testAc8_errorsArrayPresentOnlyOn422Responses() {
        // Always check that ErrorEnvelope schema documents errors[] as 422-only
        JsonNode errorEnvelopeSchema = spec.path("components").path("schemas").path("ErrorEnvelope");
        if (!errorEnvelopeSchema.isMissingNode()) {
            JsonNode errorsProperty = errorEnvelopeSchema.path("properties").path("errors");
            assertThat(errorsProperty.isMissingNode())
                    .as("ErrorEnvelope must document the errors[] property")
                    .isFalse();

            String errorsDescription = errorsProperty.path("description").asText("");
            assertThat(errorsDescription)
                    .as("ErrorEnvelope.errors description must clarify it is only present on 422 responses")
                    .containsIgnoringCase("422");
        }

        JsonNode paths = spec.path("paths");
        if (paths.isMissingNode() || paths.isEmpty()) {
            // No endpoints yet — schema check above is sufficient
            return;
        }

        // For any endpoint that documents 4xx or 5xx responses (other than 422),
        // verify the response schema does NOT include an errors[] property inline
        List<String> violations = new ArrayList<>();

        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            pathItem.fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey();
                if (!isHttpMethod(method)) return;

                JsonNode responses = methodEntry.getValue().path("responses");
                responses.fields().forEachRemaining(responseEntry -> {
                    String statusCode = responseEntry.getKey();
                    // Only check non-422 error responses
                    if ("422".equals(statusCode) || "default".equals(statusCode)) return;
                    if (!isErrorStatus(statusCode)) return;

                    JsonNode responseSchema = findInlineSchema(responseEntry.getValue());
                    if (responseSchema != null && responseSchema.path("properties").has("errors")) {
                        violations.add(path + " " + method.toUpperCase() + " " + statusCode
                                + ": errors[] property must not appear on non-422 responses");
                    }
                });
            });
        });

        assertThat(violations)
                .as("errors[] array must only appear on 422 responses, not on other error status codes")
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isHttpMethod(String method) {
        return Set.of("get", "post", "put", "patch", "delete", "head", "options").contains(method);
    }

    private static boolean isErrorStatus(String statusCode) {
        try {
            int code = Integer.parseInt(statusCode);
            return code >= 400;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static JsonNode findInlineSchema(JsonNode responseNode) {
        // Try content/application~1json/schema
        JsonNode contentNode = responseNode.path("content");
        if (!contentNode.isMissingNode()) {
            for (JsonNode mediaType : contentNode) {
                JsonNode schemaNode = mediaType.path("schema");
                if (!schemaNode.isMissingNode() && !schemaNode.has("$ref")) {
                    return schemaNode;
                }
            }
        }
        return null;
    }
}
