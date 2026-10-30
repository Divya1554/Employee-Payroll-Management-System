package com.mca.payroll.unit;

import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.LeaveRequestDao;
import com.mca.payroll.dao.PayrollDao;
import com.mca.payroll.entity.LeaveType;
import com.mca.payroll.exception.InvalidLeaveRequestException;
import com.mca.payroll.service.LeaveService;
import com.mca.payroll.service.PayrollPolicy;
import com.mca.payroll.util.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for leave date validation and quota configuration (no database). */
class LeaveValidationTest {

    private LeaveService service;
    private final LocalDate joined = LocalDate.of(2020, 1, 1);

    @BeforeEach
    void setUp() {
        Properties p = new Properties();
        p.setProperty("leave.quota.ANNUAL", "18");
        p.setProperty("leave.quota.SICK", "10");
        p.setProperty("leave.max.backdate.days", "30");
        p.setProperty("leave.max.request.days", "20");
        service = new LeaveService(new LeaveRequestDao(), new EmployeeDao(), new PayrollDao(), null,
                PayrollPolicy.defaults(), AppConfig.of(p));
    }

    /** The next Monday that is at least 7 days ahead, inside the current year when possible. */
    private LocalDate futureMonday() {
        LocalDate d = LocalDate.now().plusDays(7);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    @DisplayName("Counts working days only (Sundays are weekly offs)")
    void countsWorkingDaysOnly() {
        LocalDate monday = futureMonday();
        if (monday.getYear() != monday.plusDays(6).getYear()) {
            monday = monday.minusDays(14);               // avoid the year-boundary rule for this test
        }
        assertEquals(6, service.validateRange(monday, monday.plusDays(6), joined));   // Mon..Sun = 6 working days
    }

    @Test
    @DisplayName("End date before start date is rejected")
    void endBeforeStartRejected() {
        LocalDate d = LocalDate.now().plusDays(5);
        assertThrows(InvalidLeaveRequestException.class, () -> service.validateRange(d, d.minusDays(1), joined));
    }

    @Test
    @DisplayName("Leave starting before the joining date is rejected")
    void beforeJoiningRejected() {
        assertThrows(InvalidLeaveRequestException.class,
                () -> service.validateRange(joined.minusDays(3), joined.plusDays(1), joined));
    }

    @Test
    @DisplayName("Leave too far in the past is rejected")
    void tooFarInPastRejected() {
        LocalDate old = LocalDate.now().minusDays(120);
        assertThrows(InvalidLeaveRequestException.class, () -> service.validateRange(old, old.plusDays(1), joined));
    }

    @Test
    @DisplayName("A request spanning two calendar years is rejected")
    void crossYearRejected() {
        LocalDate start = LocalDate.of(LocalDate.now().getYear(), 12, 30);
        LocalDate end = start.plusDays(5);
        assertThrows(InvalidLeaveRequestException.class, () -> service.validateRange(start, end, joined));
    }

    @Test
    @DisplayName("A request containing only a weekly off is rejected")
    void onlyWeeklyOffRejected() {
        LocalDate sunday = futureMonday().plusDays(6);
        assertThrows(InvalidLeaveRequestException.class, () -> service.validateRange(sunday, sunday, joined));
    }

    @Test
    @DisplayName("A request longer than the configured maximum is rejected")
    void tooLongRejected() {
        LocalDate start = LocalDate.of(LocalDate.now().getYear(), 12, 31).minusDays(60);
        if (start.isBefore(LocalDate.now().minusDays(29))) {
            start = LocalDate.now();                      // keep inside the back-date window
        }
        LocalDate s = start;
        LocalDate e = LocalDate.of(s.getYear(), 12, 31);
        if (com.mca.payroll.util.WorkingCalendar.countWorkingDays(s, e, PayrollPolicy.defaults().getWeeklyOffs()) > 20) {
            assertThrows(InvalidLeaveRequestException.class, () -> service.validateRange(s, e, joined));
        }
    }

    @Test
    @DisplayName("Quotas come from configuration; unpaid leave is unlimited")
    void quotas() {
        assertEquals(18, service.quotaFor(LeaveType.ANNUAL));
        assertEquals(10, service.quotaFor(LeaveType.SICK));
        assertEquals(0, service.quotaFor(LeaveType.CASUAL));   // not configured in this test -> 0
        assertNull(service.quotaFor(LeaveType.UNPAID));
    }
}
