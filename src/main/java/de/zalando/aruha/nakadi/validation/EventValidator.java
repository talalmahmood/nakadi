package de.zalando.aruha.nakadi.validation;

import org.json.JSONObject;

import java.util.Optional;

public interface EventValidator {
    Optional<ValidationError> accepts(final JSONObject event);
}
