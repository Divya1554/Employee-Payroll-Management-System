package com.mca.payroll.unit;

import com.mca.payroll.dto.AttendanceSummaryDto;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.service.AttendanceCalculator;
import com.mca.payroll.service.PayrollPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mca.payroll.unit.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for monthly attendance summaries. April 2025 has 30 days, 4 Sundays -> 26 working days. */
class AttendanceCalculatorTest {

    private static final YearMonth APRIL = YearMonth.of(2025, 4);
    private static final LocalDate OLD = LocalDate.of(2020, 1, 1);

    @Test
    @DisplayName("Summary counts every status and the buckets add up to the working days")
    void summaryCountsAndInvariant() {
        Map<LocalDate, AttendanceStatus> att = new HashMap<>();
        att.put(LocalDate.of(2025, 4, 1), AttendanceStatus.PRESENT);
        att.put(LocalDate.of(2025, 4, 2), AttendanceStatus.ABSENT);
        att.put(LocalDate.of(2025, 4, 3), AttendanceStatus.HALF_DAY);
        att.put(LocalDate.of(2025, 4, 4), AttendanceStatus.PAID_LEAVE);
        att.put(LocalDate.of(2025, 4, 5), AttendanceStatus.UNPAID_LEAVE);

        AttendanceSummaryDto s = AttendanceCalculator.summarize(APRIL, OLD, att, PayrollPolicy.defaults());

        assertEquals(26, s.workingDays());
        assertEquals(1, s.presentCount());
        assertEquals(1, s.absentCount());
        assertEquals(1, s.halfDayCount());
        assertEquals(1, s.paidLeaveCount());
        assertEquals(1, s.unpaidLeaveCount());
        assertEquals(21, s.unrecordedDays());                        // missing = PRESENT by default
        assertEquals(bd("1.00"), s.paidLeaveDays().setScale(2));
        assertEquals(bd("2.50"), s.unpaidDays().setScale(2));        // absent 1 + unpaid leave 1 + half day 0.5
        assertEquals(bd("26"), s.presentDays().add(s.paidLeaveDays()).add(s.unpaidDays()).stripTrailingZeros());
    }

    @Test
    @DisplayName("Absent days are identified")
    void absentDaysAreIdentified() {
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 2), AttendanceStatus.ABSENT,
                LocalDate.of(2025, 4, 9), AttendanceStatus.ABSENT);
        AttendanceSummaryDto s = AttendanceCalculator.summarize(APRIL, OLD, att, PayrollPolicy.defaults());

        assertEquals(List.of(LocalDate.of(2025, 4, 2), LocalDate.of(2025, 4, 9)), s.absentDates());
    }

    @Test
    @DisplayName("Records on weekly-off days (Sunday) are ignored")
    void weeklyOffRecordsIgnored() {
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 6), AttendanceStatus.ABSENT); // a Sunday
        AttendanceSummaryDto s = AttendanceCalculator.summarize(APRIL, OLD, att, PayrollPolicy.defaults());

        assertEquals(0, s.absentCount());
        assertEquals(bd("0"), s.unpaidDays().stripTrailingZeros());
    }

    @Test
    @DisplayName("Working days before the joining date are counted as not employed (unpaid)")
    void beforeJoiningDate() {
        AttendanceSummaryDto s = AttendanceCalculator.summarize(APRIL, LocalDate.of(2025, 4, 21), Map.of(),
                PayrollPolicy.defaults());

        // 1st-20th April contains 17 non-Sunday days (Sundays: 6, 13, 20)
        assertEquals(17, s.notEmployedDays());
        assertEquals(bd("17"), s.unpaidDays().stripTrailingZeros());
    }

    @Test
    @DisplayName("Missing records are unpaid when the policy says ABSENT")
    void missingRecordsAbsentPolicy() {
        PayrollPolicy policy = TestData.policy(PayrollPolicy.PayableDaysMode.CALENDAR_DAYS, 30,
                java.util.EnumSet.of(java.time.DayOfWeek.SUNDAY), PayrollPolicy.MissingAttendance.ABSENT,
                PayrollPolicy.ProrationBase.GROSS, false, null);
        AttendanceSummaryDto s = AttendanceCalculator.summarize(APRIL, OLD, Map.of(), policy);

        assertEquals(26, s.unrecordedDays());
        assertEquals(bd("26"), s.unpaidDays().stripTrailingZeros());
        assertEquals(26, s.absentDates().size());
    }
}
