package com.studio.booking.architecture.violation.controller;

import org.springframework.transaction.annotation.Transactional;

/** Deliberate rule-3 violation: @Transactional outside application package. */
@Transactional
public class TransactionalController {}
