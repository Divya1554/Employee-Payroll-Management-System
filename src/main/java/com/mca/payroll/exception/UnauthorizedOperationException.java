package com.mca.payroll.exception;

/** Thrown when the signed-in user is not allowed to perform an operation. */
public class UnauthorizedOperationException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
