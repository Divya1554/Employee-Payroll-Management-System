package com.mca.payroll.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Totals for all payroll records of one month. */
public record MonthlyPayrollSummary(YearMonth period, long records, BigDecimal totalGross,
                                    BigDecimal totalDeductions, BigDecimal totalNet) {
}
