package com.mca.payroll.exception;

/** Thrown when a request is well formed but breaks a business rule (e.g. deleting a department that still has employees). */
public class BusinessRuleViolationException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
