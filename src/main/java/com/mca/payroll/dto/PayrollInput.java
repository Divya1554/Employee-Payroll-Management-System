package com.mca.payroll.dto;

import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.SalaryComponents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/** Everything the pure PayrollCalculator needs (no database access involved). */
public record PayrollInput(YearMonth period, LocalDate dateOfJoining, SalaryComponents components,
                           Map<LocalDate, AttendanceStatus> attendance, BigDecimal manualOtherDeductions) {
}
