package com.mca.payroll.exception;

/** Thrown when an employee code or e-mail address is already in use. */
public class DuplicateEmployeeException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public DuplicateEmployeeException(String message) {
        super(message);
    }
}
