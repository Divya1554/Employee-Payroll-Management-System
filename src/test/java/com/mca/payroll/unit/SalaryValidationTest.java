package com.mca.payroll.unit;

import com.mca.payroll.dto.SalaryRequest;
import com.mca.payroll.entity.SalaryComponents;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.service.SalaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.mca.payroll.unit.TestData.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Salary-structure validation (components and deductions must be non-negative). */
class SalaryValidationTest {

    private SalaryRequest request(String basic, String hra, String medical, String conv, String other, String pf, String pt) {
        return new SalaryRequest(bd(basic), bd(hra), bd(medical), bd(conv), bd(other), bd(pf), bd(pt),
                LocalDate.of(2025, 4, 1));
    }

    @Test
    @DisplayName("A valid request produces components with 2-decimal scale and the right gross")
    void validRequest() {
        SalaryComponents c = SalaryService.validateComponents(request("30000", "12000", "1250", "1600", "500", "3600", "200"));
        assertEquals(bd("45350.00"), c.grossSalary());
        assertEquals(bd("3800.00"), c.fixedDeductions());
        assertEquals(2, c.getBasicSalary().scale());
    }

    @Test
    @DisplayName("Negative allowances and deductions are rejected")
    void negativeRejected() {
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(request("30000", "-1", "0", "0", "0", "0", "0")));
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(request("30000", "0", "0", "0", "0", "-5", "0")));
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(request("30000", "0", "0", "0", "0", "0", "-5")));
    }

    @Test
    @DisplayName("Basic salary must be greater than zero")
    void basicMustBePositive() {
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(request("0", "0", "0", "0", "0", "0", "0")));
    }

    @Test
    @DisplayName("Missing amounts and more than two decimals are rejected")
    void nullsAndPrecision() {
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(
                new SalaryRequest(bd("1000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now())));
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(request("1000.123", "0", "0", "0", "0", "0", "0")));
    }

    @Test
    @DisplayName("Fixed deductions cannot exceed the gross salary")
    void deductionsCannotExceedGross() {
        assertThrows(ValidationException.class, () -> SalaryService.validateComponents(request("1000", "0", "0", "0", "0", "900", "200")));
    }
}
