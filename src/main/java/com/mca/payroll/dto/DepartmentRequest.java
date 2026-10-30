package com.mca.payroll.dto;

/** Input for creating or updating a department. */
public record DepartmentRequest(String name, String description, String location) {
}
