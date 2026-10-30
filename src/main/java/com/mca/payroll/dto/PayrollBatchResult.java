package com.mca.payroll.dto;

import java.time.YearMonth;
import java.util.List;

/** Outcome of generating payroll for all employees of a month. */
public record PayrollBatchResult(YearMonth period, List<PayrollDto> generated, List<String> skipped) {
}
