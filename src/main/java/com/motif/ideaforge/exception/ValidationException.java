package com.motif.ideaforge.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when validation fails
 */
@Getter
public class ValidationException extends BaseException {
    private final List<Map<String, String>> validationErrors;

    public ValidationException(String message, List<Map<String, String>> validationErrors) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        this.validationErrors = validationErrors;
    }
}
