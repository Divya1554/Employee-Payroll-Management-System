package com.mca.payroll.exception;

/** Thrown when payroll cannot be calculated or changed (missing salary structure, negative net salary, finalized record ...). */
public class InvalidPayrollException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public InvalidPayrollException(String message) {
        super(message);
    }
}
