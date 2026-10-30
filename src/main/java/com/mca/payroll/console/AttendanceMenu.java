package com.mca.payroll.console;

import com.mca.payroll.dto.AttendanceDto;
import com.mca.payroll.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Module 4 - Attendance management. */
class AttendanceMenu {

    private final ConsoleIO io;
    private final Services s;

    AttendanceMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Attendance Management", List.of("Record attendance", "View attendance by employee",
                    "View attendance for a month", "Update attendance", "Monthly attendance summary",
                    "Identify absent days", "Record approved leave day", "Record attendance for a whole month (bulk)"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> record();
                    case 2 -> Views.attendance(io, s.attendance.getAttendanceByEmployee(io.readId("Employee ID: ")));
                    case 3 -> Views.attendance(io, s.attendance.getAttendanceForMonth(
                            io.readId("Employee ID: "), io.readYearMonth("Month")));
                    case 4 -> update();
                    case 5 -> Views.attendanceSummary(io, s.attendance.getMonthlySummary(
                            io.readId("Employee ID: "), io.readYearMonth("Month")));
                    case 6 -> Views.attendance(io, s.attendance.getAbsentDays(
                            io.readId("Employee ID: "), io.readYearMonth("Month")));
                    case 7 -> approvedLeave();
                    case 8 -> bulk();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    private void record() {
        Long employeeId = io.readId("Employee ID: ");
        LocalDate date = io.readDate("Date");
        AttendanceStatus status = io.readEnum("Status", AttendanceStatus.class);
        AttendanceDto saved = s.attendance.recordAttendance(employeeId, date, status, io.readOptional("Remarks"));
        io.success("Attendance recorded (ID " + saved.id() + ").");
    }

    private void update() {
        Long id = io.readId("Attendance record ID: ");
        AttendanceStatus status = io.readEnum("New status", AttendanceStatus.class);
        s.attendance.updateAttendance(id, status, io.readOptional("Remarks"));
        io.success("Attendance updated.");
    }

    private void approvedLeave() {
        Long employeeId = io.readId("Employee ID: ");
        LocalDate date = io.readDate("Leave date");
        boolean paid = io.confirm("Is it PAID leave? (n = unpaid)");
        s.attendance.recordApprovedLeave(employeeId, date, paid, io.readOptional("Remarks"));
        io.success("Approved leave recorded as " + (paid ? "PAID_LEAVE" : "UNPAID_LEAVE") + ".");
    }

    /** Marks every working day of a month that has no record yet with one status (existing records are kept). */
    private void bulk() {
        Long employeeId = io.readId("Employee ID: ");
        YearMonth month = io.readYearMonth("Month");
        AttendanceStatus status = io.readEnum("Status for every day without a record", AttendanceStatus.class);
        int created = 0;
        int skipped = 0;
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY || d.isAfter(LocalDate.now())) {
                continue;
            }
            try {
                s.attendance.recordAttendance(employeeId, d, status, "Bulk entry");
                created++;
            } catch (com.mca.payroll.exception.DuplicateRecordException | com.mca.payroll.exception.ValidationException e) {
                skipped++;
            }
        }
        io.success(created + " record(s) created, " + skipped + " skipped (already recorded or before joining).");
    }
}
