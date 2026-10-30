package com.mca.payroll.unit;

import com.mca.payroll.entity.SalaryComponents;
import com.mca.payroll.service.PayrollPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** Shared helpers for unit tests. */
final class TestData {

    private TestData() {
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** Basic 30,000 + HRA 12,000 + medical 1,250 + conveyance 1,600 + other 500 = gross 45,350; PF 3,600; PT 200. */
    static SalaryComponents typicalSalary() {
        return new SalaryComponents(bd("30000"), bd("12000"), bd("1250"), bd("1600"), bd("500"),
                bd("3600"), bd("200"));
    }

    /** Only a basic salary - used for the 30,000 / 30 days / 2 unpaid days example. */
    static SalaryComponents basicOnly(String basic) {
        return new SalaryComponents(bd(basic), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    static PayrollPolicy policy(PayrollPolicy.PayableDaysMode mode, int fixedDays, Set<DayOfWeek> weeklyOffs,
                                PayrollPolicy.MissingAttendance missing, PayrollPolicy.ProrationBase base,
                                boolean allowNegative, BigDecimal pfPercent) {
        return new PayrollPolicy(mode, fixedDays, weeklyOffs, missing, bd("0.5"), base, 2,
                RoundingMode.HALF_UP, allowNegative, pfPercent);
    }

    /** 30 payable days, no weekly offs, missing attendance = PRESENT, proration on gross. */
    static PayrollPolicy fixed30NoOffs() {
        return policy(PayrollPolicy.PayableDaysMode.FIXED, 30, EnumSet.noneOf(DayOfWeek.class),
                PayrollPolicy.MissingAttendance.PRESENT, PayrollPolicy.ProrationBase.GROSS, false, null);
    }
}
