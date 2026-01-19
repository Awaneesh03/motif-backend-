package com.motif.ideaforge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user is not authenticated
 */
public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
