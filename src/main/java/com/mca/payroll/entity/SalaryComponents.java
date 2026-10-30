package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Embeddable value object holding every monetary component of a salary structure.
 * Demonstrates {@code @Embeddable}/{@code @Embedded}: the columns live in the
 * owning table (salary_structures) but the group is handled as one object.
 * All amounts are BigDecimal - never float or double.
 */
@Embeddable
public class SalaryComponents {

    @Column(name = "basic_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "house_rent_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal houseRentAllowance = BigDecimal.ZERO;

    @Column(name = "medical_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal medicalAllowance = BigDecimal.ZERO;

    @Column(name = "conveyance_allowance", nullable = false, precision = 12, scale = 2)
    private BigDecimal conveyanceAllowance = BigDecimal.ZERO;

    @Column(name = "other_allowances", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherAllowances = BigDecimal.ZERO;

    @Column(name = "provident_fund_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal providentFundDeduction = BigDecimal.ZERO;

    @Column(name = "professional_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    /** Required by Hibernate. */
    public SalaryComponents() {
    }

    public SalaryComponents(BigDecimal basicSalary, BigDecimal houseRentAllowance,
                            BigDecimal medicalAllowance, BigDecimal conveyanceAllowance,
                            BigDecimal otherAllowances, BigDecimal providentFundDeduction,
                            BigDecimal professionalTax) {
        this.basicSalary = basicSalary;
        this.houseRentAllowance = houseRentAllowance;
        this.medicalAllowance = medicalAllowance;
        this.conveyanceAllowance = conveyanceAllowance;
        this.otherAllowances = otherAllowances;
        this.providentFundDeduction = providentFundDeduction;
        this.professionalTax = professionalTax;
    }

    /** Gross = Basic + HRA + Medical + Conveyance + Other allowances. */
    public BigDecimal grossSalary() {
        return nz(basicSalary).add(nz(houseRentAllowance)).add(nz(medicalAllowance))
                .add(nz(conveyanceAllowance)).add(nz(otherAllowances));
    }

    /** Fixed deductions stored in the structure (PF + professional tax). */
    public BigDecimal fixedDeductions() {
        return nz(providentFundDeduction).add(nz(professionalTax));
    }

    /** Independent copy, used when a structure is assigned/revised so history is never shared. */
    public SalaryComponents copy() {
        return new SalaryComponents(basicSalary, houseRentAllowance, medicalAllowance,
                conveyanceAllowance, otherAllowances, providentFundDeduction, professionalTax);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

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

    public BigDecimal getProvidentFundDeduction() { return providentFundDeduction; }
    public void setProvidentFundDeduction(BigDecimal providentFundDeduction) { this.providentFundDeduction = providentFundDeduction; }

    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal professionalTax) { this.professionalTax = professionalTax; }

    @Override
    public String toString() {
        return "SalaryComponents{basic=" + basicSalary + ", hra=" + houseRentAllowance
                + ", medical=" + medicalAllowance + ", conveyance=" + conveyanceAllowance
                + ", other=" + otherAllowances + ", pf=" + providentFundDeduction
                + ", pt=" + professionalTax + '}';
    }
}
