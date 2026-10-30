package com.mca.payroll.integration;

import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.PayrollBatchResult;
import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.Payroll;
import com.mca.payroll.entity.PayrollStatus;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.InvalidPayrollException;
import com.mca.payroll.exception.PayrollAlreadyExistsException;
import com.mca.payroll.exception.UnauthorizedOperationException;
import com.mca.payroll.util.HibernateUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollIntegrationTest extends IntegrationTestBase {

    private static final YearMonth APRIL = YearMonth.of(2025, 4);

    private EmployeeDto employeeWithSalary() {
        EmployeeDto e = employee("EMP001", department("IT").id());
        salary(e.id(), LocalDate.of(2024, 1, 1), "30000");
        return e;
    }

    private long payrollRows() {
        return HibernateUtil.readOnly(s -> s.createQuery("select count(p) from Payroll p", Long.class).getSingleResult());
    }

    @Test
    @DisplayName("Payroll generation applies the formulas and stores a DRAFT snapshot")
    void generatePayroll() {
        EmployeeDto e = employeeWithSalary();
        attendance.recordAttendance(e.id(), LocalDate.of(2025, 4, 2), AttendanceStatus.ABSENT, null);

        PayrollDto p = payroll.generatePayroll(e.id(), APRIL, BigDecimal.ZERO);

        assertEquals(PayrollStatus.DRAFT, p.status());
        assertEquals(new BigDecimal("45350.00"), p.grossSalary());
        assertEquals(new BigDecimal("1.00"), p.unpaidLeaveDays());
        // April 2025: calendar-days basis 30 -> 45,350 / 30 = 1,511.67 unpaid deduction
        assertEquals(new BigDecimal("1511.67"), p.unpaidDeduction());
        assertEquals(p.grossSalary().subtract(p.totalDeductions()), p.netSalary());
    }

    @Test
    @DisplayName("Duplicate payroll for the same employee, month and year is prevented")
    void duplicatePayrollPrevented() {
        EmployeeDto e = employeeWithSalary();
        payroll.generatePayroll(e.id(), APRIL, null);

        assertThrows(PayrollAlreadyExistsException.class, () -> payroll.generatePayroll(e.id(), APRIL, null));
        assertEquals(1, payrollRows());
    }

    @Test
    @DisplayName("Finalized payroll is unchanged by later salary revisions and cannot be regenerated")
    void finalizedPayrollIsHistory() {
        EmployeeDto e = employeeWithSalary();
        PayrollDto draft = payroll.generatePayroll(e.id(), APRIL, null);
        PayrollDto finalized = payroll.finalizePayroll(draft.id());
        assertEquals(PayrollStatus.FINALIZED, finalized.status());

        // revise the salary from May and also correct the old structure's amounts
        salaries.createStructure(e.id(), salaryRequest(LocalDate.of(2025, 5, 1), "60000"));
        salaries.updateStructure(salaries.getStructuresByEmployee(e.id()).get(1).id(),
                salaryRequest(LocalDate.of(2024, 1, 1), "99999"));

        PayrollDto reloaded = payroll.getPayrollById(draft.id());
        assertEquals(finalized.basicSalary(), reloaded.basicSalary());
        assertEquals(finalized.netSalary(), reloaded.netSalary());
        assertThrows(InvalidPayrollException.class, () -> payroll.regenerateDraftPayroll(e.id(), APRIL, null));
        assertEquals(2, salaries.getStructuresByEmployee(e.id()).size());        // history kept
    }

    @Test
    @DisplayName("A DRAFT payroll can be regenerated after an attendance change")
    void draftCanBeRegenerated() {
        EmployeeDto e = employeeWithSalary();
        PayrollDto first = payroll.generatePayroll(e.id(), APRIL, null);
        attendance.recordAttendance(e.id(), LocalDate.of(2025, 4, 2), AttendanceStatus.ABSENT, null);

        PayrollDto second = payroll.regenerateDraftPayroll(e.id(), APRIL, null);

        assertEquals(first.id(), second.id());
        assertTrue(second.netSalary().compareTo(first.netSalary()) < 0);
        assertEquals(1, payrollRows());
    }

    @Test
    @DisplayName("Attendance of a finalized month is locked")
    void finalizedMonthIsLocked() {
        EmployeeDto e = employeeWithSalary();
        payroll.finalizePayroll(payroll.generatePayroll(e.id(), APRIL, null).id());

        assertThrows(BusinessRuleViolationException.class, () ->
                attendance.recordAttendance(e.id(), LocalDate.of(2025, 4, 10), AttendanceStatus.ABSENT, null));
    }

    @Test
    @DisplayName("A failed calculation (negative net) rolls back and stores nothing")
    void negativeNetRollsBack() {
        EmployeeDto e = employeeWithSalary();
        assertThrows(InvalidPayrollException.class,
                () -> payroll.generatePayroll(e.id(), APRIL, new BigDecimal("1000000")));
        assertEquals(0, payrollRows());
    }

    @Test
    @DisplayName("An exception after persist rolls the whole transaction back")
    void exceptionAfterPersistRollsBack() {
        EmployeeDto e = employeeWithSalary();
        assertThrows(IllegalStateException.class, () -> HibernateUtil.inTransaction(session -> {
            Payroll p = new Payroll();
            p.setEmployee(session.getReference(com.mca.payroll.entity.Employee.class, e.id()));
            p.setPayrollMonth(4);
            p.setPayrollYear(2025);
            p.setWorkingDays(26);
            p.setPresentDays(BigDecimal.ZERO);
            p.setPaidLeaveDays(BigDecimal.ZERO);
            p.setUnpaidLeaveDays(BigDecimal.ZERO);
            p.setPayableDaysBasis(30);
            for (java.util.function.Consumer<BigDecimal> c : java.util.List.<java.util.function.Consumer<BigDecimal>>of(
                    p::setBasicSalary, p::setHouseRentAllowance, p::setMedicalAllowance, p::setConveyanceAllowance,
                    p::setOtherAllowances, p::setGrossSalary, p::setProvidentFundDeduction, p::setProfessionalTax,
                    p::setUnpaidDeduction, p::setOtherDeductions, p::setTotalDeductions, p::setNetSalary)) {
                c.accept(BigDecimal.TEN);
            }
            p.setGeneratedAt(java.time.LocalDateTime.now());
            session.persist(p);
            session.flush();                                   // row is written inside the transaction ...
            throw new IllegalStateException("simulated failure");   // ... and must be rolled back
        }));
        assertEquals(0, payrollRows());
    }

    @Test
    @DisplayName("Batch generation processes active employees and reports skipped ones")
    void batchGeneration() {
        EmployeeDto withSalary = employeeWithSalary();
        employee("EMP002", department("HR").id());              // no salary structure -> skipped

        PayrollBatchResult result = payroll.generatePayrollForAll(APRIL);

        assertEquals(1, result.generated().size());
        assertEquals(withSalary.employeeCode(), result.generated().get(0).employeeCode());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).startsWith("EMP002"));
    }

    @Test
    @DisplayName("Payroll history of an employee lists all months, newest first")
    void payrollHistory() {
        EmployeeDto e = employeeWithSalary();
        payroll.generatePayroll(e.id(), YearMonth.of(2025, 3), null);
        payroll.generatePayroll(e.id(), YearMonth.of(2025, 4), null);

        var history = payroll.getPayrollByEmployee(e.id());
        assertEquals(2, history.size());
        assertEquals(4, history.get(0).month());
    }

    @Test
    @DisplayName("Employees see only their own finalized payroll")
    void employeeAccess() {
        EmployeeDto e1 = employeeWithSalary();
        EmployeeDto e2 = employee("EMP002", department("HR").id());
        salary(e2.id(), LocalDate.of(2024, 1, 1), "25000");
        PayrollDto draft1 = payroll.generatePayroll(e1.id(), APRIL, null);
        PayrollDto draft2 = payroll.generatePayroll(e2.id(), APRIL, null);
        payroll.finalizePayroll(draft1.id());

        signIn(com.mca.payroll.entity.Role.EMPLOYEE, e1.id());
        assertEquals(1, payroll.getPayrollByEmployee(e1.id()).size());
        assertThrows(UnauthorizedOperationException.class, () -> payroll.getPayrollByEmployee(e2.id()));
        assertThrows(UnauthorizedOperationException.class, () -> payroll.getPayrollById(draft2.id()));
        assertThrows(UnauthorizedOperationException.class, () -> payroll.finalizePayroll(draft1.id()));
    }
}
