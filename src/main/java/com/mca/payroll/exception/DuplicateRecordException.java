package com.mca.payroll.exception;

/** Thrown when a unique record (department name, attendance for a date, user name ...) already exists. */
public class DuplicateRecordException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public DuplicateRecordException(String message) {
        super(message);
    }
}
