package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * An employee (table: employees).
 *
 * <p>Relationships (all lazy, none cascading - historical data must never be removed by accident):
 * Many-to-One to Department (owning side, real foreign key) and One-to-Many to Attendance,
 * LeaveRequest, Payroll and SalaryStructure (inverse sides).</p>
 */
@Entity
@Table(name = "employees",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_code", columnNames = "employee_code"),
                @UniqueConstraint(name = "uk_employee_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_employee_department", columnList = "department_id"),
                @Index(name = "idx_employee_designation", columnList = "designation"),
                @Index(name = "idx_employee_joining", columnList = "date_of_joining"),
                @Index(name = "idx_employee_status", columnList = "employment_status")
        })
@NamedQueries({
        @NamedQuery(name = "Employee.findByCode",
                query = "select e from Employee e where e.employeeCode = :code"),
        @NamedQuery(name = "Employee.findByEmail",
                query = "select e from Employee e where lower(e.email) = lower(:email)"),
        @NamedQuery(name = "Employee.findByDesignation",
                query = "select e from Employee e join fetch e.department "
                        + "where lower(e.designation) = lower(:designation) order by e.employeeCode"),
        @NamedQuery(name = "Employee.countInDepartment",
                query = "select count(e) from Employee e where e.department.departmentId = :departmentId")
})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "designation", nullable = false, length = 100)
    private String designation;

    /** Stored as text (ACTIVE / INACTIVE), not as a MySQL ENUM type. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_employee_department"))
    private Department department;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private Set<Attendance> attendanceRecords = new HashSet<>();

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private Set<LeaveRequest> leaveRequests = new HashSet<>();

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private Set<Payroll> payrolls = new HashSet<>();

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private Set<SalaryStructure> salaryStructures = new HashSet<>();

    protected Employee() {
        // required by Hibernate
    }

    public Employee(String employeeCode, String firstName, String lastName, String email, String phone,
                    LocalDate dateOfBirth, LocalDate dateOfJoining, String designation, Department department) {
        this.employeeCode = employeeCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.dateOfJoining = dateOfJoining;
        this.designation = designation;
        this.department = department;
        this.employmentStatus = EmploymentStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return employmentStatus == EmploymentStatus.ACTIVE;
    }

    public Long getEmployeeId() { return employeeId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(EmploymentStatus employmentStatus) { this.employmentStatus = employmentStatus; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }

    public Set<Attendance> getAttendanceRecords() { return Collections.unmodifiableSet(attendanceRecords); }
    public Set<LeaveRequest> getLeaveRequests() { return Collections.unmodifiableSet(leaveRequests); }
    public Set<Payroll> getPayrolls() { return Collections.unmodifiableSet(payrolls); }
    public Set<SalaryStructure> getSalaryStructures() { return Collections.unmodifiableSet(salaryStructures); }

    /** Business-key equality: the employee code is unique and immutable once assigned. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Employee other)) {
            return false;
        }
        return employeeCode != null && employeeCode.equals(other.getEmployeeCode());
    }

    @Override
    public int hashCode() {
        return employeeCode == null ? Employee.class.hashCode() : employeeCode.hashCode();
    }

    /** Excludes the department and every collection to avoid lazy loading and infinite recursion. */
    @Override
    public String toString() {
        return "Employee{id=" + employeeId + ", code='" + employeeCode + "', name='" + getFullName()
                + "', status=" + employmentStatus + '}';
    }
}
