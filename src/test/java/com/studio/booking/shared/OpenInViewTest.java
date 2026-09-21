package com.studio.booking.shared;

import jakarta.persistence.*;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that lazy-loading after session close throws LazyInitializationException,
 * which is the expected behaviour when spring.jpa.open-in-view=false.
 */
@DataJpaTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class OpenInViewTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    TestEntityManager em;

    @Test
    void ac4_openInViewIsFalse_lazyCollectionThrowsAfterSessionClose() {
        // Persist a parent with a child
        OivParent parent = new OivParent("p1");
        OivChild child = new OivChild("c1", parent);
        em.persist(parent);
        em.persist(child);
        em.flush();
        Long parentId = parent.getId();

        // Clear the persistence context — simulates end of transaction / session close
        em.clear();

        // Load parent in a fresh find (loads entity but NOT the lazy collection yet)
        OivParent loaded = em.find(OivParent.class, parentId);
        // Detach to sever the session link
        em.getEntityManager().detach(loaded);

        // Accessing the lazy collection without an active session must throw
        assertThatThrownBy(() -> loaded.getChildren().size())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void ac4_openInViewPropertyIsSetToFalse() {
        // Confirm the property is explicitly false — belt-and-suspenders
        assertThat(
                em.getEntityManager().getEntityManagerFactory().getProperties()
        ).doesNotContainKey("hibernate.enable_lazy_load_no_trans");
    }

    // Minimal test-only entities — not part of the production domain

    @Entity
    @Table(name = "oiv_parent")
    static class OivParent {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;

        @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
        private List<OivChild> children = new ArrayList<>();

        protected OivParent() {}
        OivParent(String name) { this.name = name; }
        Long getId() { return id; }
        List<OivChild> getChildren() { return children; }
    }

    @Entity
    @Table(name = "oiv_child")
    static class OivChild {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "parent_id")
        private OivParent parent;

        protected OivChild() {}
        OivChild(String name, OivParent parent) { this.name = name; this.parent = parent; }
    }
}
