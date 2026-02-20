package com.motif.ideaforge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when AI service (OpenAI) fails
 * Uses 503 Service Unavailable to indicate the AI backend is having issues
 */
public class AIServiceException extends BaseException {
    
    public AIServiceException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_ERROR");
    }

    public AIServiceException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_ERROR");
    }
    
    /**
     * Create a timeout-specific exception
     */
    public static AIServiceException timeout() {
        return new AIServiceException("AI service took too long to respond. Please try again.");
    }
    
    /**
     * Create a rate-limit exception
     */
    public static AIServiceException rateLimited() {
        return new AIServiceException("AI service is busy. Please wait a moment and try again.");
    }
}
