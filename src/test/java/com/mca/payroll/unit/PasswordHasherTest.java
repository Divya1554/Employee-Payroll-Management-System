package com.mca.payroll.unit;

import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @BeforeEach
    void fastCost() {
        Properties p = new Properties();
        p.setProperty("security.bcrypt.cost", "4");      // low cost only to keep the test fast
        AppConfig.setInstance(AppConfig.of(p));
    }

    @AfterEach
    void reset() {
        AppConfig.setInstance(null);
    }

    @Test
    @DisplayName("Passwords are stored as salted BCrypt hashes, never as plain text")
    void hashesAreNotPlaintext() {
        String hash = PasswordHasher.hash("Secret123");
        assertNotEquals("Secret123", hash);
        assertTrue(hash.startsWith("$2"));
        assertEquals(60, hash.length());
        assertNotEquals(hash, PasswordHasher.hash("Secret123"));   // random salt
    }

    @Test
    @DisplayName("Verification accepts the right password and rejects wrong, null or malformed input")
    void verification() {
        String hash = PasswordHasher.hash("Secret123");
        assertTrue(PasswordHasher.verify("Secret123", hash));
        assertFalse(PasswordHasher.verify("secret123", hash));
        assertFalse(PasswordHasher.verify(null, hash));
        assertFalse(PasswordHasher.verify("Secret123", null));
        assertFalse(PasswordHasher.verify("Secret123", "not-a-hash"));
    }
}
