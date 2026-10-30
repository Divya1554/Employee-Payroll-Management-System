package com.mca.payroll.dto;

import com.mca.payroll.entity.PayrollStatus;

import java.math.BigDecimal;

/** Number and net total of payroll records in one status. */
public record PayrollStatusCount(PayrollStatus status, long records, BigDecimal totalNet) {
}
