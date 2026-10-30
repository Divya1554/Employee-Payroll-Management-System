package com.mca.payroll.util;

import com.mca.payroll.exception.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/** Date parsing and formatting helpers. Console input uses ISO dates: yyyy-MM-dd and yyyy-MM. */
public final class DateUtil {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateUtil() {
    }

    public static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException("'" + text + "' is not a valid date. Use the format yyyy-MM-dd, e.g. 2025-04-15.");
        }
    }

    public static YearMonth parseYearMonth(String text) {
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException("'" + text + "' is not a valid month. Use the format yyyy-MM, e.g. 2025-04.");
        }
    }

    public static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    public static String formatPeriod(YearMonth period) {
        return monthName(period.getMonthValue()) + " " + period.getYear();
    }

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME.format(value);
    }

    public static String formatDate(LocalDate value) {
        return value == null ? "-" : value.toString();
    }
}
