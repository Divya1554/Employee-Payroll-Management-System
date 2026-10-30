package com.mca.payroll.exception;

/** Thrown when payroll for the same employee, month and year already exists. */
public class PayrollAlreadyExistsException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public PayrollAlreadyExistsException(String message) {
        super(message);
    }
}
