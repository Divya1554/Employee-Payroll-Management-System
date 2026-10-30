package com.mca.payroll.console;

import com.mca.payroll.dto.DepartmentDto;
import com.mca.payroll.dto.DepartmentRequest;

import java.util.List;

/** Module 1 - Department management. */
class DepartmentMenu {

    private final ConsoleIO io;
    private final Services s;

    DepartmentMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Department Management", List.of("Add department", "View all departments",
                    "Search department by ID", "Search department by name", "Update department", "Delete department"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> add();
                    case 2 -> Views.departments(io, s.departments.getAllDepartments());
                    case 3 -> Views.departments(io, List.of(s.departments.getDepartmentById(io.readId("Department ID: "))));
                    case 4 -> Views.departments(io, s.departments.searchByName(io.readRequired("Name contains: ")));
                    case 5 -> update();
                    case 6 -> delete();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    private void add() {
        DepartmentRequest request = new DepartmentRequest(io.readRequired("Department name: "),
                io.readOptional("Description"), io.readOptional("Location"));
        DepartmentDto created = s.departments.addDepartment(request);
        io.success("Department created with ID " + created.id() + ".");
    }

    private void update() {
        Long id = io.readId("Department ID to update: ");
        DepartmentDto current = s.departments.getDepartmentById(id);
        io.println("Current: " + current.name() + " | " + current.location() + " | " + current.description());
        io.println("Press Enter to keep the current value.");
        String name = io.readLine("Name [" + current.name() + "]: ");
        String description = io.readLine("Description [" + nz(current.description()) + "]: ");
        String location = io.readLine("Location [" + nz(current.location()) + "]: ");
        DepartmentDto updated = s.departments.updateDepartment(id, new DepartmentRequest(
                name.isEmpty() ? current.name() : name,
                description.isEmpty() ? current.description() : description,
                location.isEmpty() ? current.location() : location));
        io.success("Department " + updated.id() + " updated.");
    }

    private void delete() {
        Long id = io.readId("Department ID to delete: ");
        DepartmentDto current = s.departments.getDepartmentById(id);
        if (io.confirm("Delete department '" + current.name() + "'?")) {
            s.departments.deleteDepartment(id);
            io.success("Department deleted.");
        } else {
            io.info("Cancelled.");
        }
    }

    private static String nz(String text) {
        return text == null ? "" : text;
    }
}
