package com.mca.payroll.console;

import com.mca.payroll.dto.AttendanceDto;
import com.mca.payroll.dto.AttendanceSummaryDto;
import com.mca.payroll.dto.DepartmentDto;
import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.LeaveRequestDto;
import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.dto.SalaryStructureDto;
import com.mca.payroll.dto.UserDto;
import com.mca.payroll.util.DateUtil;
import com.mca.payroll.util.MoneyUtil;

import java.util.List;

/** Console renderings of DTO lists and details. */
final class Views {

    private Views() {
    }

    static void departments(ConsoleIO io, List<DepartmentDto> list) {
        if (list.isEmpty()) {
            io.info("No departments found.");
            return;
        }
        TableFormatter t = new TableFormatter("ID", "Name", "Location", "Description");
        list.forEach(d -> t.row(d.id(), d.name(), d.location(), d.description()));
        io.println(t.render());
    }

    static void employees(ConsoleIO io, List<EmployeeDto> list) {
        if (list.isEmpty()) {
            io.info("No employees found.");
            return;
        }
        TableFormatter t = new TableFormatter("ID", "Code", "Name", "Email", "Designation", "Department", "Joined", "Status");
        list.forEach(e -> t.row(e.id(), e.employeeCode(), e.fullName(), e.email(), e.designation(),
                e.departmentName(), e.dateOfJoining(), e.status()));
        io.println(t.render());
        io.println(list.size() + " employee(s).");
    }

    static void employeeDetail(ConsoleIO io, EmployeeDto e) {
        io.println("ID            : " + e.id());
        io.println("Code          : " + e.employeeCode());
        io.println("Name          : " + e.fullName());
        io.println("Email         : " + e.email());
        io.println("Phone         : " + (e.phone() == null ? "-" : e.phone()));
        io.println("Date of birth : " + e.dateOfBirth());
        io.println("Date joined   : " + e.dateOfJoining());
        io.println("Designation   : " + e.designation());
        io.println("Department    : " + e.departmentName());
        io.println("Status        : " + e.status());
        io.println("Created       : " + DateUtil.formatDateTime(e.createdAt()));
        io.println("Updated       : " + DateUtil.formatDateTime(e.updatedAt()));
    }

    static void salaryStructures(ConsoleIO io, List<SalaryStructureDto> list) {
        if (list.isEmpty()) {
            io.info("No salary structures found.");
            return;
        }
        TableFormatter t = new TableFormatter("ID", "Employee", "Basic", "HRA", "Medical", "Conv.", "Other",
                "PF", "Prof.Tax", "Gross", "From", "To");
        list.forEach(s -> t.row(s.id(), s.employeeCode() + " " + s.employeeName(), money(s.basicSalary()),
                money(s.houseRentAllowance()), money(s.medicalAllowance()), money(s.conveyanceAllowance()),
                money(s.otherAllowances()), money(s.providentFundDeduction()), money(s.professionalTax()),
                money(s.grossSalary()), s.effectiveFrom(), s.effectiveTo() == null ? "current" : s.effectiveTo()));
        io.println(t.render());
    }

    static void attendance(ConsoleIO io, List<AttendanceDto> list) {
        if (list.isEmpty()) {
            io.info("No attendance records found.");
            return;
        }
        TableFormatter t = new TableFormatter("ID", "Employee", "Date", "Day", "Status", "Remarks");
        list.forEach(a -> t.row(a.id(), a.employeeCode() + " " + a.employeeName(), a.date(),
                a.date().getDayOfWeek().toString().substring(0, 3), a.status(), a.remarks()));
        io.println(t.render());
    }

    static void attendanceSummary(ConsoleIO io, AttendanceSummaryDto s) {
        io.println("Month               : " + DateUtil.formatPeriod(s.period()));
        io.println("Working days        : " + s.workingDays());
        io.println("Present (records)   : " + s.presentCount());
        io.println("Half days           : " + s.halfDayCount());
        io.println("Paid leave          : " + s.paidLeaveCount());
        io.println("Unpaid leave        : " + s.unpaidLeaveCount());
        io.println("Absent              : " + s.absentCount());
        io.println("Days before joining : " + s.notEmployedDays());
        io.println("Unrecorded days     : " + s.unrecordedDays() + " (treated per payroll.missing.attendance)");
        io.println("-> Present days     : " + s.presentDays().toPlainString());
        io.println("-> Paid leave days  : " + s.paidLeaveDays().toPlainString());
        io.println("-> Unpaid days      : " + s.unpaidDays().toPlainString());
        if (!s.absentDates().isEmpty()) {
            io.println("Absent dates        : " + s.absentDates());
        }
    }

    static void leaves(ConsoleIO io, List<LeaveRequestDto> list) {
        if (list.isEmpty()) {
            io.info("No leave requests found.");
            return;
        }
        TableFormatter t = new TableFormatter("ID", "Employee", "Type", "From", "To", "Days", "Status", "Reason", "Reviewed by");
        list.forEach(l -> t.row(l.id(), l.employeeCode() + " " + l.employeeName(), l.leaveType(), l.startDate(),
                l.endDate(), l.numberOfDays(), l.status(), l.reason(), l.reviewedBy()));
        io.println(t.render());
    }

    static void payrolls(ConsoleIO io, List<PayrollDto> list) {
        if (list.isEmpty()) {
            io.info("No payroll records found.");
            return;
        }
        TableFormatter t = new TableFormatter("ID", "Employee", "Month", "Work", "Unpaid", "Gross", "Deductions", "Net", "Status");
        list.forEach(p -> t.row(p.id(), p.employeeCode() + " " + p.employeeName(), p.year() + "-" + String.format("%02d", p.month()),
                p.workingDays(), p.unpaidLeaveDays().toPlainString(), money(p.grossSalary()),
                money(p.totalDeductions()), money(p.netSalary()), p.status()));
        io.println(t.render());
    }

    static void users(ConsoleIO io, List<UserDto> list) {
        TableFormatter t = new TableFormatter("ID", "Username", "Role", "Active", "Employee", "Last login");
        list.forEach(u -> t.row(u.id(), u.username(), u.role(), u.active() ? "yes" : "no", u.employeeCode(),
                DateUtil.formatDateTime(u.lastLoginAt())));
        io.println(t.render());
    }

    static String money(java.math.BigDecimal amount) {
        return MoneyUtil.format(amount);
    }
}
