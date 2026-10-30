package com.mca.payroll.integration;

import com.mca.payroll.dto.AttendanceSummaryDto;
import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.LeaveRequestDto;
import com.mca.payroll.entity.Attendance;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.LeaveStatus;
import com.mca.payroll.entity.LeaveType;
import com.mca.payroll.exception.DatabaseOperationException;
import com.mca.payroll.exception.DuplicateRecordException;
import com.mca.payroll.exception.InvalidLeaveRequestException;
import com.mca.payroll.util.HibernateUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceLeaveIntegrationTest extends IntegrationTestBase {

    private EmployeeDto newEmployee() {
        return employee("EMP001", department("IT").id());
    }

    private LocalDate futureMonday() {
        LocalDate d = LocalDate.now().plusDays(10);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY || d.getYear() != d.plusDays(14).getYear()) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    @DisplayName("Duplicate attendance for the same employee and date is prevented by the service")
    void duplicateAttendanceRejectedByService() {
        EmployeeDto e = newEmployee();
        LocalDate day = LocalDate.of(2025, 4, 2);
        attendance.recordAttendance(e.id(), day, AttendanceStatus.PRESENT, null);

        assertThrows(DuplicateRecordException.class,
                () -> attendance.recordAttendance(e.id(), day, AttendanceStatus.ABSENT, null));
    }

    @Test
    @DisplayName("The database unique constraint also rejects duplicates (service bypassed)")
    void duplicateAttendanceRejectedByDatabase() {
        EmployeeDto e = newEmployee();
        LocalDate day = LocalDate.of(2025, 4, 2);
        attendance.recordAttendance(e.id(), day, AttendanceStatus.PRESENT, null);

        DatabaseOperationException ex = assertThrows(DatabaseOperationException.class, () ->
                HibernateUtil.inTransaction(session -> {
                    Employee ref = session.getReference(Employee.class, e.id());
                    session.persist(new Attendance(ref, day, AttendanceStatus.ABSENT, "direct"));
                    session.flush();
                    return null;
                }));
        assertTrue(ex.isConstraintViolation());
    }

    @Test
    @DisplayName("Monthly summary counts statuses and identifies absent days")
    void monthlySummary() {
        EmployeeDto e = newEmployee();
        attendance.recordAttendance(e.id(), LocalDate.of(2025, 4, 1), AttendanceStatus.PRESENT, null);
        attendance.recordAttendance(e.id(), LocalDate.of(2025, 4, 2), AttendanceStatus.ABSENT, null);
        attendance.recordAttendance(e.id(), LocalDate.of(2025, 4, 3), AttendanceStatus.HALF_DAY, null);

        AttendanceSummaryDto s = attendance.getMonthlySummary(e.id(), YearMonth.of(2025, 4));
        assertEquals(1, s.presentCount());
        assertEquals(1, s.absentCount());
        assertEquals(1, s.halfDayCount());
        assertEquals(1, attendance.getAbsentDays(e.id(), YearMonth.of(2025, 4)).size());
    }

    @Test
    @DisplayName("Approving paid leave writes PAID_LEAVE attendance for each working day")
    void approvalWritesAttendance() {
        EmployeeDto e = newEmployee();
        LocalDate monday = futureMonday();
        LeaveRequestDto request = leave.submitLeaveRequest(e.id(), LeaveType.CASUAL, monday, monday.plusDays(2), "Family event");
        assertEquals(LeaveStatus.PENDING, request.status());
        assertEquals(3, request.numberOfDays());

        LeaveRequestDto approved = leave.approveLeave(request.id(), "ok");
        assertEquals(LeaveStatus.APPROVED, approved.status());
        long paidLeaveDays = attendance.getAttendanceByEmployee(e.id()).stream()
                .filter(a -> a.status() == AttendanceStatus.PAID_LEAVE).count();
        assertEquals(3, paidLeaveDays);
    }

    @Test
    @DisplayName("Overlapping leave requests are rejected")
    void overlappingLeaveRejected() {
        EmployeeDto e = newEmployee();
        LocalDate monday = futureMonday();
        leave.submitLeaveRequest(e.id(), LeaveType.CASUAL, monday, monday.plusDays(1), "First");

        assertThrows(InvalidLeaveRequestException.class, () ->
                leave.submitLeaveRequest(e.id(), LeaveType.SICK, monday.plusDays(1), monday.plusDays(2), "Overlap"));
    }

    @Test
    @DisplayName("A request exceeding the annual quota is rejected")
    void quotaExceeded() {
        EmployeeDto e = newEmployee();
        LocalDate monday = futureMonday();
        // CASUAL quota defaults to 8 working days; Mon-Sat of two weeks = 12 working days
        assertThrows(InvalidLeaveRequestException.class, () ->
                leave.submitLeaveRequest(e.id(), LeaveType.CASUAL, monday, monday.plusDays(12), "Too long"));
    }

    @Test
    @DisplayName("A request that was already reviewed cannot be reviewed again")
    void cannotReviewTwice() {
        EmployeeDto e = newEmployee();
        LocalDate monday = futureMonday();
        LeaveRequestDto request = leave.submitLeaveRequest(e.id(), LeaveType.CASUAL, monday, monday, "One day");
        leave.rejectLeave(request.id(), "Not possible");
        assertThrows(InvalidLeaveRequestException.class, () -> leave.approveLeave(request.id(), null));
    }
}
