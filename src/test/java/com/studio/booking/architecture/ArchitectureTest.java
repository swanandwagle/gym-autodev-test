package com.studio.booking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AnalyzeClasses(
        packages = "com.studio.booking",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    // Rule 1: api layer must not depend on infrastructure layer
    @ArchTest
    static final ArchRule ac3_rule1_apiMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .as("API layer must not depend on infrastructure layer");

    // Rule 2: infrastructure layer must not depend on api layer
    @ArchTest
    static final ArchRule ac3_rule2_infrastructureMustNotDependOnApi =
            noClasses().that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("..api..")
                    .as("Infrastructure layer must not depend on API layer");

    // Rule 3: @Transactional only in application packages
    @ArchTest
    static final ArchRule ac3_rule3_transactionalOnlyInApplicationPackage =
            classes().that().areAnnotatedWith(Transactional.class)
                    .should().resideInAPackage("..application..")
                    .as("@Transactional must only be used in application packages");

    // Rule 4: Repository classes in infrastructure must not be @Transactional
    @ArchTest
    static final ArchRule ac3_rule4_repositoriesNotTransactional =
            noClasses().that().resideInAPackage("..infrastructure..")
                    .and().haveNameMatching(".*Repository")
                    .should().beAnnotatedWith(Transactional.class)
                    .as("Repository classes must not be annotated with @Transactional");

    // Violation proofs: each rule must catch a deliberate violation

    @Test
    void ac3_rule1_violation_apiDependsOnInfrastructure_isDetected() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(
                com.studio.booking.architecture.violation.api.ApiClassThatUsesInfrastructure.class,
                com.studio.booking.architecture.violation.infrastructure.SomeInfrastructureClass.class
        );

        assertThatThrownBy(() ->
                noClasses().that().resideInAPackage("..api..")
                        .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                        .check(violatingClasses)
        ).hasMessageContaining("was violated");
    }

    @Test
    void ac3_rule2_violation_infrastructureDependsOnApi_isDetected() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(
                com.studio.booking.architecture.violation.infrastructure.InfrastructureClassThatUsesApi.class,
                com.studio.booking.architecture.violation.api.SomeApiClass.class
        );

        assertThatThrownBy(() ->
                noClasses().that().resideInAPackage("..infrastructure..")
                        .should().dependOnClassesThat().resideInAPackage("..api..")
                        .check(violatingClasses)
        ).hasMessageContaining("was violated");
    }

    @Test
    void ac3_rule3_violation_transactionalOutsideApplication_isDetected() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(
                com.studio.booking.architecture.violation.controller.TransactionalController.class
        );

        assertThatThrownBy(() ->
                classes().that().areAnnotatedWith(Transactional.class)
                        .should().resideInAPackage("..application..")
                        .check(violatingClasses)
        ).hasMessageContaining("was violated");
    }

    @Test
    void ac3_rule4_violation_repositoryIsTransactional_isDetected() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(
                com.studio.booking.architecture.violation.infrastructure.TransactionalNamedRepository.class
        );

        assertThatThrownBy(() ->
                noClasses().that().resideInAPackage("..infrastructure..")
                        .and().haveNameMatching(".*Repository")
                        .should().beAnnotatedWith(Transactional.class)
                        .check(violatingClasses)
        ).hasMessageContaining("was violated");
    }
}
