package com.mca.payroll.service;

import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.AppConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Configurable payroll rules. Nothing here is a statutory rule - every value comes from
 * application.properties so an institution can change the policy without touching code.
 *
 * <p><b>Payroll period:</b> one calendar month.<br>
 * <b>Payable-day basis</b> (divisor of the daily rate): CALENDAR_DAYS, WORKING_DAYS or FIXED.</p>
 */
public final class PayrollPolicy {

    public enum PayableDaysMode { CALENDAR_DAYS, WORKING_DAYS, FIXED }

    public enum MissingAttendance { PRESENT, ABSENT }

    public enum ProrationBase { GROSS, BASIC }

    private final PayableDaysMode payableDaysMode;
    private final int fixedPayableDays;
    private final Set<DayOfWeek> weeklyOffs;
    private final MissingAttendance missingAttendance;
    private final BigDecimal halfDayUnpaidFraction;
    private final ProrationBase prorationBase;
    private final int scale;
    private final RoundingMode roundingMode;
    private final boolean allowNegativeNet;
    private final BigDecimal pfPercentOfBasic;

    public PayrollPolicy(PayableDaysMode payableDaysMode, int fixedPayableDays, Set<DayOfWeek> weeklyOffs,
                         MissingAttendance missingAttendance, BigDecimal halfDayUnpaidFraction,
                         ProrationBase prorationBase, int scale, RoundingMode roundingMode,
                         boolean allowNegativeNet, BigDecimal pfPercentOfBasic) {
        if (fixedPayableDays <= 0) {
            throw new ValidationException("payroll.payable.days.fixed must be greater than zero.");
        }
        if (halfDayUnpaidFraction.signum() < 0 || halfDayUnpaidFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException("payroll.halfday.unpaid.fraction must be between 0 and 1.");
        }
        if (pfPercentOfBasic != null && (pfPercentOfBasic.signum() < 0
                || pfPercentOfBasic.compareTo(new BigDecimal("100")) > 0)) {
            throw new ValidationException("payroll.pf.percent.of.basic must be between 0 and 100.");
        }
        this.payableDaysMode = payableDaysMode;
        this.fixedPayableDays = fixedPayableDays;
        this.weeklyOffs = weeklyOffs.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weeklyOffs);
        this.missingAttendance = missingAttendance;
        this.halfDayUnpaidFraction = halfDayUnpaidFraction;
        this.prorationBase = prorationBase;
        this.scale = scale;
        this.roundingMode = roundingMode;
        this.allowNegativeNet = allowNegativeNet;
        this.pfPercentOfBasic = pfPercentOfBasic;
    }

    /** Policy used by the unit tests and as documentation of the defaults. */
    public static PayrollPolicy defaults() {
        return new PayrollPolicy(PayableDaysMode.CALENDAR_DAYS, 30, EnumSet.of(DayOfWeek.SUNDAY),
                MissingAttendance.PRESENT, new BigDecimal("0.5"), ProrationBase.GROSS, 2,
                RoundingMode.HALF_UP, false, null);
    }

    /** Reads the policy from application.properties. */
    public static PayrollPolicy fromConfig(AppConfig config) {
        try {
            Set<DayOfWeek> offs = EnumSet.noneOf(DayOfWeek.class);
            for (String day : config.getList("payroll.weekly.off")) {
                offs.add(DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)));
            }
            return new PayrollPolicy(
                    PayableDaysMode.valueOf(config.get("payroll.payable.days.mode", "CALENDAR_DAYS").toUpperCase(Locale.ROOT)),
                    config.getInt("payroll.payable.days.fixed", 30),
                    offs,
                    MissingAttendance.valueOf(config.get("payroll.missing.attendance", "PRESENT").toUpperCase(Locale.ROOT)),
                    config.getBigDecimal("payroll.halfday.unpaid.fraction") == null
                            ? new BigDecimal("0.5") : config.getBigDecimal("payroll.halfday.unpaid.fraction"),
                    ProrationBase.valueOf(config.get("payroll.proration.base", "GROSS").toUpperCase(Locale.ROOT)),
                    config.getInt("payroll.rounding.scale", 2),
                    RoundingMode.valueOf(config.get("payroll.rounding.mode", "HALF_UP").toUpperCase(Locale.ROOT)),
                    config.getBoolean("payroll.allow.negative.net", false),
                    config.getBigDecimal("payroll.pf.percent.of.basic"));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid payroll policy in application.properties: " + ex.getMessage());
        }
    }

    public PayableDaysMode getPayableDaysMode() { return payableDaysMode; }
    public int getFixedPayableDays() { return fixedPayableDays; }
    public Set<DayOfWeek> getWeeklyOffs() { return weeklyOffs; }
    public MissingAttendance getMissingAttendance() { return missingAttendance; }
    public BigDecimal getHalfDayUnpaidFraction() { return halfDayUnpaidFraction; }
    public ProrationBase getProrationBase() { return prorationBase; }
    public int getScale() { return scale; }
    public RoundingMode getRoundingMode() { return roundingMode; }
    public boolean isAllowNegativeNet() { return allowNegativeNet; }
    public BigDecimal getPfPercentOfBasic() { return pfPercentOfBasic; }
}
