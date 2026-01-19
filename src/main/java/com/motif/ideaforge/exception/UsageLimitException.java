package com.motif.ideaforge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when usage limit is exceeded
 */
public class UsageLimitException extends BaseException {
    public UsageLimitException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "USAGE_LIMIT_EXCEEDED");
    }
}
