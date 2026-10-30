package com.mca.payroll.integration;

import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.dto.DepartmentDto;
import com.mca.payroll.dto.DepartmentRequest;
import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.EmployeeRequest;
import com.mca.payroll.dto.SalaryRequest;
import com.mca.payroll.entity.Role;
import com.mca.payroll.service.AttendanceService;
import com.mca.payroll.service.DepartmentService;
import com.mca.payroll.service.EmployeeService;
import com.mca.payroll.service.LeaveService;
import com.mca.payroll.service.PayrollService;
import com.mca.payroll.service.SalaryService;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.SecurityContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class of the MySQL integration tests. They are tagged "integration" and skipped by a normal
 * {@code mvn test}; run them with {@code mvn test -Pintegration}.
 *
 * <p>They use a SEPARATE database (default employee_payroll_test) that Hibernate creates and drops
 * (create-drop) - never your real data. Configure it with the environment variables
 * PAYROLL_TEST_DB_URL, PAYROLL_TEST_DB_USER and PAYROLL_TEST_DB_PASSWORD.</p>
 */
@Tag("integration")
abstract class IntegrationTestBase {

    static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/employee_payroll_test"
            + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    protected final DepartmentService departments = new DepartmentService();
    protected final EmployeeService employees = new EmployeeService();
    protected final SalaryService salaries = new SalaryService();
    protected final AttendanceService attendance = new AttendanceService();
    protected final LeaveService leave = new LeaveService(attendance);
    protected final PayrollService payroll = new PayrollService(attendance);

    @BeforeAll
    static void startHibernate() {
        String url = env("PAYROLL_TEST_DB_URL", DEFAULT_URL);
        String user = env("PAYROLL_TEST_DB_USER", "root");
        String password = env("PAYROLL_TEST_DB_PASSWORD", "");
        Map<String, Object> settings = new HashMap<>();
        settings.put("jakarta.persistence.jdbc.url", url);
        settings.put("hibernate.connection.url", url);
        settings.put("jakarta.persistence.jdbc.user", user);
        settings.put("hibernate.connection.username", user);
        settings.put("jakarta.persistence.jdbc.password", password);
        settings.put("hibernate.connection.password", password);
        settings.put("hibernate.hbm2ddl.auto", "create-drop");
        HibernateUtil.reinitialize(settings);
    }

    @AfterAll
    static void stopHibernate() {
        HibernateUtil.shutdown();
    }

    @BeforeEach
    void cleanDatabaseAndSignInAsAdmin() {
        HibernateUtil.inTransaction(session -> {
            session.createNativeMutationQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            for (String table : List.of("payroll", "leave_requests", "attendance", "salary_structures",
                    "users", "employees", "departments")) {
                session.createNativeMutationQuery("DELETE FROM " + table).executeUpdate();
            }
            session.createNativeMutationQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
            return null;
        });
        signIn(Role.ADMIN, null);
    }

    @AfterEach
    void signOut() {
        SecurityContext.clear();
    }

    protected void signIn(Role role, Long employeeId) {
        SecurityContext.setCurrentUser(new AuthenticatedUser(1L, role.name().toLowerCase(), role, employeeId));
    }

    // ------------------------------------------------------------------ data helpers

    protected DepartmentDto department(String name) {
        return departments.addDepartment(new DepartmentRequest(name, name + " department", "Pune"));
    }

    protected EmployeeDto employee(String code, Long departmentId) {
        return employees.addEmployee(new EmployeeRequest(code, "First" + code.replaceAll("[^A-Za-z]", ""), "Last",
                code.toLowerCase() + "@example.com", "9876543210", LocalDate.of(1995, 5, 20),
                LocalDate.of(2024, 1, 1), "Developer", departmentId));
    }

    protected void salary(Long employeeId, LocalDate from, String basic) {
        salaries.createStructure(employeeId, salaryRequest(from, basic));
    }

    protected SalaryRequest salaryRequest(LocalDate from, String basic) {
        return new SalaryRequest(new BigDecimal(basic), new BigDecimal("12000"), new BigDecimal("1250"),
                new BigDecimal("1600"), new BigDecimal("500"), new BigDecimal("3600"), new BigDecimal("200"), from);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
