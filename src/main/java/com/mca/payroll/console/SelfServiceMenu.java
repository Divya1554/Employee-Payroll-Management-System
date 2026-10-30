package com.mca.payroll.console;

import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.util.SecurityContext;

import java.util.List;

/**
 * Menu for the EMPLOYEE role: only the signed-in employee's own data. The service layer enforces the
 * same rule, so this menu is a convenience, not the security boundary.
 */
class SelfServiceMenu {

    private final ConsoleIO io;
    private final Services s;
    private final LeaveMenu leaveMenu;
    private final PayslipMenu payslipMenu;
    private final UserMenu userMenu;

    SelfServiceMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
        this.leaveMenu = new LeaveMenu(io, s);
        this.payslipMenu = new PayslipMenu(io, s);
        this.userMenu = new UserMenu(io, s);
    }

    void show() {
        AuthenticatedUser me = SecurityContext.requireAuthenticated();
        if (me.employeeId() == null) {
            io.error("Your account is not linked to an employee record. Ask an administrator to link it.");
            return;
        }
        Long id = me.employeeId();
        while (true) {
            int choice = io.menu("My Self-Service", List.of("My profile", "My payroll history", "My payslip",
                    "Save my payslip to a file", "My attendance for a month", "Submit leave request",
                    "My leave history", "My leave balance", "My salary structure", "Change my password"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> Views.employeeDetail(io, s.employees.getEmployeeById(id));
                    case 2 -> Views.payrolls(io, s.payroll.getPayrollByEmployee(id));
                    case 3 -> payslipMenu.display(payslipMenu.byEmployeeAndMonth(id));
                    case 4 -> payslipMenu.saveFor(payslipMenu.byEmployeeAndMonth(id));
                    case 5 -> Views.attendance(io, s.attendance.getAttendanceForMonth(id, io.readYearMonth("Month")));
                    case 6 -> leaveMenu.submit(id);
                    case 7 -> Views.leaves(io, s.leave.getLeaveHistory(id));
                    case 8 -> leaveMenu.balances(id);
                    case 9 -> Views.salaryStructures(io, s.salaries.getStructuresByEmployee(id));
                    case 10 -> userMenu.changeOwnPassword();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }
}
