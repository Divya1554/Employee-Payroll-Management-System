package com.mca.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only view of one salary structure (one revision). */
public record SalaryStructureDto(Long id, Long employeeId, String employeeCode, String employeeName,
                                 BigDecimal basicSalary, BigDecimal houseRentAllowance,
                                 BigDecimal medicalAllowance, BigDecimal conveyanceAllowance,
                                 BigDecimal otherAllowances, BigDecimal providentFundDeduction,
                                 BigDecimal professionalTax, BigDecimal grossSalary,
                                 LocalDate effectiveFrom, LocalDate effectiveTo) {
}
