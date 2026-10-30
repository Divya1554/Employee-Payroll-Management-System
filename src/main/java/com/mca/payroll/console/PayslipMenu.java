package com.mca.payroll.console;

import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.dto.PayslipDto;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

/** Module 7 - Payslip management (also used by self-service). */
class PayslipMenu {

    private final ConsoleIO io;
    private final Services s;

    PayslipMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Payslip Management", List.of("Display payslip by payroll ID",
                    "Display payslip by employee and month", "Save payslip to a text file"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> display(s.payslips.buildPayslip(io.readId("Payroll ID: ")));
                    case 2 -> display(byEmployeeAndMonth(io.readId("Employee ID: ")));
                    case 3 -> save();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    PayslipDto byEmployeeAndMonth(Long employeeId) {
        YearMonth month = io.readYearMonth("Payroll month");
        PayrollDto payroll = s.payroll.getPayrollForEmployeeAndMonth(employeeId, month);
        return s.payslips.buildPayslip(payroll.id());
    }

    void display(PayslipDto slip) {
        io.println();
        io.println(s.payslips.render(slip));
    }

    void saveFor(PayslipDto slip) {
        Path file = s.payslips.writeToFile(slip);
        io.success("Payslip saved to " + file.toAbsolutePath());
    }

    private void save() {
        PayslipDto slip = s.payslips.buildPayslip(io.readId("Payroll ID: "));
        saveFor(slip);
    }
}
