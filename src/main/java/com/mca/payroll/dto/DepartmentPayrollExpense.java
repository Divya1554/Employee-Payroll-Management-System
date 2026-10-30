package com.mca.payroll.dto;

import java.math.BigDecimal;

/** Payroll expense of one department in one month. */
public record DepartmentPayrollExpense(String department, long employees, BigDecimal totalGross,
                                       BigDecimal totalNet) {
}
