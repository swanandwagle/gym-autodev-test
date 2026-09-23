package com.studio.booking.membership.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class ValidPriceValidator implements ConstraintValidator<ValidPrice, MoneyDto> {

    @Override
    public boolean isValid(MoneyDto value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.amount() == null || value.currency() == null) {
            return false;
        }

        BigDecimal amount = value.amount();

        // Check if amount is non-negative
        if (amount.signum() < 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Price amount must not be negative")
                    .addPropertyNode("amount")
                    .addConstraintViolation();
            return false;
        }

        // Check decimal scale (max 2 decimal places)
        if (amount.scale() > 2) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Price amount must have at most 2 decimal places")
                    .addPropertyNode("amount")
                    .addConstraintViolation();
            return false;
        }

        // Check currency is uppercase 3-char code
        String currency = value.currency();
        if (!currency.matches("^[A-Z]{3}$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Currency must be a 3-letter uppercase code (e.g., USD, EUR, INR)")
                    .addPropertyNode("currency")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
