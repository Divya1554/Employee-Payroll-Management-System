package com.mca.payroll.integration;

import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.UserDto;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.AuthenticationFailedException;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.service.AuthService;
import com.mca.payroll.service.UserService;
import com.mca.payroll.util.HibernateUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthIntegrationTest extends IntegrationTestBase {

    private final AuthService auth = new AuthService();
    private final UserService users = new UserService();

    @Test
    @DisplayName("First-run setup creates one ADMIN, stores only a BCrypt hash and allows login")
    void firstRunAndLogin() {
        assertTrue(auth.isFirstRun());
        auth.createInitialAdmin("admin", "Admin12345");
        assertFalse(auth.isFirstRun());
        assertThrows(BusinessRuleViolationException.class, () -> auth.createInitialAdmin("second", "Admin12345"));

        String stored = HibernateUtil.readOnly(s -> s.createQuery(
                "select u.passwordHash from User u where u.username = 'admin'", String.class).getSingleResult());
        assertTrue(stored.startsWith("$2"));
        assertFalse(stored.contains("Admin12345"));

        AuthenticatedUser user = auth.login("ADMIN", "Admin12345");        // user names are case-insensitive
        assertEquals(Role.ADMIN, user.role());
    }

    @Test
    @DisplayName("Wrong password, unknown user and inactive user all fail with the same message")
    void failedLogins() {
        auth.createInitialAdmin("admin", "Admin12345");
        AuthenticationFailedException wrong = assertThrows(AuthenticationFailedException.class, () -> auth.login("admin", "bad"));
        AuthenticationFailedException unknown = assertThrows(AuthenticationFailedException.class, () -> auth.login("nobody", "Admin12345"));
        assertEquals(wrong.getMessage(), unknown.getMessage());

        signIn(Role.ADMIN, null);
        UserDto hr = users.createUser("hruser", "Hr12345678", Role.HR, null);
        users.setActive(hr.id(), false);
        assertThrows(AuthenticationFailedException.class, () -> auth.login("hruser", "Hr12345678"));
    }

    @Test
    @DisplayName("EMPLOYEE accounts must be linked to an employee, one account per employee")
    void employeeAccounts() {
        EmployeeDto e = employee("EMP001", department("IT").id());
        assertThrows(com.mca.payroll.exception.ValidationException.class,
                () -> users.createUser("emp1", "Emp12345678", Role.EMPLOYEE, null));
        users.createUser("emp1", "Emp12345678", Role.EMPLOYEE, e.id());
        assertThrows(com.mca.payroll.exception.DuplicateRecordException.class,
                () -> users.createUser("emp2", "Emp12345678", Role.EMPLOYEE, e.id()));

        AuthenticatedUser login = auth.login("emp1", "Emp12345678");
        assertEquals(e.id(), login.employeeId());
    }
}
