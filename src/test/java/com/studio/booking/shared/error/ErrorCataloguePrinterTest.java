package com.studio.booking.shared.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the committed docs/error-catalogue.md matches what ErrorCataloguePrinter
 * would generate for the current ErrorCode enum (GYM-19 AC-7).
 *
 * This test fails the CI build when:
 *   - A code is added to ErrorCode but docs/error-catalogue.md is not regenerated, or
 *   - docs/error-catalogue.md is edited manually.
 */
class ErrorCataloguePrinterTest {

    @Test
    void testAc7GeneratedCatalogueMatchesEnum() throws IOException {
        String expected = ErrorCataloguePrinter.generate();
        Path cataloguePath = resolveCataloguePath();

        assertThat(cataloguePath)
                .as("docs/error-catalogue.md must exist")
                .exists();

        // Normalise line endings so the test passes on both Windows and Unix CI runners.
        String committed = Files.readString(cataloguePath).replace("\r\n", "\n");
        String normalised = expected.replace("\r\n", "\n");

        assertThat(committed)
                .as("""
                        docs/error-catalogue.md is out of sync with the ErrorCode enum.
                        Regenerate it by running: ErrorCataloguePrinter.main(new String[0])
                        or: mvn exec:java -Dexec.mainClass=com.studio.booking.shared.error.ErrorCataloguePrinter
                        """)
                .isEqualTo(normalised);
    }

    @Test
    void testAc7GeneratedContentContainsAllCodes() {
        String generated = ErrorCataloguePrinter.generate();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(generated)
                    .as("Generated catalogue must contain code %s", code.name())
                    .contains(code.name());
            assertThat(generated)
                    .as("Generated catalogue must contain kebab slug for %s", code.name())
                    .contains(code.toKebab());
        }
    }

    // -------------------------------------------------------------------------

    private Path resolveCataloguePath() {
        Path[] candidates = {
                Paths.get("docs/error-catalogue.md"),
                Paths.get("../docs/error-catalogue.md"),
                Paths.get(System.getProperty("user.dir"), "docs/error-catalogue.md")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Cannot locate docs/error-catalogue.md from working directory: "
                + System.getProperty("user.dir"));
    }
}
