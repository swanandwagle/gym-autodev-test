package com.studio.booking.violations.rule3;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Violation stub: a @Repository class also annotated @Transactional. */
@Repository
@Transactional
@SuppressWarnings("unused")
public class FakeTransactionalRepository {}
