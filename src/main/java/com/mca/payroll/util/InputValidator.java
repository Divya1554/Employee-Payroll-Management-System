package com.mca.payroll.util;

import com.mca.payroll.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.regex.Pattern;

/** Centralised input validation. Every method throws {@link ValidationException} with a readable message. */
public final class InputValidator {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern EMPLOYEE_CODE = Pattern.compile("^[A-Za-z0-9_-]{2,30}$");
    private static final Pattern PERSON_NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]{0,49}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\- ]{7,20}$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    /** Minimum age of an employee on the day of joining. */
    public static final int MINIMUM_JOINING_AGE = 18;

    private InputValidator() {
    }

    /** Returns the trimmed value; throws when it is null or blank. */
    public static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value.trim();
    }

    public static String requireMaxLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new ValidationException(fieldName + " must not be longer than " + max + " characters.");
        }
        return value;
    }

    /** Optional text: trimmed, blank becomes null, length checked. */
    public static String optionalText(String value, int max, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        requireMaxLength(trimmed, max, fieldName);
        return trimmed;
    }

    /** Required text with a maximum length. */
    public static String requiredText(String value, int max, String fieldName) {
        String trimmed = requireNotBlank(value, fieldName);
        requireMaxLength(trimmed, max, fieldName);
        return trimmed;
    }

    public static String validateEmployeeCode(String code) {
        String trimmed = requireNotBlank(code, "Employee code");
        if (!EMPLOYEE_CODE.matcher(trimmed).matches()) {
            throw new ValidationException(
                    "Employee code must be 2-30 characters: letters, digits, '-' or '_' (e.g. EMP001).");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static String validatePersonName(String name, String fieldName) {
        String trimmed = requireNotBlank(name, fieldName);
        if (!PERSON_NAME.matcher(trimmed).matches()) {
            throw new ValidationException(fieldName + " may contain only letters, spaces, '.', ''' and '-' (max 50 characters).");
        }
        return trimmed;
    }

    /** Returns the e-mail in lower case. */
    public static String validateEmail(String email) {
        String trimmed = requireNotBlank(email, "Email");
        if (trimmed.length() > 150 || !EMAIL.matcher(trimmed).matches()) {
            throw new ValidationException("'" + trimmed + "' is not a valid e-mail address.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** Optional phone number; returns null when blank. */
    public static String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        long digits = trimmed.chars().filter(Character::isDigit).count();
        if (!PHONE.matcher(trimmed).matches() || digits < 7 || digits > 15) {
            throw new ValidationException("Phone number must contain 7-15 digits (digits, spaces, '+', '-', '(' and ')' allowed).");
        }
        return trimmed;
    }

    public static void validateDateOfBirth(LocalDate dateOfBirth, LocalDate dateOfJoining, LocalDate today) {
        if (dateOfBirth == null) {
            throw new ValidationException("Date of birth is required.");
        }
        if (!dateOfBirth.isBefore(today)) {
            throw new ValidationException("Date of birth must be in the past.");
        }
        if (dateOfBirth.isBefore(today.minusYears(100))) {
            throw new ValidationException("Date of birth is more than 100 years ago - please check it.");
        }
        if (dateOfJoining != null && Period.between(dateOfBirth, dateOfJoining).getYears() < MINIMUM_JOINING_AGE) {
            throw new ValidationException("The employee must be at least " + MINIMUM_JOINING_AGE
                    + " years old on the date of joining.");
        }
    }

    public static void validateDateOfJoining(LocalDate dateOfJoining, LocalDate today) {
        if (dateOfJoining == null) {
            throw new ValidationException("Date of joining is required.");
        }
        if (dateOfJoining.isBefore(LocalDate.of(1950, 1, 1))) {
            throw new ValidationException("Date of joining is too far in the past - please check it.");
        }
        if (dateOfJoining.isAfter(today.plusDays(90))) {
            throw new ValidationException("Date of joining cannot be more than 90 days in the future.");
        }
    }

    public static BigDecimal requireNonNegative(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new ValidationException(fieldName + " is required (use 0 if not applicable).");
        }
        if (amount.signum() < 0) {
            throw new ValidationException(fieldName + " must not be negative.");
        }
        return amount;
    }

    public static BigDecimal requirePositive(BigDecimal amount, String fieldName) {
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException(fieldName + " must be greater than zero.");
        }
        return amount;
    }

    public static String validateUsername(String username) {
        String trimmed = requireNotBlank(username, "Username");
        if (!USERNAME.matcher(trimmed).matches()) {
            throw new ValidationException("Username must be 3-50 characters: letters, digits, '.', '_' or '-'.");
        }
        return trimmed;
    }

    /** Password policy: minimum length (configurable) with at least one letter and one digit. */
    public static void validatePassword(String password, int minLength) {
        if (password == null || password.length() < minLength) {
            throw new ValidationException("Password must be at least " + minLength + " characters long.");
        }
        if (password.length() > 72) {
            throw new ValidationException("Password must not be longer than 72 characters.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("Password must contain at least one letter and one digit.");
        }
    }
}
