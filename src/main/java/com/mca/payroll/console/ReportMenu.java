package com.mca.payroll.console;

import com.mca.payroll.dto.EmployeeCountReport;
import com.mca.payroll.dto.MonthlyPayrollSummary;
import com.mca.payroll.dto.SalaryHistoryReport;
import com.mca.payroll.util.DateUtil;

import java.time.YearMonth;
import java.util.List;

/** Module 8 - Reports (HQL aggregates). */
class ReportMenu {

    private final ConsoleIO io;
    private final Services s;

    ReportMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Reports", List.of("Total / active / inactive employees",
                    "Department-wise employees", "Monthly payroll summary", "Employee salary history",
                    "Department-wise payroll expenses", "Employees with unpaid leave (month)",
                    "Pending leave requests", "Payroll records by status"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> counts();
                    case 2 -> departmentEmployees();
                    case 3 -> monthly();
                    case 4 -> history();
                    case 5 -> expenses();
                    case 6 -> unpaid();
                    case 7 -> Views.leaves(io, s.reports.pendingLeaveRequests());
                    case 8 -> byStatus();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    private void counts() {
        EmployeeCountReport r = s.reports.employeeCounts();
        io.println("Total employees   : " + r.total());
        io.println("Active employees  : " + r.active());
        io.println("Inactive employees: " + r.inactive());
    }

    private void departmentEmployees() {
        TableFormatter t = new TableFormatter("Department", "Employees");
        s.reports.employeesByDepartment().forEach(r -> t.row(r.department(), r.employees()));
        io.println(t.render());
    }

    private void monthly() {
        YearMonth month = io.readYearMonth("Month");
        MonthlyPayrollSummary r = s.reports.monthlyPayrollSummary(month);
        io.println("Payroll summary for " + DateUtil.formatPeriod(month));
        io.println("Records           : " + r.records());
        io.println("Total gross       : " + Views.money(r.totalGross()));
        io.println("Total deductions  : " + Views.money(r.totalDeductions()));
        io.println("Total net payable : " + Views.money(r.totalNet()));
    }

    private void history() {
        SalaryHistoryReport r = s.reports.salaryHistory(io.readId("Employee ID: "));
        io.println("Employee: " + r.employee().employeeCode() + " " + r.employee().fullName());
        io.println("Salary revisions:");
        Views.salaryStructures(io, r.structures());
        io.println("Monthly payroll history:");
        Views.payrolls(io, r.payrolls());
    }

    private void expenses() {
        YearMonth month = io.readYearMonth("Month");
        TableFormatter t = new TableFormatter("Department", "Employees paid", "Total gross", "Total net");
        s.reports.departmentPayrollExpenses(month).forEach(r ->
                t.row(r.department(), r.employees(), Views.money(r.totalGross()), Views.money(r.totalNet())));
        io.println("Department-wise payroll expense - " + DateUtil.formatPeriod(month));
        io.println(t.render());
    }

    private void unpaid() {
        YearMonth month = io.readYearMonth("Month");
        TableFormatter t = new TableFormatter("Code", "Employee", "Unpaid leave days");
        s.reports.employeesWithUnpaidLeave(month).forEach(r -> t.row(r.employeeCode(), r.employeeName(), r.unpaidLeaveDays()));
        io.println(t.render());
    }

    private void byStatus() {
        TableFormatter t = new TableFormatter("Status", "Records", "Total net");
        s.reports.payrollByStatus().forEach(r -> t.row(r.status(), r.records(), Views.money(r.totalNet())));
        io.println(t.render());
    }
}
