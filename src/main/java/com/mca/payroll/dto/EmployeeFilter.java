package com.mca.payroll.dto;

import com.mca.payroll.entity.EmploymentStatus;

import java.time.LocalDate;

/** Optional criteria for the dynamic (Criteria API) employee search. Null fields are ignored. */
public record EmployeeFilter(String nameFragment, Long departmentId, String designation,
                             EmploymentStatus status, LocalDate joinedFrom, LocalDate joinedTo) {
}
