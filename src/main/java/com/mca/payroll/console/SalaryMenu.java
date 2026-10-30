package com.mca.payroll.console;

import com.mca.payroll.dto.SalaryRequest;
import com.mca.payroll.dto.SalaryStructureDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Module 3 - Salary structure management. */
class SalaryMenu {

    private final ConsoleIO io;
    private final Services s;

    SalaryMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Salary Structure Management", List.of(
                    "Create salary structure / revision for an employee", "View all salary structures",
                    "View salary structures of an employee", "View current salary structure of an employee",
                    "Update (correct) a salary structure", "Assign a copy of a structure to an employee"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> create();
                    case 2 -> Views.salaryStructures(io, s.salaries.getAllStructures());
                    case 3 -> Views.salaryStructures(io, s.salaries.getStructuresByEmployee(io.readId("Employee ID: ")));
                    case 4 -> Views.salaryStructures(io, List.of(s.salaries.getCurrentStructure(io.readId("Employee ID: "))));
                    case 5 -> update();
                    case 6 -> assign();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    private SalaryRequest readAmounts(LocalDate effectiveFrom, SalaryStructureDto d) {
        io.println("Enter monthly amounts (use 0 where not applicable)."
                + (d == null ? "" : " Press Enter to keep the value in brackets."));
        return new SalaryRequest(
                io.readAmount("Basic salary", d == null ? null : d.basicSalary()),
                io.readAmount("House rent allowance", d == null ? BigDecimal.ZERO : d.houseRentAllowance()),
                io.readAmount("Medical allowance", d == null ? BigDecimal.ZERO : d.medicalAllowance()),
                io.readAmount("Conveyance allowance", d == null ? BigDecimal.ZERO : d.conveyanceAllowance()),
                io.readAmount("Other allowances", d == null ? BigDecimal.ZERO : d.otherAllowances()),
                io.readAmount("Provident fund deduction", d == null ? BigDecimal.ZERO : d.providentFundDeduction()),
                io.readAmount("Professional tax", d == null ? BigDecimal.ZERO : d.professionalTax()),
                effectiveFrom);
    }

    private void create() {
        Long employeeId = io.readId("Employee ID: ");
        LocalDate from = io.readDate("Effective from");
        SalaryStructureDto created = s.salaries.createStructure(employeeId, readAmounts(from, null));
        io.success("Salary structure " + created.id() + " created. Gross salary: " + Views.money(created.grossSalary()));
    }

    private void update() {
        Long id = io.readId("Salary structure ID to correct: ");
        SalaryStructureDto current = s.salaries.getAllStructures().stream()
                .filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (current == null) {
            io.error("Salary structure " + id + " was not found.");
            return;
        }
        Views.salaryStructures(io, List.of(current));
        SalaryStructureDto updated = s.salaries.updateStructure(id, readAmounts(current.effectiveFrom(), current));
        io.success("Salary structure updated. Gross salary: " + Views.money(updated.grossSalary())
                + ". (Finalized payroll is NOT affected.)");
    }

    private void assign() {
        Long source = io.readId("Source salary structure ID: ");
        Long employeeId = io.readId("Assign to employee ID: ");
        LocalDate from = io.readDate("Effective from");
        SalaryStructureDto created = s.salaries.assignStructure(source, employeeId, from);
        io.success("Structure " + created.id() + " assigned to " + created.employeeCode() + ".");
    }
}
