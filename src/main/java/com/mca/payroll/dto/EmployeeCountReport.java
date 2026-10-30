package com.mca.payroll.dto;

/** Total / active / inactive employee counts. */
public record EmployeeCountReport(long total, long active, long inactive) {
}
