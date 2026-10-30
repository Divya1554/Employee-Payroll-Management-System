package com.mca.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for creating a salary structure / revision. effectiveFrom is ignored when correcting an existing one. */
public record SalaryRequest(BigDecimal basicSalary, BigDecimal houseRentAllowance, BigDecimal medicalAllowance,
                            BigDecimal conveyanceAllowance, BigDecimal otherAllowances,
                            BigDecimal providentFundDeduction, BigDecimal professionalTax,
                            LocalDate effectiveFrom) {
}
