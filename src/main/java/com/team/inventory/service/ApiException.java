package com.team.inventory.service;

import org.springframework.http.HttpStatus;

/**
 * A request that can't be completed, for the product, category and report APIs.
 * Comes back to the browser as {"error": message} with the given status (see ApiExceptionHandler).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
}
