package com.studio.booking.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ac5_serialisesToDocumentedShape() throws Exception {
        var springPage = new PageImpl<>(
                List.of("a", "b"),
                PageRequest.of(0, 20),
                42L
        );
        var response = PageResponse.from(springPage);

        String json = mapper.writeValueAsString(response);
        JsonNode root = mapper.readTree(json);

        assertThat(root.has("content")).isTrue();
        assertThat(root.get("content").isArray()).isTrue();

        JsonNode page = root.get("page");
        assertThat(page).isNotNull();
        assertThat(page.get("number").asInt()).isEqualTo(0);
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(42L);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);

        // Ensure no extra top-level fields
        assertThat(root.size()).isEqualTo(2);
    }
}
