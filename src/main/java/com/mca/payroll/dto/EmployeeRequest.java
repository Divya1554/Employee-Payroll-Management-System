package com.mca.payroll.dto;

import java.time.LocalDate;

/** Input for creating or updating an employee. The employee code cannot be changed on update. */
public record EmployeeRequest(String employeeCode, String firstName, String lastName, String email,
                              String phone, LocalDate dateOfBirth, LocalDate dateOfJoining,
                              String designation, Long departmentId) {
}
