package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * One monthly payroll record for one employee (table: payroll).
 *
 * <p><b>Snapshot:</b> every salary amount is copied into this row when the payroll is generated.
 * Later salary revisions therefore never change an existing payroll. A FINALIZED or PAID row is
 * immutable: the service rejects any attempt to regenerate or modify it.</p>
 *
 * <p>The unique constraint (employee, month, year) prevents duplicate payroll for the same period.</p>
 */
@Entity
@Table(name = "payroll",
        uniqueConstraints = @UniqueConstraint(name = "uk_payroll_employee_period",
                columnNames = {"employee_id", "payroll_month", "payroll_year"}),
        indexes = {
                @Index(name = "idx_payroll_period", columnList = "payroll_year, payroll_month"),
                @Index(name = "idx_payroll_status", columnList = "payroll_status")
        })
@NamedQueries({
        @NamedQuery(name = "Payroll.findByEmployeeAndPeriod",
                query = "select p from Payroll p where p.employee.employeeId = :employeeId "
                        + "and p.payrollMonth = :periodMonth and p.payrollYear = :periodYear"),
        @NamedQuery(name = "Payroll.countByEmployee",
                query = "select count(p) from Payroll p where p.employee.employeeId = :employeeId")
})
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payroll_id")
    private Long payrollId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payroll_employee"))
    private Employee employee;

    @Column(name = "payroll_month", nullable = false)
    private Integer payrollMonth;

    @Column(name = "payroll_year", nullable = false)
    private Integer payrollYear;

    // ---- attendance figures -------------------------------------------
    @Column(name = "working_days", nullable = false)
    private Integer workingDays;

    @Column(name = "present_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal presentDays;

    @Column(name = "paid_leave_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal paidLeaveDays;

    @Column(name = "unpaid_leave_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal unpaidLeaveDays;

    /** The divisor used to turn the monthly salary into a daily rate (see payroll.payable.days.mode). */
    @Column(name = "payable_days_basis", nullable = false)
    private Integer payableDaysBasis;

    // ---- salary snapshot ------------------------------------------------
    @Column(name = "basic_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "house_rent_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal houseRentAllowance;

    @Column(name = "medical_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal medicalAllowance;

    @Column(name = "conveyance_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal conveyanceAllowance;

    @Column(name = "other_allowances", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherAllowances;

    @Column(name = "gross_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossSalary;

    // ---- deductions -------------------------------------------------------
    @Column(name = "provident_fund_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal providentFundDeduction;

    @Column(name = "professional_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal professionalTax;

    /** Loss-of-pay amount for unpaid days. It is part of {@code otherDeductions}. */
    @Column(name = "unpaid_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal unpaidDeduction;

    /** unpaidDeduction + any manual other deduction entered at generation time. */
    @Column(name = "other_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherDeductions;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    // ---- status and audit ---------------------------------------------------
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payroll_status", nullable = false, length = 20)
    private PayrollStatus payrollStatus = PayrollStatus.DRAFT;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", length = 50)
    private String generatedBy;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by", length = 50)
    private String finalizedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public Payroll() {
        // required by Hibernate (public so the service layer can create new instances)
    }

    public boolean isDraft() {
        return payrollStatus == PayrollStatus.DRAFT;
    }

    public YearMonth period() {
        return YearMonth.of(payrollYear, payrollMonth);
    }

    public Long getPayrollId() { return payrollId; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Integer getPayrollMonth() { return payrollMonth; }
    public void setPayrollMonth(Integer payrollMonth) { this.payrollMonth = payrollMonth; }

    public Integer getPayrollYear() { return payrollYear; }
    public void setPayrollYear(Integer payrollYear) { this.payrollYear = payrollYear; }

    public Integer getWorkingDays() { return workingDays; }
    public void setWorkingDays(Integer workingDays) { this.workingDays = workingDays; }

    public BigDecimal getPresentDays() { return presentDays; }
    public void setPresentDays(BigDecimal presentDays) { this.presentDays = presentDays; }

    public BigDecimal getPaidLeaveDays() { return paidLeaveDays; }
    public void setPaidLeaveDays(BigDecimal paidLeaveDays) { this.paidLeaveDays = paidLeaveDays; }

    public BigDecimal getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public void setUnpaidLeaveDays(BigDecimal unpaidLeaveDays) { this.unpaidLeaveDays = unpaidLeaveDays; }

    public Integer getPayableDaysBasis() { return payableDaysBasis; }
    public void setPayableDaysBasis(Integer payableDaysBasis) { this.payableDaysBasis = payableDaysBasis; }

    public BigDecimal getBasicSalary() { return basicSalary; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }

    public BigDecimal getHouseRentAllowance() { return houseRentAllowance; }
    public void setHouseRentAllowance(BigDecimal houseRentAllowance) { this.houseRentAllowance = houseRentAllowance; }

    public BigDecimal getMedicalAllowance() { return medicalAllowance; }
    public void setMedicalAllowance(BigDecimal medicalAllowance) { this.medicalAllowance = medicalAllowance; }

    public BigDecimal getConveyanceAllowance() { return conveyanceAllowance; }
    public void setConveyanceAllowance(BigDecimal conveyanceAllowance) { this.conveyanceAllowance = conveyanceAllowance; }

    public BigDecimal getOtherAllowances() { return otherAllowances; }
    public void setOtherAllowances(BigDecimal otherAllowances) { this.otherAllowances = otherAllowances; }

    public BigDecimal getGrossSalary() { return grossSalary; }
    public void setGrossSalary(BigDecimal grossSalary) { this.grossSalary = grossSalary; }

    public BigDecimal getProvidentFundDeduction() { return providentFundDeduction; }
    public void setProvidentFundDeduction(BigDecimal providentFundDeduction) { this.providentFundDeduction = providentFundDeduction; }

    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; }

    public BigDecimal getUnpaidDeduction() { return unpaidDeduction; }
    public void setUnpaidDeduction(BigDecimal unpaidDeduction) { this.unpaidDeduction = unpaidDeduction; }

    public BigDecimal getOtherDeductions() { return otherDeductions; }
    public void setOtherDeductions(BigDecimal otherDeductions) { this.otherDeductions = otherDeductions; }

    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }

    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }

    public PayrollStatus getPayrollStatus() { return payrollStatus; }
    public void setPayrollStatus(PayrollStatus payrollStatus) { this.payrollStatus = payrollStatus; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }

    public String getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(String finalizedBy) { this.finalizedBy = finalizedBy; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public Integer getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Payroll other)) {
            return false;
        }
        return payrollId != null && payrollId.equals(other.getPayrollId());
    }

    @Override
    public int hashCode() {
        return Payroll.class.hashCode();
    }

    @Override
    public String toString() {
        return "Payroll{id=" + payrollId + ", period=" + payrollMonth + "/" + payrollYear
                + ", net=" + netSalary + ", status=" + payrollStatus + '}';
    }
}
