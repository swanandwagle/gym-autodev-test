package com.studio.booking.shared.jpa;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OpenInViewTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("booking_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    OivParentRepo parentRepo;

    @Autowired
    OivSetupHelper setupHelper;

    @Test
    void ac4_lazyLoadOutsideTransactionThrows() {
        UUID parentId = setupHelper.createParentWithChild();

        // Load entity — the JPA session closes when the repository call returns
        OivParent parent = parentRepo.findById(parentId).orElseThrow();

        // Accessing the lazy collection outside a transaction must throw because open-in-view=false
        assertThatThrownBy(() -> parent.children.size())
                .isInstanceOf(LazyInitializationException.class);
    }
}
