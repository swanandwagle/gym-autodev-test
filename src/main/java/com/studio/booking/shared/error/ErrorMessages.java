package com.studio.booking.shared.error;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * Resolves human-readable detail messages for {@link ErrorCode} values from
 * {@code error-messages.properties}.
 *
 * Wording changes require only editing the properties file — no recompilation of
 * any business code is needed.
 */
public final class ErrorMessages {

    private static final MessageSource SOURCE;

    static {
        ResourceBundleMessageSource src = new ResourceBundleMessageSource();
        src.setBasename("error-messages");
        src.setDefaultEncoding("UTF-8");
        src.setUseCodeAsDefaultMessage(true);
        SOURCE = src;
    }

    private ErrorMessages() {}

    /** Returns the message template for the given code, falling back to the code name. */
    public static String forCode(ErrorCode code) {
        return SOURCE.getMessage(code.name(), null, code.name(), Locale.ROOT);
    }
}
