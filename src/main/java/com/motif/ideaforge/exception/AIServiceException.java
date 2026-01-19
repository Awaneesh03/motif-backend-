package com.motif.ideaforge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when AI service (Groq) fails
 */
public class AIServiceException extends BaseException {
    public AIServiceException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "AI_SERVICE_ERROR");
    }

    public AIServiceException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "AI_SERVICE_ERROR");
    }
}
