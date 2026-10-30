package com.mca.payroll.exception;

/** Thrown when a login attempt fails. The message is deliberately generic. */
public class AuthenticationFailedException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
