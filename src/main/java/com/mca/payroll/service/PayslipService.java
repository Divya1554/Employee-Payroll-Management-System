package com.mca.payroll.service;

import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.dto.PayslipDto;
import com.mca.payroll.entity.PayrollStatus;
import com.mca.payroll.exception.PayrollException;
import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.DateUtil;
import com.mca.payroll.util.MoneyUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Builds a readable monthly salary statement. Access control (own payroll / staff) is enforced by
 * PayrollService.getPayrollById, which this service calls.
 */
public class PayslipService {

    private static final int WIDTH = 64;

    private final PayrollService payrollService;
    private final AppConfig config;

    public PayslipService(PayrollService payrollService) {
        this(payrollService, AppConfig.getInstance());
    }

    public PayslipService(PayrollService payrollService, AppConfig config) {
        this.payrollService = payrollService;
        this.config = config;
    }

    public PayslipDto buildPayslip(Long payrollId) {
        PayrollDto payroll = payrollService.getPayrollById(payrollId);
        return new PayslipDto(
                config.get("company.name", "Company"),
                config.get("company.address", ""),
                config.get("company.phone", ""),
                config.get("company.email", ""),
                config.get("currency.label", "INR"),
                payroll);
    }

    /** Plain-text payslip (console-friendly, fixed width). */
    public String render(PayslipDto slip) {
        PayrollDto p = slip.payroll();
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(WIDTH);
        String thin = "-".repeat(WIDTH);

        sb.append(line).append('\n');
        sb.append(center(slip.companyName())).append('\n');
        if (!slip.companyAddress().isEmpty()) {
            sb.append(center(slip.companyAddress())).append('\n');
        }
        String contact = (slip.companyPhone() + "  " + slip.companyEmail()).trim();
        if (!contact.isEmpty()) {
            sb.append(center(contact)).append('\n');
        }
        sb.append(line).append('\n');
        sb.append(center("PAYSLIP - " + DateUtil.formatPeriod(p.period()).toUpperCase(Locale.ROOT))).append('\n');
        sb.append(line).append('\n');

        field(sb, "Employee Name", p.employeeName());
        field(sb, "Employee Code", p.employeeCode());
        field(sb, "Department", p.department());
        field(sb, "Designation", p.designation());
        sb.append(thin).append('\n');

        sb.append("ATTENDANCE SUMMARY\n");
        field(sb, "Working days", String.valueOf(p.workingDays()));
        field(sb, "Present days", p.presentDays().toPlainString());
        field(sb, "Paid leave days", p.paidLeaveDays().toPlainString());
        field(sb, "Unpaid days", p.unpaidLeaveDays().toPlainString());
        field(sb, "Payable-day basis", String.valueOf(p.payableDaysBasis()));
        sb.append(thin).append('\n');

        String cur = slip.currencyLabel();
        sb.append(String.format("%-32s %12s%n", "EARNINGS", cur));
        money(sb, "Basic salary", p.basicSalary());
        money(sb, "House rent allowance", p.houseRentAllowance());
        money(sb, "Medical allowance", p.medicalAllowance());
        money(sb, "Conveyance allowance", p.conveyanceAllowance());
        money(sb, "Other allowances", p.otherAllowances());
        money(sb, "GROSS SALARY", p.grossSalary());
        sb.append(thin).append('\n');

        sb.append(String.format("%-32s %12s%n", "DEDUCTIONS", cur));
        money(sb, "Provident fund", p.providentFundDeduction());
        money(sb, "Professional tax", p.professionalTax());
        money(sb, "Unpaid-leave deduction", p.unpaidDeduction());
        BigDecimal manual = p.manualOtherDeductions();
        if (manual.signum() != 0) {
            money(sb, "Other deductions", manual);
        }
        money(sb, "TOTAL DEDUCTIONS", p.totalDeductions());
        sb.append(line).append('\n');
        money(sb, "NET SALARY", p.netSalary());
        sb.append(line).append('\n');

        sb.append("Payroll status    : ").append(p.status());
        if (p.status() == PayrollStatus.DRAFT) {
            sb.append("  (not final - figures may change)");
        }
        sb.append('\n');
        sb.append("Generated         : ").append(DateUtil.formatDateTime(p.generatedAt())).append('\n');
        if (p.finalizedAt() != null) {
            sb.append("Finalized         : ").append(DateUtil.formatDateTime(p.finalizedAt())).append('\n');
        }
        sb.append(thin).append('\n');
        sb.append(center("This is a computer generated payslip.")).append('\n');
        return sb.toString();
    }

    /** Writes the payslip to payslip.output.dir/payslip_CODE_YYYY-MM.txt and returns the path. */
    public Path writeToFile(PayslipDto slip) {
        PayrollDto p = slip.payroll();
        Path dir = Paths.get(config.get("payslip.output.dir", "payslips"));
        String safeCode = p.employeeCode().replaceAll("[^A-Za-z0-9_-]", "_");
        Path file = dir.resolve(String.format("payslip_%s_%d-%02d.txt", safeCode, p.year(), p.month()));
        try {
            Files.createDirectories(dir);
            Files.writeString(file, render(slip), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PayrollException("Could not write the payslip file: " + e.getMessage(), e);
        }
        return file;
    }

    private static void field(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-18s: %s%n", label, value));
    }

    private static void money(StringBuilder sb, String label, BigDecimal amount) {
        sb.append(String.format("%-32s %12s%n", label, MoneyUtil.format(amount)));
    }

    private static String center(String text) {
        String t = truncate(text, WIDTH);
        int pad = Math.max(0, (WIDTH - t.length()) / 2);
        return " ".repeat(pad) + t;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + ".";
    }
}
