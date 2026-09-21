package com.studio.booking.architecture;

import com.studio.booking.violations.rule1.api.FakeApiController;
import com.studio.booking.violations.rule1.infrastructure.FakeRepository;
import com.studio.booking.violations.rule2.FakeController;
import com.studio.booking.violations.rule3.FakeTransactionalRepository;
import com.studio.booking.violations.rule4.api.FakeApiDto;
import com.studio.booking.violations.rule4.application.FakeApplicationService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-3: Four architecture rules must pass on the skeleton, and each must
 * be proven to fail when a deliberate violation is introduced.
 */
class ArchitectureTest {

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.studio.booking");
    }

    // -----------------------------------------------------------------------
    // Rule 1 — api layer must not depend on infrastructure layer
    // -----------------------------------------------------------------------

    @Test
    void rule1_apiMustNotAccessInfrastructure_passesOnSkeleton() {
        apiMustNotAccessInfrastructureRule().check(mainClasses);
    }

    @Test
    void rule1_apiMustNotAccessInfrastructure_failsOnViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(FakeApiController.class, FakeRepository.class);

        EvaluationResult result = apiMustNotAccessInfrastructureRule().evaluate(violatingClasses);
        assertThat(result.hasViolation())
                .as("Rule 1 must catch api class depending on infrastructure class")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Rule 2 — @Transactional only in application packages
    // -----------------------------------------------------------------------

    @Test
    void rule2_transactionalOnlyInApplication_passesOnSkeleton() {
        transactionalOnlyInApplicationRule().check(mainClasses);
    }

    @Test
    void rule2_transactionalOnlyInApplication_failsOnViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(FakeController.class);

        EvaluationResult result = transactionalOnlyInApplicationRule().evaluate(violatingClasses);
        assertThat(result.hasViolation())
                .as("Rule 2 must catch @Transactional on class outside application package")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Rule 3 — Repositories must not be @Transactional
    // -----------------------------------------------------------------------

    @Test
    void rule3_noTransactionalOnRepositories_passesOnSkeleton() {
        noTransactionalOnRepositoriesRule().check(mainClasses);
    }

    @Test
    void rule3_noTransactionalOnRepositories_failsOnViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(FakeTransactionalRepository.class);

        EvaluationResult result = noTransactionalOnRepositoriesRule().evaluate(violatingClasses);
        assertThat(result.hasViolation())
                .as("Rule 3 must catch @Repository class also annotated @Transactional")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Rule 4 — application layer must not depend on api layer
    // -----------------------------------------------------------------------

    @Test
    void rule4_applicationMustNotAccessApi_passesOnSkeleton() {
        applicationMustNotAccessApiRule().check(mainClasses);
    }

    @Test
    void rule4_applicationMustNotAccessApi_failsOnViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(FakeApplicationService.class, FakeApiDto.class);

        EvaluationResult result = applicationMustNotAccessApiRule().evaluate(violatingClasses);
        assertThat(result.hasViolation())
                .as("Rule 4 must catch application class depending on api class")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Rule definitions (package-private so the rule names appear in reports)
    // -----------------------------------------------------------------------

    static ArchRule apiMustNotAccessInfrastructureRule() {
        return noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .as("Rule 1: api must not depend on infrastructure");
    }

    static ArchRule transactionalOnlyInApplicationRule() {
        return noClasses()
                .that().resideOutsideOfPackage("..application..")
                .should().beAnnotatedWith(Transactional.class)
                .as("Rule 2: @Transactional only in application packages");
    }

    static ArchRule noTransactionalOnRepositoriesRule() {
        return noClasses()
                .that().areAnnotatedWith(Repository.class)
                .should().beAnnotatedWith(Transactional.class)
                .as("Rule 3: repositories must not be @Transactional");
    }

    static ArchRule applicationMustNotAccessApiRule() {
        return noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .as("Rule 4: application must not depend on api");
    }
}
