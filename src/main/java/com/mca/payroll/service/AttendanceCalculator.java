package com.mca.payroll.service;

import com.mca.payroll.dto.AttendanceSummaryDto;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.util.WorkingCalendar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure attendance arithmetic (no database). Every working day of the month is classified exactly
 * once, so presentDays + paidLeaveDays + unpaidDays = workingDays and nothing is deducted twice.
 *
 * <ul>
 *   <li>PRESENT: 1 present day</li>
 *   <li>HALF_DAY: (1 - fraction) present, fraction unpaid (policy payroll.halfday.unpaid.fraction)</li>
 *   <li>PAID_LEAVE: 1 paid-leave day</li>
 *   <li>UNPAID_LEAVE and ABSENT: 1 unpaid day</li>
 *   <li>day before the joining date: 1 unpaid day ("not employed")</li>
 *   <li>no record: per policy payroll.missing.attendance (PRESENT or ABSENT)</li>
 *   <li>records on weekly-off days are ignored</li>
 * </ul>
 */
public final class AttendanceCalculator {

    private AttendanceCalculator() {
    }

    public static AttendanceSummaryDto summarize(YearMonth period, LocalDate dateOfJoining,
                                                 Map<LocalDate, AttendanceStatus> records, PayrollPolicy policy) {
        List<LocalDate> workingDays = WorkingCalendar.workingDays(period.atDay(1), period.atEndOfMonth(),
                policy.getWeeklyOffs());

        int present = 0, absent = 0, half = 0, paidLeave = 0, unpaidLeave = 0, notEmployed = 0, unrecorded = 0;
        BigDecimal presentDays = BigDecimal.ZERO;
        BigDecimal paidLeaveDays = BigDecimal.ZERO;
        BigDecimal unpaidDays = BigDecimal.ZERO;
        List<LocalDate> absentDates = new ArrayList<>();
        BigDecimal fraction = policy.getHalfDayUnpaidFraction();

        for (LocalDate day : workingDays) {
            if (day.isBefore(dateOfJoining)) {
                notEmployed++;
                unpaidDays = unpaidDays.add(BigDecimal.ONE);
                continue;
            }
            AttendanceStatus status = records.get(day);
            if (status == null) {
                unrecorded++;
                if (policy.getMissingAttendance() == PayrollPolicy.MissingAttendance.PRESENT) {
                    presentDays = presentDays.add(BigDecimal.ONE);
                } else {
                    unpaidDays = unpaidDays.add(BigDecimal.ONE);
                    absentDates.add(day);
                }
                continue;
            }
            switch (status) {
                case PRESENT -> {
                    present++;
                    presentDays = presentDays.add(BigDecimal.ONE);
                }
                case HALF_DAY -> {
                    half++;
                    presentDays = presentDays.add(BigDecimal.ONE.subtract(fraction));
                    unpaidDays = unpaidDays.add(fraction);
                }
                case PAID_LEAVE -> {
                    paidLeave++;
                    paidLeaveDays = paidLeaveDays.add(BigDecimal.ONE);
                }
                case UNPAID_LEAVE -> {
                    unpaidLeave++;
                    unpaidDays = unpaidDays.add(BigDecimal.ONE);
                }
                case ABSENT -> {
                    absent++;
                    unpaidDays = unpaidDays.add(BigDecimal.ONE);
                    absentDates.add(day);
                }
            }
        }
        return new AttendanceSummaryDto(period, workingDays.size(), present, absent, half, paidLeave, unpaidLeave,
                notEmployed, unrecorded, presentDays, paidLeaveDays, unpaidDays, absentDates);
    }
}
