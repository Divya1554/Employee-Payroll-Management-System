package com.mca.payroll.dto;

/** Read-only view of a department. */
public record DepartmentDto(Long id, String name, String description, String location) {
}
