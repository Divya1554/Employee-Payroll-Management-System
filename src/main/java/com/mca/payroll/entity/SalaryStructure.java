package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One salary revision of one employee (table: salary_structures).
 *
 * <p>An employee has many structures over time (Many-to-One from this side). Each row is valid
 * from {@code effectiveFrom} to {@code effectiveTo} (null = still current). A revision never
 * overwrites the previous row - it closes it - so salary history is preserved. Payroll rows
 * additionally keep their own snapshot of the amounts they used.</p>
 */
@Entity
@Table(name = "salary_structures",
        uniqueConstraints = @UniqueConstraint(name = "uk_salary_employee_from",
                columnNames = {"employee_id", "effective_from"}),
        indexes = @Index(name = "idx_salary_employee_period", columnList = "employee_id, effective_from, effective_to"))
@NamedQueries({
        @NamedQuery(name = "SalaryStructure.findByEmployee",
                query = "select s from SalaryStructure s where s.employee.employeeId = :employeeId "
                        + "order by s.effectiveFrom desc"),
        @NamedQuery(name = "SalaryStructure.findEffective",
                query = "select s from SalaryStructure s where s.employee.employeeId = :employeeId "
                        + "and s.effectiveFrom <= :onDate and (s.effectiveTo is null or s.effectiveTo >= :onDate) "
                        + "order by s.effectiveFrom desc")
})
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "salary_structure_id")
    private Long salaryStructureId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_salary_employee"))
    private Employee employee;

    @Embedded
    private SalaryComponents components = new SalaryComponents();

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected SalaryStructure() {
        // required by Hibernate
    }

    public SalaryStructure(Employee employee, SalaryComponents components, LocalDate effectiveFrom, String createdBy) {
        this.employee = employee;
        this.components = components;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isEffectiveOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    public Long getSalaryStructureId() { return salaryStructureId; }

    public Employee getEmployee() { return employee; }

    public SalaryComponents getComponents() { return components; }
    public void setComponents(SalaryComponents components) { this.components = components; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Integer getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SalaryStructure other)) {
            return false;
        }
        return salaryStructureId != null && salaryStructureId.equals(other.getSalaryStructureId());
    }

    @Override
    public int hashCode() {
        return SalaryStructure.class.hashCode();
    }

    @Override
    public String toString() {
        return "SalaryStructure{id=" + salaryStructureId + ", from=" + effectiveFrom + ", to=" + effectiveTo + '}';
    }
}
