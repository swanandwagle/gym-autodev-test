package com.studio.booking.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-5: PageResponse serialises to the documented shape:
 * { content, page: { number, size, totalElements, totalPages } }
 */
class PageResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialisesToDocumentedShape() throws Exception {
        var springPage = new PageImpl<>(
                List.of("item1", "item2"),
                PageRequest.of(0, 20),
                42L
        );

        PageResponse<String> pageResponse = PageResponse.from(springPage);
        String json = objectMapper.writeValueAsString(pageResponse);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("content")).isTrue();
        assertThat(root.has("page")).isTrue();

        JsonNode content = root.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).asText()).isEqualTo("item1");

        JsonNode page = root.get("page");
        assertThat(page.get("number").asInt()).isEqualTo(0);
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(42L);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void emptyPageSerialises() throws Exception {
        var springPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0L
        );

        PageResponse<String> pageResponse = PageResponse.from(springPage);
        String json = objectMapper.writeValueAsString(pageResponse);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("content").isArray()).isTrue();
        assertThat(root.get("content")).hasSize(0);
        assertThat(root.get("page").get("totalElements").asLong()).isZero();
        assertThat(root.get("page").get("totalPages").asInt()).isZero();
    }
}
