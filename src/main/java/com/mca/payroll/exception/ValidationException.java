package com.mca.payroll.exception;

/** Thrown when user supplied data is missing or malformed. */
public class ValidationException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
