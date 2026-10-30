package com.mca.payroll.console;

import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.EmployeeFilter;
import com.mca.payroll.dto.EmployeeRequest;
import com.mca.payroll.dto.PageResult;
import com.mca.payroll.entity.EmploymentStatus;

import java.time.LocalDate;
import java.util.List;

/** Module 2 - Employee management. */
class EmployeeMenu {

    private static final int PAGE_SIZE = 10;

    private final ConsoleIO io;
    private final Services s;

    EmployeeMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Employee Management", List.of("Add employee", "View all employees (paged)",
                    "Search employee by ID", "Search employee by name", "Update employee", "Deactivate employee",
                    "Reactivate employee", "View employees by department", "View employees by designation",
                    "Search employees by joining date", "Advanced search (multiple filters)",
                    "Delete employee (only if no history)"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> add();
                    case 2 -> paged();
                    case 3 -> Views.employeeDetail(io, s.employees.getEmployeeById(io.readId("Employee ID: ")));
                    case 4 -> Views.employees(io, s.employees.searchByName(io.readRequired("Name contains: ")));
                    case 5 -> update();
                    case 6 -> deactivate();
                    case 7 -> reactivate();
                    case 8 -> Views.employees(io, s.employees.getByDepartment(io.readId("Department ID: ")));
                    case 9 -> Views.employees(io, s.employees.getByDesignation(io.readRequired("Designation: ")));
                    case 10 -> byJoiningDate();
                    case 11 -> advanced();
                    case 12 -> delete();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    private void add() {
        Views.departments(io, s.departments.getAllDepartments());
        EmployeeRequest request = new EmployeeRequest(
                io.readRequired("Employee code (e.g. EMP010): "),
                io.readRequired("First name: "), io.readRequired("Last name: "),
                io.readRequired("Email: "), io.readOptional("Phone"),
                io.readDate("Date of birth"), io.readDate("Date of joining"),
                io.readRequired("Designation: "), io.readId("Department ID: "));
        EmployeeDto created = s.employees.addEmployee(request);
        io.success("Employee " + created.employeeCode() + " created with ID " + created.id() + ".");
    }

    private void paged() {
        int page = 0;
        while (true) {
            PageResult<EmployeeDto> result = s.employees.getEmployeesPage(page, PAGE_SIZE);
            Views.employees(io, result.items());
            io.println("Page " + (result.page() + 1) + " of " + Math.max(result.totalPages(), 1)
                    + "  (total " + result.totalItems() + ")");
            String cmd = io.readLine("[n]ext, [p]revious, [q]uit: ").toLowerCase();
            if (cmd.equals("n") && result.hasNext()) {
                page++;
            } else if (cmd.equals("p") && result.hasPrevious()) {
                page--;
            } else if (cmd.equals("q")) {
                return;
            } else {
                io.info("No such page.");
            }
        }
    }

    private void update() {
        Long id = io.readId("Employee ID to update: ");
        EmployeeDto e = s.employees.getEmployeeById(id);
        Views.employeeDetail(io, e);
        io.println("Press Enter to keep the current value (the employee code cannot be changed).");
        String first = io.readLine("First name [" + e.firstName() + "]: ");
        String last = io.readLine("Last name [" + e.lastName() + "]: ");
        String email = io.readLine("Email [" + e.email() + "]: ");
        String phone = io.readLine("Phone [" + (e.phone() == null ? "" : e.phone()) + "]: ");
        String dob = io.readLine("Date of birth [" + e.dateOfBirth() + "]: ");
        String doj = io.readLine("Date of joining [" + e.dateOfJoining() + "]: ");
        String designation = io.readLine("Designation [" + e.designation() + "]: ");
        String dept = io.readLine("Department ID [" + e.departmentId() + "]: ");

        EmployeeRequest request = new EmployeeRequest(e.employeeCode(),
                first.isEmpty() ? e.firstName() : first,
                last.isEmpty() ? e.lastName() : last,
                email.isEmpty() ? e.email() : email,
                phone.isEmpty() ? e.phone() : phone,
                dob.isEmpty() ? e.dateOfBirth() : com.mca.payroll.util.DateUtil.parseDate(dob),
                doj.isEmpty() ? e.dateOfJoining() : com.mca.payroll.util.DateUtil.parseDate(doj),
                designation.isEmpty() ? e.designation() : designation,
                dept.isEmpty() ? e.departmentId() : parseId(dept));
        s.employees.updateEmployee(id, request);
        io.success("Employee updated.");
    }

    private void deactivate() {
        Long id = io.readId("Employee ID to deactivate: ");
        EmployeeDto e = s.employees.getEmployeeById(id);
        if (io.confirm("Deactivate " + e.employeeCode() + " " + e.fullName() + "? (history is kept)")) {
            s.employees.deactivateEmployee(id);
            io.success("Employee deactivated.");
        } else {
            io.info("Cancelled.");
        }
    }

    private void reactivate() {
        s.employees.reactivateEmployee(io.readId("Employee ID to reactivate: "));
        io.success("Employee reactivated.");
    }

    private void byJoiningDate() {
        LocalDate from = io.readDate("Joined from");
        LocalDate to = io.readDate("Joined to");
        Views.employees(io, s.employees.getByJoiningDate(from, to));
    }

    private void advanced() {
        io.println("Leave a filter empty to ignore it.");
        String name = io.readLine("Name contains: ");
        String dept = io.readLine("Department ID: ");
        String designation = io.readLine("Designation: ");
        String status = io.readLine("Status (ACTIVE/INACTIVE): ").toUpperCase();
        String from = io.readLine("Joined from [yyyy-MM-dd]: ");
        String to = io.readLine("Joined to [yyyy-MM-dd]: ");
        EmployeeFilter filter = new EmployeeFilter(
                name.isEmpty() ? null : name,
                dept.isEmpty() ? null : parseId(dept),
                designation.isEmpty() ? null : designation,
                status.isEmpty() ? null : parseStatus(status),
                from.isEmpty() ? null : com.mca.payroll.util.DateUtil.parseDate(from),
                to.isEmpty() ? null : com.mca.payroll.util.DateUtil.parseDate(to));
        Views.employees(io, s.employees.search(filter));
    }

    private void delete() {
        Long id = io.readId("Employee ID to delete permanently: ");
        EmployeeDto e = s.employees.getEmployeeById(id);
        if (io.confirm("PERMANENTLY delete " + e.employeeCode() + " " + e.fullName() + "?")) {
            s.employees.deleteEmployee(id);
            io.success("Employee deleted.");
        } else {
            io.info("Cancelled.");
        }
    }

    private static Long parseId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new com.mca.payroll.exception.ValidationException("'" + text + "' is not a valid ID.");
        }
    }

    private static EmploymentStatus parseStatus(String text) {
        try {
            return EmploymentStatus.valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new com.mca.payroll.exception.ValidationException("Status must be ACTIVE or INACTIVE.");
        }
    }
}
