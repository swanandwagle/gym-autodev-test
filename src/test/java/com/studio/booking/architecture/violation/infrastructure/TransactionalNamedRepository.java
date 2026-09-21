package com.studio.booking.architecture.violation.infrastructure;

import org.springframework.transaction.annotation.Transactional;

/** Deliberate rule-4 violation: repository class annotated with @Transactional. */
@Transactional
public class TransactionalNamedRepository {}
