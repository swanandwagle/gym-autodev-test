package com.studio.booking.shared.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Jackson to reject unknown JSON properties with a 422.
 * Unknown fields surface as {@link com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException}
 * wrapped in {@link org.springframework.http.converter.HttpMessageNotReadableException},
 * which GlobalExceptionHandler maps to 422 VALIDATION_FAILED with an UNKNOWN_FIELD entry.
 */
@Configuration
public class StrictJsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictUnknownFieldsCustomizer() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
