package com.mca.payroll.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Working-day arithmetic. A working day is any day that is not a configured weekly off. */
public final class WorkingCalendar {

    private WorkingCalendar() {
    }

    public static boolean isWorkingDay(LocalDate date, Set<DayOfWeek> weeklyOffs) {
        return !weeklyOffs.contains(date.getDayOfWeek());
    }

    /** Working days between two dates, both inclusive, in date order. */
    public static List<LocalDate> workingDays(LocalDate from, LocalDate to, Set<DayOfWeek> weeklyOffs) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkingDay(d, weeklyOffs)) {
                days.add(d);
            }
        }
        return days;
    }

    public static int countWorkingDays(LocalDate from, LocalDate to, Set<DayOfWeek> weeklyOffs) {
        return workingDays(from, to, weeklyOffs).size();
    }
}
