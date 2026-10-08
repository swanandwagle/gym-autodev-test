package com.studio.booking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AnalyzeClasses(
        packages = "com.studio.booking",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class MemberStatusGateArchTest {

    @ArchTest
    static final ArchRule ac5_booking_must_not_reference_member_entity =
            noClasses().that().resideInAPackage("com.studio.booking.booking..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.studio.booking.member.domain..")
                    .as("Booking module must not directly reference member domain entities");

    @ArchTest
    static final ArchRule ac5_booking_must_not_reference_member_repository =
            noClasses().that().resideInAPackage("com.studio.booking.booking..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.studio.booking.member.infrastructure..")
                    .as("Booking module must not directly reference member infrastructure");

    @ArchTest
    static final ArchRule ac5_booking_must_not_reference_member_service =
            noClasses().that().resideInAPackage("com.studio.booking.booking..")
                    .should().dependOnClassesThat(
                            resideInAPackage("com.studio.booking.member.application..").and(nameMatching(".*Service")))
                    .as("Booking module must not directly reference member services (use MemberStatusGate instead)");

    @Test
    void ac6_archunit_rule_fails_on_deliberate_direct_member_entity_import() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(
                com.studio.booking.architecture.violation.booking.BookingClassThatUsesMemberEntity.class,
                com.studio.booking.member.domain.Member.class
        );

        assertThatThrownBy(() ->
                noClasses().that().resideInAPackage("..booking..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("..member.domain..")
                        .check(violatingClasses)
        ).hasMessageContaining("was violated");
    }

    @Test
    void ac6_archunit_rule_fails_on_deliberate_member_repository_import() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(
                com.studio.booking.architecture.violation.booking.BookingClassThatUsesMemberRepository.class,
                com.studio.booking.member.infrastructure.MemberRepository.class
        );

        assertThatThrownBy(() ->
                noClasses().that().resideInAPackage("..booking..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("..member.infrastructure..")
                        .check(violatingClasses)
        ).hasMessageContaining("was violated");
    }
}
