package com.mca.payroll.exception;

/**
 * Base class of every application-specific exception. It is unchecked so that service
 * methods stay readable, and its message is always safe to show to the user
 * (no SQL, no credentials, no stack information).
 */
public class PayrollException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PayrollException(String message) {
        super(message);
    }

    public PayrollException(String message, Throwable cause) {
        super(message, cause);
    }
}
