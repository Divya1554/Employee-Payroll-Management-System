package com.mca.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Attendance summary of one employee for one month.
 *
 * <p>Invariant: presentDays + paidLeaveDays + unpaidDays = workingDays. Days before the joining date
 * count as unpaid ("not employed"); working days without a record follow the configured
 * payroll.missing.attendance policy.</p>
 */
public record AttendanceSummaryDto(YearMonth period, int workingDays,
                                   int presentCount, int absentCount, int halfDayCount,
                                   int paidLeaveCount, int unpaidLeaveCount,
                                   int notEmployedDays, int unrecordedDays,
                                   BigDecimal presentDays, BigDecimal paidLeaveDays, BigDecimal unpaidDays,
                                   List<LocalDate> absentDates) {
}
