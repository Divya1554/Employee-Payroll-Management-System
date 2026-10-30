package com.mca.payroll.exception;

/**
 * Wraps low-level Hibernate / JDBC failures. The message is user-safe (it never contains SQL or
 * connection details); the original cause is kept for the log file.
 */
public class DatabaseOperationException extends PayrollException {

    private static final long serialVersionUID = 1L;

    private final boolean constraintViolation;

    public DatabaseOperationException(String message, Throwable cause, boolean constraintViolation) {
        super(message, cause);
        this.constraintViolation = constraintViolation;
    }

    /** True when the failure was caused by a unique / foreign-key / not-null constraint. */
    public boolean isConstraintViolation() {
        return constraintViolation;
    }
}
