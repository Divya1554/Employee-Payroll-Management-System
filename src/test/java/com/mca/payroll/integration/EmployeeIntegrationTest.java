package com.mca.payroll.integration;

import com.mca.payroll.dto.DepartmentDto;
import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.EmployeeRequest;
import com.mca.payroll.entity.EmploymentStatus;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.DuplicateEmployeeException;
import com.mca.payroll.exception.DuplicateRecordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeeIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Employee creation persists the employee and assigns the department")
    void createEmployeeAssignsDepartment() {
        DepartmentDto it = department("IT");
        EmployeeDto e = employee("EMP001", it.id());

        assertNotNull(e.id());
        assertEquals("EMP001", e.employeeCode());
        assertEquals(it.id(), e.departmentId());
        assertEquals("IT", e.departmentName());
        assertEquals(EmploymentStatus.ACTIVE, e.status());
        assertEquals(1, employees.getByDepartment(it.id()).size());
    }

    @Test
    @DisplayName("Duplicate employee code and duplicate e-mail are rejected")
    void duplicateEmployeeCodeAndEmail() {
        DepartmentDto it = department("IT");
        employee("EMP001", it.id());

        assertThrows(DuplicateEmployeeException.class, () -> employee("EMP001", it.id()));

        EmployeeRequest sameEmail = new EmployeeRequest("EMP002", "Other", "Person", "emp001@example.com", null,
                LocalDate.of(1990, 1, 1), LocalDate.of(2024, 1, 1), "Tester", it.id());
        assertThrows(DuplicateEmployeeException.class, () -> employees.addEmployee(sameEmail));
    }

    @Test
    @DisplayName("Department names are unique")
    void duplicateDepartmentName() {
        department("IT");
        assertThrows(DuplicateRecordException.class, () -> department("it"));
    }

    @Test
    @DisplayName("A department that still has employees cannot be deleted")
    void departmentWithEmployeesCannotBeDeleted() {
        DepartmentDto it = department("IT");
        employee("EMP001", it.id());
        assertThrows(BusinessRuleViolationException.class, () -> departments.deleteDepartment(it.id()));

        DepartmentDto empty = department("Empty");
        departments.deleteDepartment(empty.id());
        assertEquals(1, departments.getAllDepartments().size());
    }

    @Test
    @DisplayName("Deactivation keeps the record; employees with history cannot be deleted")
    void deactivateInsteadOfDelete() {
        DepartmentDto it = department("IT");
        EmployeeDto e = employee("EMP001", it.id());
        salary(e.id(), LocalDate.of(2024, 1, 1), "30000");

        assertThrows(BusinessRuleViolationException.class, () -> employees.deleteEmployee(e.id()));
        EmployeeDto inactive = employees.deactivateEmployee(e.id());
        assertEquals(EmploymentStatus.INACTIVE, inactive.status());
        assertEquals(1, employees.getAllEmployees().size());
    }

    @Test
    @DisplayName("Search by name, designation, joining date and the dynamic filter")
    void searches() {
        DepartmentDto it = department("IT");
        employee("EMP001", it.id());
        employee("EMP002", it.id());

        assertEquals(2, employees.searchByName("first").size());
        assertEquals(2, employees.getByDesignation("developer").size());
        assertEquals(2, employees.getByJoiningDate(LocalDate.of(2023, 12, 1), LocalDate.of(2024, 2, 1)).size());
        assertEquals(1, employees.getEmployeesPage(0, 1).items().size());
        assertEquals(2, employees.getEmployeesPage(0, 1).totalItems());
    }
}
