package com.studio.booking.shared.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CI coverage enforcement test (GYM-19 AC-4).
 *
 * This test fails the build when a new ErrorCode constant is added without a corresponding
 * test that references it. "Referenced" means the code's name appears as a literal in the
 * test source tree under src/test.
 *
 * This is the containment check: it is impossible to ship a new code without a test.
 */
class ErrorCodeCoverageTest {

    @Test
    void testAc4EachCodeHasDedicatedTest() throws IOException {
        Path testRoot = resolveTestRoot();
        String testSources = collectTestSources(testRoot);

        List<ErrorCode> uncovered = Arrays.stream(ErrorCode.values())
                .filter(code -> !testSources.contains(code.name()))
                .collect(Collectors.toList());

        assertThat(uncovered)
                .as("""
                        The following ErrorCode constants have no reference in the test source tree.
                        Add at least one test that names each constant (e.g. ErrorCode.%s or "%s").
                        This check is the CI gate: every new code MUST have a test before merge.
                        """,
                        uncovered.isEmpty() ? "" : uncovered.get(0).name(),
                        uncovered.isEmpty() ? "" : uncovered.get(0).name())
                .isEmpty();
    }

    // -------------------------------------------------------------------------

    private Path resolveTestRoot() {
        // Try several candidate locations so the test works from both the project root
        // and from inside an IDE with a different working directory.
        Path[] candidates = {
                Paths.get("src/test/java"),
                Paths.get("../src/test/java"),
                Paths.get(System.getProperty("user.dir"), "src/test/java")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Cannot locate src/test/java from working directory: " + System.getProperty("user.dir"));
    }

    private String collectTestSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            return "";
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}
