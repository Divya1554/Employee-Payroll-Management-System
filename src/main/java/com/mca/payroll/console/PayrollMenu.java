package com.mca.payroll.console;

import com.mca.payroll.dto.PageResult;
import com.mca.payroll.dto.PayrollBatchResult;
import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.util.DateUtil;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Module 6 - Payroll processing. */
class PayrollMenu {

    private static final int PAGE_SIZE = 10;

    private final ConsoleIO io;
    private final Services s;

    PayrollMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Payroll Processing", List.of("Generate payroll for one employee",
                    "Generate payroll for all active employees", "Regenerate a DRAFT payroll",
                    "View payroll by employee (history)", "View payroll by month", "View all payroll records (paged)",
                    "Finalize one payroll (ADMIN)", "Finalize all DRAFT payroll of a month (ADMIN)",
                    "Mark payroll as PAID (ADMIN)"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> generateOne();
                    case 2 -> generateAll();
                    case 3 -> regenerate();
                    case 4 -> Views.payrolls(io, s.payroll.getPayrollByEmployee(io.readId("Employee ID: ")));
                    case 5 -> Views.payrolls(io, s.payroll.getPayrollByMonth(io.readYearMonth("Month")));
                    case 6 -> paged();
                    case 7 -> finalizeOne();
                    case 8 -> finalizeMonth();
                    case 9 -> markPaid();
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    private void generateOne() {
        Long employeeId = io.readId("Employee ID: ");
        YearMonth month = io.readYearMonth("Payroll month");
        BigDecimal other = io.readAmount("Other deductions (manual, e.g. salary advance)", BigDecimal.ZERO);
        PayrollDto p = s.payroll.generatePayroll(employeeId, month, other);
        summary(p);
    }

    private void generateAll() {
        YearMonth month = io.readYearMonth("Payroll month");
        if (!io.confirm("Generate DRAFT payroll for all active employees for " + DateUtil.formatPeriod(month) + "?")) {
            io.info("Cancelled.");
            return;
        }
        PayrollBatchResult result = s.payroll.generatePayrollForAll(month);
        Views.payrolls(io, result.generated());
        io.success(result.generated().size() + " payroll record(s) generated.");
        if (!result.skipped().isEmpty()) {
            io.info("Skipped " + result.skipped().size() + ":");
            result.skipped().forEach(line -> io.println("   - " + line));
        }
    }

    private void regenerate() {
        Long employeeId = io.readId("Employee ID: ");
        YearMonth month = io.readYearMonth("Payroll month");
        BigDecimal other = io.readAmount("Other deductions (manual)", BigDecimal.ZERO);
        summary(s.payroll.regenerateDraftPayroll(employeeId, month, other));
    }

    private void paged() {
        int page = 0;
        while (true) {
            PageResult<PayrollDto> result = s.payroll.getAllPayroll(page, PAGE_SIZE);
            Views.payrolls(io, result.items());
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

    private void finalizeOne() {
        Long id = io.readId("Payroll ID to finalize: ");
        PayrollDto p = s.payroll.getPayrollById(id);
        summary(p);
        if (io.confirm("Finalize this payroll? It can no longer be changed.")) {
            s.payroll.finalizePayroll(id);
            io.success("Payroll finalized.");
        } else {
            io.info("Cancelled.");
        }
    }

    private void finalizeMonth() {
        YearMonth month = io.readYearMonth("Month to finalize");
        if (io.confirm("Finalize ALL draft payroll of " + DateUtil.formatPeriod(month) + "?")) {
            int count = s.payroll.finalizeMonth(month);
            io.success(count + " payroll record(s) finalized.");
        } else {
            io.info("Cancelled.");
        }
    }

    private void markPaid() {
        Long id = io.readId("Payroll ID to mark as PAID: ");
        s.payroll.markAsPaid(id);
        io.success("Payroll marked as PAID.");
    }

    private void summary(PayrollDto p) {
        io.println("Payroll #" + p.id() + " - " + p.employeeCode() + " " + p.employeeName() + " - "
                + DateUtil.formatPeriod(p.period()) + " [" + p.status() + "]");
        io.println("  Working days " + p.workingDays() + ", present " + p.presentDays().toPlainString()
                + ", paid leave " + p.paidLeaveDays().toPlainString() + ", unpaid " + p.unpaidLeaveDays().toPlainString());
        io.println("  Gross salary     : " + Views.money(p.grossSalary()));
        io.println("  Total deductions : " + Views.money(p.totalDeductions())
                + " (PF " + Views.money(p.providentFundDeduction()) + ", PT " + Views.money(p.professionalTax())
                + ", other " + Views.money(p.otherDeductions()) + ")");
        io.println("  NET SALARY       : " + Views.money(p.netSalary()));
    }
}
