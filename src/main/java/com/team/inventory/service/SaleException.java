package com.team.inventory.service;

import org.springframework.http.HttpStatus;

/** A sale that can't be recorded. The message is shown to the cashier, so keep it readable. */
public class SaleException extends RuntimeException {

    /** The same sale is still being saved by another request; the outcome isn't known yet. */
    public static final String SALE_IN_PROGRESS = "SALE_IN_PROGRESS";

    private final HttpStatus status;
    private final String code;

    public SaleException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public SaleException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }

    /** Optional machine-readable code for the frontend, e.g. SALE_IN_PROGRESS. */
    public String getCode() { return code; }
}
