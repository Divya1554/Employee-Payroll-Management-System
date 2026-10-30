package com.mca.payroll.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for BigDecimal money values. Money is never stored or calculated with float/double. */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** Null-safe: a missing amount counts as zero. */
    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal round(BigDecimal value, int scale, RoundingMode mode) {
        return nz(value).setScale(scale, mode);
    }

    /**
     * Formats an amount with Indian digit grouping and two decimals, e.g. 1234567.5 -> "12,34,567.50".
     */
    public static String format(BigDecimal value) {
        BigDecimal v = nz(value).setScale(2, RoundingMode.HALF_UP);
        boolean negative = v.signum() < 0;
        String plain = v.abs().toPlainString();
        int dot = plain.indexOf('.');
        String intPart = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? ".00" : plain.substring(dot);

        StringBuilder sb = new StringBuilder();
        int len = intPart.length();
        if (len <= 3) {
            sb.append(intPart);
        } else {
            String lastThree = intPart.substring(len - 3);
            String rest = intPart.substring(0, len - 3);
            int firstGroup = rest.length() % 2;
            if (firstGroup > 0) {
                sb.append(rest, 0, firstGroup);
            }
            for (int i = firstGroup; i < rest.length(); i += 2) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(rest, i, i + 2);
            }
            sb.append(',').append(lastThree);
        }
        sb.append(fraction);
        return negative ? "-" + sb : sb.toString();
    }
}
