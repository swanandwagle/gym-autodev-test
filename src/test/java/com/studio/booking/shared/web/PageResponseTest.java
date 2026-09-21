package com.studio.booking.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class PageResponseTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void ac5_serialisesToDocumentedShape() throws Exception {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 0, 20, 2);

        String json = objectMapper.writeValueAsString(response);
        var node = objectMapper.readTree(json);

        assertThat(node.has("content")).isTrue();
        assertThat(node.get("content").isArray()).isTrue();
        assertThat(node.get("content").size()).isEqualTo(2);

        assertThat(node.has("page")).isTrue();
        var page = node.get("page");
        assertThat(page.get("number").asInt()).isEqualTo(0);
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    void ac5_totalPagesCalculatedCorrectly() {
        PageResponse<Integer> r1 = PageResponse.of(List.of(), 0, 20, 0);
        assertThat(r1.page().totalPages()).isEqualTo(0);

        PageResponse<Integer> r2 = PageResponse.of(List.of(1, 2, 3), 0, 2, 3);
        assertThat(r2.page().totalPages()).isEqualTo(2);

        PageResponse<Integer> r3 = PageResponse.of(List.of(1, 2), 0, 2, 4);
        assertThat(r3.page().totalPages()).isEqualTo(2);
    }

    @Test
    void ac5_noExtraFieldsInPageNode() throws Exception {
        PageResponse<String> response = PageResponse.of(List.of("x"), 1, 10, 11);
        String json = objectMapper.writeValueAsString(response);
        var page = objectMapper.readTree(json).get("page");

        assertThat(page.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("number", "size", "totalElements", "totalPages");
    }
}
