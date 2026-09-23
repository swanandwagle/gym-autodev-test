package com.studio.booking.shared.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CI drift check for the committed OpenAPI specification (GYM-24).
 *
 * The committed spec is at docs/openapi.yaml. This test boots the application (with H2, no
 * Flyway), fetches the live spec from /v3/api-docs.yaml, and compares it structurally
 * against the committed file.
 *
 * <h3>Regenerating the committed spec</h3>
 * Run with system property {@code -Dopenapi.spec.regenerate=true} to update
 * docs/openapi.yaml automatically:
 * <pre>
 *   mvn test -Dtest=OpenApiSpecDriftTest -Dopenapi.spec.regenerate=true
 * </pre>
 *
 * <h3>AC coverage</h3>
 * <ul>
 *   <li>AC-1: Spec generates and is committed — {@link #testAc1_specGeneratesAndCommittedFileExists()}</li>
 *   <li>AC-2: Double generation produces byte-identical output — {@link #testAc2_doubleGenerationProducesByteIdenticalOutput()}</li>
 *   <li>AC-3 + AC-4: Drift check fails on change, passes after regen — {@link #testAc3Ac4_committedSpecMatchesGenerated()}</li>
 * </ul>
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
class OpenApiSpecDriftTest {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Autowired
    private MockMvc mockMvc;

    /**
     * AC-1: The spec generates successfully and the committed file exists.
     * When run with -Dopenapi.spec.regenerate=true, updates docs/openapi.yaml.
     */
    @Test
    void testAc1_specGeneratesAndCommittedFileExists() throws Exception {
        String generated = fetchGeneratedSpec();
        assertThat(generated).isNotBlank();

        boolean shouldRegenerate = Boolean.parseBoolean(
                System.getProperty("openapi.spec.regenerate", "false"));

        Path specPath = resolveSpecPath();
        if (shouldRegenerate) {
            Files.createDirectories(specPath.getParent());
            Files.writeString(specPath, generated, StandardCharsets.UTF_8);
        }

        assertThat(Files.exists(specPath))
                .as("docs/openapi.yaml must exist as a committed file at: " + specPath.toAbsolutePath())
                .isTrue();
    }

    /**
     * AC-2: Running generation twice without code changes produces byte-identical output.
     * Verified by calling the spec endpoint twice and comparing the results.
     */
    @Test
    void testAc2_doubleGenerationProducesByteIdenticalOutput() throws Exception {
        String firstGeneration = fetchGeneratedSpec();
        String secondGeneration = fetchGeneratedSpec();

        assertThat(secondGeneration)
                .as("Two consecutive spec generations must be byte-identical (determinism check)")
                .isEqualTo(firstGeneration);
    }

    /**
     * AC-3 + AC-4: The committed spec must match the live-generated spec (structurally).
     *
     * Both specs are parsed as YAML and compared as JSON trees so that whitespace and
     * comment differences don't cause false failures while semantic drift is still caught.
     *
     * <ul>
     *   <li>AC-3: When a controller is changed without regenerating, this test fails and
     *       shows exactly what differs.</li>
     *   <li>AC-4: After regenerating docs/openapi.yaml, this test passes.</li>
     * </ul>
     *
     * To fix a drift failure, run:
     * <pre>
     *   mvn test -Dtest=OpenApiSpecDriftTest -Dopenapi.spec.regenerate=true
     * </pre>
     */
    @Test
    void testAc3Ac4_committedSpecMatchesGenerated() throws Exception {
        String generated = fetchGeneratedSpec();
        JsonNode generatedTree = YAML_MAPPER.readTree(generated);

        String committed = readCommittedSpec();
        JsonNode committedTree = YAML_MAPPER.readTree(committed);

        if (!generatedTree.equals(committedTree)) {
            ObjectMapper prettyJson = new ObjectMapper();
            String generatedJson = prettyJson.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(generatedTree);
            String committedJson = prettyJson.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(committedTree);
            String diff = buildDiffMessage(committedJson, generatedJson);

            throw new AssertionError(
                    "OpenAPI specification has drifted from docs/openapi.yaml.\n"
                    + "To fix: run mvn test -Dtest=OpenApiSpecDriftTest -Dopenapi.spec.regenerate=true\n\n"
                    + diff);
        }
    }

    // -------------------------------------------------------------------------

    private String fetchGeneratedSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String readCommittedSpec() throws IOException {
        Path specPath = resolveSpecPath();
        if (!Files.exists(specPath)) {
            throw new AssertionError(
                    "Committed OpenAPI spec not found at: " + specPath.toAbsolutePath()
                    + "\nGenerate it by running: mvn test "
                    + "-Dtest=OpenApiSpecDriftTest -Dopenapi.spec.regenerate=true");
        }
        return Files.readString(specPath, StandardCharsets.UTF_8);
    }

    private Path resolveSpecPath() {
        Path[] candidates = {
                Paths.get("docs/openapi.yaml"),
                Paths.get("../docs/openapi.yaml"),
                Paths.get(System.getProperty("user.dir"), "docs/openapi.yaml")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return Paths.get(System.getProperty("user.dir"), "docs/openapi.yaml");
    }

    private String buildDiffMessage(String expected, String actual) {
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);

        StringBuilder diff = new StringBuilder();
        diff.append("--- docs/openapi.yaml (committed)\n");
        diff.append("+++ generated (live)\n\n");

        int maxLines = Math.max(expectedLines.length, actualLines.length);
        int changesShown = 0;
        for (int i = 0; i < maxLines && changesShown < 30; i++) {
            String exp = i < expectedLines.length ? expectedLines[i] : "<missing>";
            String act = i < actualLines.length ? actualLines[i] : "<missing>";
            if (!exp.equals(act)) {
                diff.append(String.format("Line %d:%n  - %s%n  + %s%n", i + 1, exp, act));
                changesShown++;
            }
        }
        if (changesShown == 30) {
            diff.append("... (truncated; first 30 differing lines shown)\n");
        }
        return diff.toString();
    }
}
