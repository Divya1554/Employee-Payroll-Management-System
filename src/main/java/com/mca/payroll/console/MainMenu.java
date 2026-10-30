package com.mca.payroll.console;

import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.AuthenticationFailedException;
import com.mca.payroll.util.SecurityContext;

import java.util.List;

/** Sign-in flow and the top-level menu. */
public class MainMenu {

    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final ConsoleIO io;
    private final Services services;

    public MainMenu(ConsoleIO io, Services services) {
        this.io = io;
        this.services = services;
    }

    /** Runs the whole interactive session; returns when the user exits. */
    public void run() {
        io.println("========== EMPLOYEE PAYROLL MANAGEMENT SYSTEM ==========");
        if (!signIn()) {
            return;
        }
        while (true) {
            AuthenticatedUser user = SecurityContext.requireAuthenticated();
            if (user.role() == Role.EMPLOYEE) {
                new SelfServiceMenu(io, services).show();
                if (confirmLogout()) {
                    return;
                }
                continue;
            }
            io.println();
            io.println("Signed in as " + user.username() + " (" + user.role() + ")");
            int choice = io.menu("Employee Payroll Management System", List.of("Department Management",
                    "Employee Management", "Salary Structure Management", "Attendance Management",
                    "Leave Management", "Payroll Processing", "Payslip Management", "Reports", "User Management"));
            switch (choice) {
                case 0 -> {
                    if (confirmLogout()) {
                        return;
                    }
                }
                case 1 -> new DepartmentMenu(io, services).show();
                case 2 -> new EmployeeMenu(io, services).show();
                case 3 -> new SalaryMenu(io, services).show();
                case 4 -> new AttendanceMenu(io, services).show();
                case 5 -> new LeaveMenu(io, services).show();
                case 6 -> new PayrollMenu(io, services).show();
                case 7 -> new PayslipMenu(io, services).show();
                case 8 -> new ReportMenu(io, services).show();
                case 9 -> {
                    if (user.role() == Role.ADMIN) {
                        new UserMenu(io, services).show();
                    } else {
                        io.error("User management is available to ADMIN only.");
                    }
                }
                default -> io.error("Invalid choice.");
            }
        }
    }

    private boolean confirmLogout() {
        if (io.confirm("Sign out and exit?")) {
            services.auth.logout();
            io.println("Goodbye.");
            return true;
        }
        return false;
    }

    private boolean signIn() {
        try {
            if (services.auth.isFirstRun() && !firstRunSetup()) {
                return false;
            }
        } catch (com.mca.payroll.exception.PayrollException e) {
            io.error(e.getMessage());
            return false;
        }
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            String username = io.readRequired("Username: ");
            String password = io.readPassword("Password: ");
            try {
                AuthenticatedUser user = services.auth.login(username, password);
                io.success("Welcome, " + user.username() + " (" + user.role() + ").");
                return true;
            } catch (AuthenticationFailedException e) {
                io.error(e.getMessage() + " (attempt " + attempt + " of " + MAX_LOGIN_ATTEMPTS + ")");
            } catch (com.mca.payroll.exception.PayrollException e) {
                io.error(e.getMessage());
                return false;
            }
        }
        io.error("Too many failed attempts. Exiting.");
        return false;
    }

    /** First run: there are no users yet, so create the initial ADMIN account. */
    private boolean firstRunSetup() {
        io.println();
        io.info("First run: no user accounts exist yet. Create the administrator account.");
        while (true) {
            String username = io.readRequired("Admin username: ");
            String password = io.readPassword("Admin password (min 8 chars, letters and digits): ");
            String again = io.readPassword("Repeat password: ");
            if (!password.equals(again)) {
                io.error("The passwords do not match. Try again.");
                continue;
            }
            try {
                services.auth.createInitialAdmin(username, password);
                io.success("Administrator account created. Please sign in.");
                return true;
            } catch (com.mca.payroll.exception.ValidationException e) {
                io.error(e.getMessage());
            } catch (com.mca.payroll.exception.PayrollException e) {
                io.error(e.getMessage());
                return false;
            }
        }
    }
}
