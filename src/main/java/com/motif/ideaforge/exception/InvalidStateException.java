package com.motif.ideaforge.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested state transition is not permitted given the resource's current status.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidStateException extends BaseException {

    public InvalidStateException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_STATE");
    }
}
