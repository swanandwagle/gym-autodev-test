package com.studio.booking.architecture.violation.api;

import com.studio.booking.architecture.violation.infrastructure.SomeInfrastructureClass;

/** Deliberate rule-1 violation: api package depending on infrastructure. */
public class ApiClassThatUsesInfrastructure {
    SomeInfrastructureClass ref = new SomeInfrastructureClass();
}
