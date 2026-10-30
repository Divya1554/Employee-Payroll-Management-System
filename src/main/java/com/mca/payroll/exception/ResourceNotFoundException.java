package com.mca.payroll.exception;

/** Thrown when any other record (salary structure, leave request, payroll, user ...) cannot be found. */
public class ResourceNotFoundException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " with ID " + id + " was not found.");
    }
}
