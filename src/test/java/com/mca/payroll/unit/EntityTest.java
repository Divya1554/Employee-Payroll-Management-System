package com.mca.payroll.unit;

import com.mca.payroll.entity.Department;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.EmploymentStatus;
import com.mca.payroll.entity.Role;
import com.mca.payroll.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Entity behaviour: employee creation defaults, equals/hashCode/toString without recursion. */
class EntityTest {

    private Employee newEmployee(String code, Department d) {
        return new Employee(code, "Rahul", "Sharma", "rahul@example.com", "9876543210",
                LocalDate.of(1995, 5, 20), LocalDate.of(2022, 6, 1), "Developer", d);
    }

    @Test
    @DisplayName("New employee is ACTIVE and linked to its department")
    void creationDefaults() {
        Department it = new Department("IT", "Information Technology", "Pune");
        Employee e = newEmployee("EMP001", it);

        assertEquals(EmploymentStatus.ACTIVE, e.getEmploymentStatus());
        assertTrue(e.isActive());
        assertEquals("Rahul Sharma", e.getFullName());
        assertEquals(it, e.getDepartment());
    }

    @Test
    @DisplayName("Employee equality uses the business key (employee code)")
    void equalityByCode() {
        Department it = new Department("IT", null, null);
        assertEquals(newEmployee("EMP001", it), newEmployee("EMP001", it));
        assertFalse(newEmployee("EMP001", it).equals(newEmployee("EMP002", it)));
        assertEquals(newEmployee("EMP001", it).hashCode(), newEmployee("EMP001", it).hashCode());
    }

    @Test
    @DisplayName("toString never touches lazy associations and never prints the password hash")
    void toStringIsSafe() {
        Department it = new Department("IT", null, "Pune");
        Employee e = newEmployee("EMP001", it);
        assertFalse(e.toString().contains("Department"));
        assertTrue(it.toString().contains("IT"));

        User u = new User("admin", "$2a$12$secrethashvalue", Role.ADMIN, null);
        assertFalse(u.toString().contains("secrethashvalue"));
    }

    @Test
    @DisplayName("Department exposes employees read-only")
    void departmentEmployeesReadOnly() {
        Department it = new Department("IT", null, null);
        try {
            it.getEmployees().add(newEmployee("EMP001", it));
            throw new AssertionError("collection should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
