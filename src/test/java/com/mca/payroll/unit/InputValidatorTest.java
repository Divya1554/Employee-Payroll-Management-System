package com.mca.payroll.unit;

import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.InputValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation rules used by the employee, salary and user services. */
class InputValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    @Test
    @DisplayName("E-mail format is validated and normalised to lower case")
    void emailValidation() {
        assertEquals("rahul.sharma@example.com", InputValidator.validateEmail("  Rahul.Sharma@Example.com "));
        for (String bad : new String[]{"", "plain", "a@b", "a@@b.com", "a b@c.com", "@c.com", "a@.com"}) {
            assertThrows(ValidationException.class, () -> InputValidator.validateEmail(bad), bad);
        }
    }

    @Test
    @DisplayName("Employee code rules")
    void employeeCode() {
        assertEquals("EMP001", InputValidator.validateEmployeeCode(" emp001 "));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmployeeCode(""));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmployeeCode("a"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmployeeCode("EMP 001"));
    }

    @Test
    @DisplayName("Required fields cannot be blank")
    void requiredFields() {
        assertThrows(ValidationException.class, () -> InputValidator.requireNotBlank("   ", "First name"));
        assertThrows(ValidationException.class, () -> InputValidator.requireNotBlank(null, "First name"));
        assertEquals("Asha", InputValidator.requireNotBlank(" Asha ", "First name"));
    }

    @Test
    @DisplayName("Person names accept letters, spaces, dots, apostrophes and hyphens only")
    void personNames() {
        assertDoesNotThrow(() -> InputValidator.validatePersonName("Mary-Ann O'Neil", "Name"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePersonName("R2D2", "Name"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePersonName("<script>", "Name"));
    }

    @Test
    @DisplayName("Phone is optional but validated when given")
    void phone() {
        assertNull(InputValidator.validatePhone("  "));
        assertEquals("+91 98765 43210", InputValidator.validatePhone("+91 98765 43210"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePhone("12ab"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePhone("123"));
    }

    @Test
    @DisplayName("Date of birth and joining date rules")
    void dates() {
        assertDoesNotThrow(() -> InputValidator.validateDateOfBirth(LocalDate.of(1995, 3, 10), LocalDate.of(2020, 1, 1), TODAY));
        assertThrows(ValidationException.class,
                () -> InputValidator.validateDateOfBirth(TODAY.plusDays(1), TODAY, TODAY));          // future
        assertThrows(ValidationException.class,
                () -> InputValidator.validateDateOfBirth(LocalDate.of(2010, 1, 1), TODAY, TODAY));   // under 18
        assertThrows(ValidationException.class, () -> InputValidator.validateDateOfBirth(null, TODAY, TODAY));
        assertDoesNotThrow(() -> InputValidator.validateDateOfJoining(TODAY.plusDays(30), TODAY));
        assertThrows(ValidationException.class, () -> InputValidator.validateDateOfJoining(TODAY.plusDays(200), TODAY));
        assertThrows(ValidationException.class, () -> InputValidator.validateDateOfJoining(null, TODAY));
    }

    @Test
    @DisplayName("Money amounts must not be negative")
    void amounts() {
        assertDoesNotThrow(() -> InputValidator.requireNonNegative(BigDecimal.ZERO, "HRA"));
        assertThrows(ValidationException.class, () -> InputValidator.requireNonNegative(new BigDecimal("-0.01"), "HRA"));
        assertThrows(ValidationException.class, () -> InputValidator.requireNonNegative(null, "HRA"));
        assertThrows(ValidationException.class, () -> InputValidator.requirePositive(BigDecimal.ZERO, "Basic"));
    }

    @Test
    @DisplayName("Password policy: length, one letter and one digit")
    void passwordPolicy() {
        assertDoesNotThrow(() -> InputValidator.validatePassword("Secret123", 8));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("short1", 8));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("onlyletters", 8));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("12345678", 8));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword(null, 8));
    }
}
