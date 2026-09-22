package com.studio.booking.shared.error;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates the canonical error-catalogue document from the live {@link ErrorCode} enum.
 *
 * The output is a Markdown table grouped by HTTP status. Committed to {@code docs/error-catalogue.md}.
 * The CI test {@code ErrorCataloguePrinterTest} fails if the committed file drifts from what
 * this class would generate for the current enum.
 */
public final class ErrorCataloguePrinter {

    private ErrorCataloguePrinter() {}

    /** Generates and returns the full catalogue as a Markdown string. */
    public static String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Error Code Catalogue\n\n");
        sb.append("Generated from `ErrorCode` enum. Do not edit manually — ");
        sb.append("regenerate with `ErrorCataloguePrinter.generate()`.\n\n");

        Map<Integer, List<ErrorCode>> byStatus = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(
                        c -> c.httpStatus().value(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<Integer, List<ErrorCode>> entry : byStatus.entrySet()) {
            int status = entry.getKey();
            sb.append("## HTTP ").append(status).append("\n\n");
            sb.append("| Code | Kebab slug | Default message |\n");
            sb.append("|------|------------|----------------|\n");
            for (ErrorCode code : entry.getValue()) {
                String msg = ErrorMessages.forCode(code).replace("|", "\\|");
                sb.append("| `").append(code.name()).append("` ");
                sb.append("| `").append(code.toKebab()).append("` ");
                sb.append("| ").append(msg).append(" |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /** Entry point for manual catalogue regeneration. */
    public static void main(String[] args) throws Exception {
        String content = generate();
        java.nio.file.Path out = java.nio.file.Paths.get("docs/error-catalogue.md");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, content);
        System.out.println("Wrote " + out.toAbsolutePath());
    }
}
