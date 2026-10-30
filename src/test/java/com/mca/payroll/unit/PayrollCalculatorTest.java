package com.mca.payroll.unit;

import com.mca.payroll.dto.PayrollInput;
import com.mca.payroll.dto.PayrollResult;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.SalaryComponents;
import com.mca.payroll.exception.InvalidPayrollException;
import com.mca.payroll.service.PayrollCalculator;
import com.mca.payroll.service.PayrollPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.mca.payroll.unit.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the payroll formulas (salary calculation). No database needed. */
class PayrollCalculatorTest {

    private static final YearMonth APRIL_2025 = YearMonth.of(2025, 4);
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);

    private PayrollResult calc(PayrollPolicy policy, SalaryComponents c, Map<LocalDate, AttendanceStatus> attendance,
                               BigDecimal manual) {
        return new PayrollCalculator(policy).calculate(new PayrollInput(APRIL_2025, LONG_AGO, c, attendance, manual));
    }

    @Test
    @DisplayName("Specification example: 30,000 / 30 payable days x 2 unpaid days = 2,000 -> net 28,000")
    void specificationExample() {
        Map<LocalDate, AttendanceStatus> att = new HashMap<>();
        att.put(LocalDate.of(2025, 4, 3), AttendanceStatus.ABSENT);
        att.put(LocalDate.of(2025, 4, 4), AttendanceStatus.UNPAID_LEAVE);

        PayrollResult r = calc(TestData.fixed30NoOffs(), TestData.basicOnly("30000"), att, null);

        assertEquals(bd("2000.00"), r.unpaidDeduction());
        assertEquals(bd("30000.00"), r.grossSalary());
        assertEquals(bd("28000.00"), r.netSalary());
        assertEquals(30, r.payableDaysBasis());
    }

    @Test
    @DisplayName("Gross = Basic + HRA + Medical + Conveyance + Other; Net = Gross - Total deductions")
    void grossDeductionsAndNetFormulas() {
        PayrollResult r = calc(PayrollPolicy.defaults(), TestData.typicalSalary(), Map.of(), bd("150"));

        assertEquals(bd("45350.00"), r.grossSalary());
        assertEquals(bd("3600.00"), r.providentFundDeduction());
        assertEquals(bd("200.00"), r.professionalTax());
        assertEquals(bd("150.00"), r.otherDeductions());          // no unpaid days -> only the manual amount
        assertEquals(bd("3950.00"), r.totalDeductions());          // 3600 + 200 + 150
        assertEquals(bd("41400.00"), r.netSalary());               // 45350 - 3950
    }

    @Test
    @DisplayName("Unpaid days are deducted once and rounded HALF_UP to 2 decimals")
    void unpaidDeductionIsRounded() {
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 3), AttendanceStatus.ABSENT,
                LocalDate.of(2025, 4, 4), AttendanceStatus.ABSENT);
        // April 2025: CALENDAR_DAYS basis = 30; 45,350 x 2 / 30 = 3,023.333...
        PayrollResult r = calc(PayrollPolicy.defaults(), TestData.typicalSalary(), att, null);

        assertEquals(bd("3023.33"), r.unpaidDeduction());
        assertEquals(bd("3023.33"), r.otherDeductions());
        assertEquals(bd("38526.67"), r.netSalary());               // 45350 - (3600 + 200 + 3023.33)
    }

    @Test
    @DisplayName("HALF_DAY deducts the configured fraction of one day")
    void halfDayDeductsFraction() {
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 3), AttendanceStatus.HALF_DAY);
        PayrollResult r = calc(TestData.fixed30NoOffs(), TestData.basicOnly("30000"), att, null);

        assertEquals(bd("0.50"), r.unpaidDays());
        assertEquals(bd("500.00"), r.unpaidDeduction());           // 30000 / 30 x 0.5
    }

    @Test
    @DisplayName("Paid leave and present days are never deducted")
    void paidLeaveIsNotDeducted() {
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 3), AttendanceStatus.PAID_LEAVE,
                LocalDate.of(2025, 4, 4), AttendanceStatus.PRESENT);
        PayrollResult r = calc(TestData.fixed30NoOffs(), TestData.basicOnly("30000"), att, null);

        assertEquals(bd("0.00"), r.unpaidDeduction());
        assertEquals(bd("1.00"), r.paidLeaveDays());
        assertEquals(bd("30000.00"), r.netSalary());
    }

    @Test
    @DisplayName("Proration base BASIC uses basic salary instead of gross")
    void prorationOnBasic() {
        PayrollPolicy policy = TestData.policy(PayrollPolicy.PayableDaysMode.FIXED, 30, EnumSet.noneOf(DayOfWeek.class),
                PayrollPolicy.MissingAttendance.PRESENT, PayrollPolicy.ProrationBase.BASIC, false, null);
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 3), AttendanceStatus.ABSENT);

        PayrollResult r = calc(policy, TestData.typicalSalary(), att, null);

        assertEquals(bd("1000.00"), r.unpaidDeduction());          // 30000 / 30 x 1
    }

    @Test
    @DisplayName("WORKING_DAYS basis divides by the month's working days")
    void workingDaysBasis() {
        PayrollPolicy policy = TestData.policy(PayrollPolicy.PayableDaysMode.WORKING_DAYS, 30,
                EnumSet.of(DayOfWeek.SUNDAY), PayrollPolicy.MissingAttendance.PRESENT,
                PayrollPolicy.ProrationBase.GROSS, false, null);
        Map<LocalDate, AttendanceStatus> att = Map.of(LocalDate.of(2025, 4, 3), AttendanceStatus.ABSENT);

        PayrollResult r = calc(policy, TestData.basicOnly("26000"), att, null);

        assertEquals(26, r.payableDaysBasis());                    // April 2025 has 26 non-Sunday days
        assertEquals(bd("1000.00"), r.unpaidDeduction());
    }

    @Test
    @DisplayName("Optional policy: provident fund as a percentage of basic")
    void pfPercentOfBasicPolicy() {
        PayrollPolicy policy = TestData.policy(PayrollPolicy.PayableDaysMode.FIXED, 30, EnumSet.noneOf(DayOfWeek.class),
                PayrollPolicy.MissingAttendance.PRESENT, PayrollPolicy.ProrationBase.GROSS, false, bd("12"));
        PayrollResult r = calc(policy, TestData.typicalSalary(), Map.of(), null);

        assertEquals(bd("3600.00"), r.providentFundDeduction());   // 12% of 30,000
    }

    @Test
    @DisplayName("Missing attendance records follow the configured policy")
    void missingAttendancePolicy() {
        PayrollPolicy absent = TestData.policy(PayrollPolicy.PayableDaysMode.FIXED, 30, EnumSet.noneOf(DayOfWeek.class),
                PayrollPolicy.MissingAttendance.ABSENT, PayrollPolicy.ProrationBase.GROSS, false, null);
        PayrollResult r = calc(absent, TestData.basicOnly("30000"), Map.of(), null);

        assertEquals(bd("30.00"), r.unpaidDays());
        assertEquals(bd("30000.00"), r.unpaidDeduction());
        assertEquals(bd("0.00"), r.netSalary());
    }

    @Test
    @DisplayName("Negative net salary is rejected when not explicitly allowed")
    void negativeNetRejected() {
        InvalidPayrollException ex = assertThrows(InvalidPayrollException.class,
                () -> calc(PayrollPolicy.defaults(), TestData.typicalSalary(), Map.of(), bd("100000")));
        assertTrue(ex.getMessage().contains("negative"));
    }

    @Test
    @DisplayName("Negative net salary is accepted only when the policy allows it")
    void negativeNetAllowedByPolicy() {
        PayrollPolicy policy = TestData.policy(PayrollPolicy.PayableDaysMode.FIXED, 30, EnumSet.noneOf(DayOfWeek.class),
                PayrollPolicy.MissingAttendance.PRESENT, PayrollPolicy.ProrationBase.GROSS, true, null);
        PayrollResult r = calc(policy, TestData.basicOnly("1000"), Map.of(), bd("1500"));

        assertEquals(bd("-500.00"), r.netSalary());
    }

    @Test
    @DisplayName("Negative manual deduction is rejected")
    void negativeManualDeductionRejected() {
        assertThrows(InvalidPayrollException.class,
                () -> calc(PayrollPolicy.defaults(), TestData.typicalSalary(), Map.of(), bd("-1")));
    }

    @Test
    @DisplayName("Days before the joining date count as unpaid")
    void daysBeforeJoiningAreUnpaid() {
        PayrollResult r = new PayrollCalculator(TestData.fixed30NoOffs()).calculate(new PayrollInput(
                APRIL_2025, LocalDate.of(2025, 4, 16), TestData.basicOnly("30000"), Map.of(), null));

        assertEquals(bd("15.00"), r.unpaidDays());            // 1st-15th
        assertEquals(bd("15000.00"), r.unpaidDeduction());
        assertEquals(bd("15000.00"), r.netSalary());
    }

    @Test
    @DisplayName("Unpaid deduction never exceeds the base amount")
    void deductionIsCapped() {
        // FIXED basis 20 but 30 unpaid days -> raw deduction 1.5 x base; must be capped at the base.
        PayrollPolicy policy = TestData.policy(PayrollPolicy.PayableDaysMode.FIXED, 20, EnumSet.noneOf(DayOfWeek.class),
                PayrollPolicy.MissingAttendance.ABSENT, PayrollPolicy.ProrationBase.GROSS, false, null);
        PayrollResult r = calc(policy, TestData.basicOnly("10000"), Map.of(), null);

        assertEquals(bd("10000.00"), r.unpaidDeduction());
        assertEquals(bd("0.00"), r.netSalary());
    }
}
