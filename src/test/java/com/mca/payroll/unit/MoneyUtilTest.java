package com.mca.payroll.unit;

import com.mca.payroll.util.MoneyUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyUtilTest {

    @Test
    @DisplayName("Amounts are formatted with Indian digit grouping and two decimals")
    void indianGrouping() {
        assertEquals("0.00", MoneyUtil.format(BigDecimal.ZERO));
        assertEquals("999.00", MoneyUtil.format(new BigDecimal("999")));
        assertEquals("1,000.00", MoneyUtil.format(new BigDecimal("1000")));
        assertEquals("45,350.00", MoneyUtil.format(new BigDecimal("45350")));
        assertEquals("12,34,567.50", MoneyUtil.format(new BigDecimal("1234567.5")));
        assertEquals("-12,345.68", MoneyUtil.format(new BigDecimal("-12345.678")));
        assertEquals("0.00", MoneyUtil.format(null));
    }
}
