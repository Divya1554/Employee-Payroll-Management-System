package com.mca.payroll.dto;

/** Everything needed to print a payslip: company header plus the payroll snapshot. */
public record PayslipDto(String companyName, String companyAddress, String companyPhone,
                         String companyEmail, String currencyLabel, PayrollDto payroll) {
}
