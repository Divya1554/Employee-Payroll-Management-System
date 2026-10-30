package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A department of the organisation (table: departments).
 * Department names are unique. Employees are NOT cascaded: removing a
 * department can never delete employees (the service refuses to delete a
 * department that still has employees).
 */
@Entity
@Table(name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uk_department_name", columnNames = "department_name"))
@NamedQueries({
        @NamedQuery(name = "Department.findAll",
                query = "select d from Department d order by d.departmentName"),
        @NamedQuery(name = "Department.findByName",
                query = "select d from Department d where lower(d.departmentName) = lower(:name)")
})
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_name", nullable = false, length = 100)
    private String departmentName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "location", length = 100)
    private String location;

    /** Optimistic locking: two users editing the same department cannot silently overwrite each other. */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Inverse side of Employee.department (One-to-Many, lazy, no cascade). */
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private Set<Employee> employees = new HashSet<>();

    protected Department() {
        // required by Hibernate
    }

    public Department(String departmentName, String description, String location) {
        this.departmentName = departmentName;
        this.description = description;
        this.location = location;
    }

    public Long getDepartmentId() { return departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getVersion() { return version; }

    /** Read-only view; employees are added by assigning Employee.department. */
    public Set<Employee> getEmployees() { return Collections.unmodifiableSet(employees); }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Department other)) {
            return false;
        }
        return departmentId != null && departmentId.equals(other.getDepartmentId());
    }

    @Override
    public int hashCode() {
        return Department.class.hashCode();
    }

    /** Deliberately excludes the employees collection (avoids lazy loading / recursion). */
    @Override
    public String toString() {
        return "Department{id=" + departmentId + ", name='" + departmentName + "', location='" + location + "'}";
    }
}
