package com.mca.payroll.dto;

import com.mca.payroll.entity.EmploymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only view of an employee (safe to use after the Hibernate session is closed). */
public record EmployeeDto(Long id, String employeeCode, String firstName, String lastName, String email,
                          String phone, LocalDate dateOfBirth, LocalDate dateOfJoining, String designation,
                          EmploymentStatus status, Long departmentId, String departmentName,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
