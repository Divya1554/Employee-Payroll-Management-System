package com.mca.payroll.exception;

/** Thrown when a leave request is invalid, overlaps another request or exceeds the balance. */
public class InvalidLeaveRequestException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public InvalidLeaveRequestException(String message) {
        super(message);
    }
}
