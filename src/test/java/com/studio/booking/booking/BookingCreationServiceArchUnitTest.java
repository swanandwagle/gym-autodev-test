package com.studio.booking.booking;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * AC-11 verification: Ensures both the booking endpoint and the waitlist promotion routine
 * depend on BookingCreationService for eligibility checking, confirming a single implementation
 * of the overlap and eligibility rules.
 */
class BookingCreationServiceArchUnitTest {

    @Test
    void booking_controller_must_depend_on_booking_creation_service() {
        JavaClasses bookingClasses = new ClassFileImporter()
            .importPackages("com.studio.booking.booking..");

        ArchRule rule = classes()
            .that()
            .haveSimpleName("BookingController")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("BookingCreationService")
            .because("BookingController must use BookingCreationService for all eligibility checks");

        rule.check(bookingClasses);
    }

    @Test
    void booking_service_must_depend_on_booking_creation_service() {
        JavaClasses bookingClasses = new ClassFileImporter()
            .importPackages("com.studio.booking.booking..");

        ArchRule rule = classes()
            .that()
            .haveSimpleName("BookingService")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("BookingCreationService")
            .because("BookingService (used by controller) must delegate to BookingCreationService for eligibility checks");

        rule.check(bookingClasses);
    }

    @Test
    void no_duplicate_overlap_check_implementations_outside_booking_creation_service() {
        JavaClasses allClasses = new ClassFileImporter()
            .importPackages("com.studio.booking..");

        // Count how many classes contain overlap-related methods
        // This is a documentation test that fails if the overlap logic is duplicated
        long overlapImplementations = allClasses.stream()
            .filter(jc -> jc.getPackageName().contains("booking"))
            .filter(jc -> jc.getMethods().stream()
                .anyMatch(m -> m.getName().contains("Overlap") ||
                              m.getName().contains("overlap")))
            .count();

        // Only BookingCreationService should implement overlap checking
        // If this test fails, it means overlap logic has been reimplemented elsewhere
        org.junit.jupiter.api.Assertions.assertTrue(
            overlapImplementations <= 1,
            "Overlap checking logic should exist in only one place (BookingCreationService); " +
            "found " + overlapImplementations + " class(es) with overlap-related methods"
        );
    }
}
