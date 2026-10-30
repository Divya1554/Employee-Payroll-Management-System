package com.mca.payroll.dto;

import java.math.BigDecimal;

/** Output of the PayrollCalculator. All amounts use the configured scale and rounding mode. */
public record PayrollResult(int workingDays, BigDecimal presentDays, BigDecimal paidLeaveDays,
                            BigDecimal unpaidDays, int payableDaysBasis,
                            BigDecimal basicSalary, BigDecimal houseRentAllowance, BigDecimal medicalAllowance,
                            BigDecimal conveyanceAllowance, BigDecimal otherAllowances, BigDecimal grossSalary,
                            BigDecimal providentFundDeduction, BigDecimal professionalTax,
                            BigDecimal unpaidDeduction, BigDecimal otherDeductions,
                            BigDecimal totalDeductions, BigDecimal netSalary) {
}
