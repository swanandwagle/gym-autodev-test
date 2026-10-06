package com.studio.booking.booking;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class BookingCreditPortArchUnitTest {

    @Test
    void booking_module_must_reference_credit_port_not_membership_entity_directly() {
        JavaClasses classes = new ClassFileImporter()
            .importPackages("com.studio.booking");

        // Booking module production classes must not directly reference Membership entity or MembershipRepository.
        // Test classes are excluded — they legitimately set up Membership data for integration tests.
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("com.studio.booking.booking..")
            .and()
            .haveSimpleNameNotEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.studio.booking.membership.infrastructure..")
            .because("Booking module must not access membership repositories directly; use CreditPort instead");

        rule.check(classes);
    }
}
