package com.studio.booking.architecture.violation.infrastructure;

import com.studio.booking.architecture.violation.api.SomeApiClass;

/** Deliberate rule-2 violation: infrastructure package depending on api. */
public class InfrastructureClassThatUsesApi {
    SomeApiClass ref = new SomeApiClass();
}
