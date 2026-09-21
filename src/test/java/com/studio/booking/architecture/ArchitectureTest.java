package com.studio.booking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureTest {

    private static final String ROOT = "com.studio.booking";

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    // -----------------------------------------------------------------------
    // Rule 1: Layering — api → application → domain ← infrastructure
    // -----------------------------------------------------------------------

    static ArchRule layeringRule() {
        return Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("api").definedBy(ROOT + "..*.api..")
                .layer("application").definedBy(ROOT + "..*.application..")
                .layer("domain").definedBy(ROOT + "..*.domain..")
                .layer("infrastructure").definedBy(ROOT + "..*.infrastructure..")
                .whereLayer("api").mayOnlyAccessLayers("application", "domain")
                .whereLayer("application").mayOnlyAccessLayers("domain")
                .whereLayer("infrastructure").mayOnlyAccessLayers("domain")
                .whereLayer("domain").mayNotAccessAnyLayer();
    }

    @Test
    void ac3_rule1_layeringPassesOnMainClasses() {
        layeringRule().check(MAIN_CLASSES);
    }

    @Test
    void ac3_rule1_layeringViolationIsDetected() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importPackages("com.studio.booking.architecture.violations");

        ArchRule rule = Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("application").definedBy("com.studio.booking.architecture.violations.application..")
                .layer("domain").definedBy("com.studio.booking.architecture.violations.domain..")
                .whereLayer("domain").mayNotAccessAnyLayer();

        assertThatThrownBy(() -> rule.check(violatingClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("domain");
    }

    // -----------------------------------------------------------------------
    // Rule 2: @Transactional only in application packages (class or method level)
    // -----------------------------------------------------------------------

    @Test
    void ac3_rule2_noTransactionalOnClassesOutsideApplicationLayer() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + "..")
                .and().resideOutsideOfPackage(ROOT + "..*.application..")
                .should().beAnnotatedWith(Transactional.class)
                .because("@Transactional belongs only in application packages");
        rule.check(MAIN_CLASSES);
    }

    @Test
    void ac3_rule2_noTransactionalOnMethodsOutsideApplicationLayer() {
        ArchRule rule = ArchRuleDefinition.noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage(ROOT + "..")
                .and().areDeclaredInClassesThat().resideOutsideOfPackage(ROOT + "..*.application..")
                .should().beAnnotatedWith(Transactional.class)
                .because("@Transactional belongs only in application packages");
        rule.check(MAIN_CLASSES);
    }

    @Test
    void ac3_rule2_transactionalViolationIsDetected() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(TransactionalViolation.class);

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.studio.booking..")
                .and().resideOutsideOfPackage("com.studio.booking..*.application..")
                .should().beAnnotatedWith(Transactional.class)
                .because("@Transactional belongs only in application packages");

        assertThatThrownBy(() -> rule.check(violatingClasses))
                .isInstanceOf(AssertionError.class);
    }

    @Transactional
    static class TransactionalViolation {
        // In architecture package (not application) — deliberate violation
    }

    // -----------------------------------------------------------------------
    // Rule 3: Controllers only in api packages
    // -----------------------------------------------------------------------

    @Test
    void ac3_rule3_controllersOnlyInApiPassesOnMainClasses() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(ROOT + "..*.api..")
                .because("@RestController must only live in api packages");
        rule.check(MAIN_CLASSES);
    }

    @Test
    void ac3_rule3_controllerViolationIsDetected() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(ControllerInDomainViolation.class);

        ArchRule rule = ArchRuleDefinition.classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(ROOT + "..*.api..")
                .because("@RestController must only live in api packages");

        assertThatThrownBy(() -> rule.check(violatingClasses))
                .isInstanceOf(AssertionError.class);
    }

    @RestController
    static class ControllerInDomainViolation {
        // In architecture package (not api) — deliberate violation
    }

    // -----------------------------------------------------------------------
    // Rule 4: No cross-module direct class dependencies
    // -----------------------------------------------------------------------

    @Test
    void ac3_rule4_noCrossModuleDependenciesPassesOnMainClasses() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + ".member..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".membership..",
                        ROOT + ".catalog..",
                        ROOT + ".booking..",
                        ROOT + ".reporting..",
                        ROOT + ".jobs.."
                )
                .because("Modules communicate through application-layer interfaces only");
        rule.check(MAIN_CLASSES);
    }

    @Test
    void ac3_rule4_crossModuleDependencyViolationIsDetected() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(CrossModuleViolation.class, CrossModuleTarget.class);

        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().haveSimpleNameEndingWith("Violation")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Target")
                .because("No cross-module class dependencies allowed");

        assertThatThrownBy(() -> rule.check(violatingClasses))
                .isInstanceOf(AssertionError.class);
    }

    static class CrossModuleTarget {
    }

    static class CrossModuleViolation {
        private CrossModuleTarget target;
    }
}
