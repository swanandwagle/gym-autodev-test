package com.studio.booking;

import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-4: open-in-view is false; lazy-loading outside a transaction throws
 * LazyInitializationException rather than silently working.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
@Testcontainers
class OpenInViewTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("booking_test")
            .withUsername("booking")
            .withPassword("booking");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private Seeder seeder;

    @Test
    void lazyLoadOutsideTransaction_throwsLazyInitializationException() {
        UUID parentId = seeder.createParentWithChildren();

        // Load the entity outside any transaction — the children collection is lazy
        Parent parent = parentRepository.findById(parentId).orElseThrow();

        // Accessing the lazy collection outside a transaction must throw
        assertThatThrownBy(() -> parent.getChildren().size())
                .isInstanceOf(org.hibernate.LazyInitializationException.class);
    }

    // -----------------------------------------------------------------------
    // Minimal test entities and repository
    // -----------------------------------------------------------------------

    @Entity
    @Table(name = "oiv_test_parent")
    static class Parent {

        @Id
        UUID id = UUID.randomUUID();

        @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
        List<Child> children = new ArrayList<>();

        public List<Child> getChildren() {
            return children;
        }
    }

    @Entity
    @Table(name = "oiv_test_child")
    static class Child {

        @Id
        UUID id = UUID.randomUUID();

        @ManyToOne(fetch = FetchType.LAZY)
        Parent parent;
    }

    interface ParentRepository extends JpaRepository<Parent, UUID> {}

    @TestConfiguration
    static class TestConfig {

        @Bean
        Seeder seeder(ParentRepository parentRepository) {
            return new Seeder(parentRepository);
        }
    }

    static class Seeder {

        private final ParentRepository parentRepository;

        Seeder(ParentRepository parentRepository) {
            this.parentRepository = parentRepository;
        }

        @Transactional
        UUID createParentWithChildren() {
            Parent parent = new Parent();
            Child child = new Child();
            child.parent = parent;
            parent.children.add(child);
            return parentRepository.save(parent).id;
        }
    }
}
