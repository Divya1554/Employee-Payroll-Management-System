package com.mca.payroll.console;

import com.mca.payroll.entity.Role;

import java.util.List;

/** Module 9 - User management (ADMIN). */
class UserMenu {

    private final ConsoleIO io;
    private final Services s;

    UserMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("User Management", List.of("Create user", "List users", "Deactivate user",
                    "Activate user", "Reset a user's password", "Change my password"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> create();
                    case 2 -> Views.users(io, s.users.listUsers());
                    case 3 -> {
                        s.users.setActive(io.readId("User ID: "), false);
                        io.success("User deactivated.");
                    }
                    case 4 -> {
                        s.users.setActive(io.readId("User ID: "), true);
                        io.success("User activated.");
                    }
                    case 5 -> resetPassword();
                    case 6 -> changeOwnPassword();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    void changeOwnPassword() {
        String current = io.readPassword("Current password: ");
        String next = io.readPassword("New password: ");
        String again = io.readPassword("Repeat new password: ");
        if (!next.equals(again)) {
            io.error("The two new passwords do not match.");
            return;
        }
        s.users.changeOwnPassword(current, next);
        io.success("Password changed.");
    }

    private void create() {
        String username = io.readRequired("Username: ");
        Role role = io.readEnum("Role", Role.class);
        Long employeeId = null;
        if (role == Role.EMPLOYEE || io.confirm("Link this account to an employee?")) {
            employeeId = io.readId("Employee ID to link: ");
        }
        String password = io.readPassword("Password: ");
        String again = io.readPassword("Repeat password: ");
        if (!password.equals(again)) {
            io.error("The passwords do not match.");
            return;
        }
        s.users.createUser(username, password, role, employeeId);
        io.success("User '" + username + "' created.");
    }

    private void resetPassword() {
        Long id = io.readId("User ID: ");
        String password = io.readPassword("New password: ");
        s.users.resetPassword(id, password);
        io.success("Password reset.");
    }
}
