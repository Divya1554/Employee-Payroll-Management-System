package com.mca.payroll.exception;

/** Thrown when a department cannot be found. */
public class DepartmentNotFoundException extends PayrollException {

    private static final long serialVersionUID = 1L;

    public DepartmentNotFoundException(Long departmentId) {
        super("Department with ID " + departmentId + " was not found.");
    }

    public DepartmentNotFoundException(String message) {
        super(message);
    }
}
