package com.mca.payroll.dto;

import com.mca.payroll.entity.PayrollStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Read-only view of a payroll record, including the salary snapshot used. */
public record PayrollDto(Long id, Long employeeId, String employeeCode, String employeeName,
                         String department, String designation, int month, int year,
                         int workingDays, BigDecimal presentDays, BigDecimal paidLeaveDays,
                         BigDecimal unpaidLeaveDays, int payableDaysBasis,
                         BigDecimal basicSalary, BigDecimal houseRentAllowance, BigDecimal medicalAllowance,
                         BigDecimal conveyanceAllowance, BigDecimal otherAllowances, BigDecimal grossSalary,
                         BigDecimal providentFundDeduction, BigDecimal professionalTax,
                         BigDecimal unpaidDeduction, BigDecimal otherDeductions,
                         BigDecimal totalDeductions, BigDecimal netSalary,
                         PayrollStatus status, LocalDateTime generatedAt, LocalDateTime finalizedAt,
                         String generatedBy) {

    public YearMonth period() {
        return YearMonth.of(year, month);
    }

    /** Manual other deductions = otherDeductions minus the loss-of-pay amount. */
    public BigDecimal manualOtherDeductions() {
        return otherDeductions.subtract(unpaidDeduction);
    }
}
