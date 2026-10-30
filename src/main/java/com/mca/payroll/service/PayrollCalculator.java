package com.mca.payroll.service;

import com.mca.payroll.dto.AttendanceSummaryDto;
import com.mca.payroll.dto.PayrollInput;
import com.mca.payroll.dto.PayrollResult;
import com.mca.payroll.entity.SalaryComponents;
import com.mca.payroll.exception.InvalidPayrollException;
import com.mca.payroll.util.MoneyUtil;

import java.math.BigDecimal;

/**
 * The payroll formulas - pure functions of their input, no database, fully unit-testable.
 *
 * <pre>
 *   Gross            = Basic + HRA + Medical + Conveyance + Other allowances
 *   Daily rate       = base / payable-days-basis          (base = GROSS or BASIC, see policy)
 *   Unpaid deduction = base x unpaidDays / basis          (counted once, from attendance only)
 *   Other deductions = Unpaid deduction + manual other deductions
 *   Total deductions = PF + Professional tax + Other deductions
 *   Net              = Gross - Total deductions
 * </pre>
 * Example: gross 30,000, basis 30, 2 unpaid days -> unpaid deduction 2,000 -> net before PF/PT 28,000.
 * Every amount is a BigDecimal rounded with the policy's scale and rounding mode.
 */
public class PayrollCalculator {

    private final PayrollPolicy policy;

    public PayrollCalculator(PayrollPolicy policy) {
        this.policy = policy;
    }

    public PayrollPolicy getPolicy() {
        return policy;
    }

    /** The divisor used to convert a monthly amount to a daily amount. */
    public int payableDaysBasis(java.time.YearMonth period, int workingDays) {
        return switch (policy.getPayableDaysMode()) {
            case CALENDAR_DAYS -> period.lengthOfMonth();
            case WORKING_DAYS -> workingDays;
            case FIXED -> policy.getFixedPayableDays();
        };
    }

    public PayrollResult calculate(PayrollInput input) {
        SalaryComponents c = input.components();
        BigDecimal manual = MoneyUtil.nz(input.manualOtherDeductions());
        if (manual.signum() < 0) {
            throw new InvalidPayrollException("Other deductions must not be negative.");
        }

        AttendanceSummaryDto attendance = AttendanceCalculator.summarize(
                input.period(), input.dateOfJoining(), input.attendance(), policy);

        int basis = payableDaysBasis(input.period(), attendance.workingDays());
        if (basis <= 0) {
            throw new InvalidPayrollException("The payable-day basis for " + input.period()
                    + " is zero - check payroll.weekly.off and payroll.payable.days.mode.");
        }

        BigDecimal basic = round(c.getBasicSalary());
        BigDecimal hra = round(c.getHouseRentAllowance());
        BigDecimal medical = round(c.getMedicalAllowance());
        BigDecimal conveyance = round(c.getConveyanceAllowance());
        BigDecimal otherAllowances = round(c.getOtherAllowances());
        BigDecimal gross = basic.add(hra).add(medical).add(conveyance).add(otherAllowances);

        BigDecimal pf = policy.getPfPercentOfBasic() != null
                ? round(basic.multiply(policy.getPfPercentOfBasic()).divide(new BigDecimal("100"),
                        policy.getScale(), policy.getRoundingMode()))
                : round(c.getProvidentFundDeduction());
        BigDecimal professionalTax = round(c.getProfessionalTax());

        BigDecimal base = policy.getProrationBase() == PayrollPolicy.ProrationBase.BASIC ? basic : gross;
        BigDecimal unpaidDeduction = base.multiply(attendance.unpaidDays())
                .divide(BigDecimal.valueOf(basis), policy.getScale(), policy.getRoundingMode());
        if (unpaidDeduction.compareTo(base) > 0) {
            unpaidDeduction = base;           // never deduct more than the base amount
        }
        unpaidDeduction = round(unpaidDeduction);

        BigDecimal otherDeductions = unpaidDeduction.add(round(manual));
        BigDecimal totalDeductions = pf.add(professionalTax).add(otherDeductions);
        BigDecimal net = gross.subtract(totalDeductions);

        if (net.signum() < 0 && !policy.isAllowNegativeNet()) {
            throw new InvalidPayrollException("Net salary would be negative (" + MoneyUtil.format(net)
                    + "): deductions " + MoneyUtil.format(totalDeductions) + " exceed gross "
                    + MoneyUtil.format(gross) + ". Payroll was not generated.");
        }

        return new PayrollResult(attendance.workingDays(), round(attendance.presentDays()),
                round(attendance.paidLeaveDays()), round(attendance.unpaidDays()), basis,
                basic, hra, medical, conveyance, otherAllowances, gross,
                pf, professionalTax, unpaidDeduction, otherDeductions, totalDeductions, net);
    }

    private BigDecimal round(BigDecimal value) {
        return MoneyUtil.round(value, policy.getScale(), policy.getRoundingMode());
    }
}
