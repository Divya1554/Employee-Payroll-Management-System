package com.mca.payroll.exception;

/** Thrown when an employee cannot be found. */
public class EmployeeNotFoundException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public EmployeeNotFoundException(Long employeeId) {
        super("Employee with ID " + employeeId + " was not found.");
    }

    public EmployeeNotFoundException(String message) {
        super(message);
    }
}
