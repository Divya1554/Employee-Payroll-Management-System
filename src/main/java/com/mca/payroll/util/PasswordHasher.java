package com.mca.payroll.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * BCrypt password hashing (library: at.favre.lib:bcrypt). Passwords are never stored or logged;
 * only the salted BCrypt hash goes into the users table.
 */
public final class PasswordHasher {

    private static final int MIN_COST = 4;
    private static final int MAX_COST = 31;

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        int cost = AppConfig.getInstance().getInt("security.bcrypt.cost", 12);
        if (cost < MIN_COST || cost > MAX_COST) {
            throw new IllegalStateException("security.bcrypt.cost must be between " + MIN_COST + " and " + MAX_COST);
        }
        return BCrypt.withDefaults().hashToString(cost, plainPassword.toCharArray());
    }

    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        return BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash).verified;
    }
}
