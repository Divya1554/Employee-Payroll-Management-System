package com.mca.payroll.console;

import com.mca.payroll.exception.PayrollException;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.DateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Console;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * All console input/output in one place: prompts, validated reads (re-asking on bad input) and an
 * error-handling wrapper so that no exception ever terminates the program.
 */
public class ConsoleIO {

    private static final Logger LOG = LogManager.getLogger(ConsoleIO.class);

    /** Thrown when standard input is closed (Ctrl+D / Ctrl+Z) so the program can exit cleanly. */
    public static class InputClosedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InputClosedException() {
            super("Input closed");
        }
    }

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleIO(Scanner scanner, PrintStream out) {
        this.scanner = scanner;
        this.out = out;
    }

    public static ConsoleIO system() {
        return new ConsoleIO(new Scanner(System.in), System.out);
    }

    // ------------------------------------------------------------------ output

    public void println(String text) {
        out.println(text);
    }

    public void println() {
        out.println();
    }

    public void printf(String format, Object... args) {
        out.printf(format, args);
    }

    public void header(String title) {
        out.println();
        out.println("========== " + title.toUpperCase() + " ==========");
        out.println();
    }

    public void success(String message) {
        out.println("[OK] " + message);
    }

    public void error(String message) {
        out.println("[ERROR] " + message);
    }

    public void info(String message) {
        out.println("[INFO] " + message);
    }

    // ------------------------------------------------------------------ input

    /** Raw line (trimmed). Throws InputClosedException when input ends. */
    public String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            throw new InputClosedException();
        }
    }

    /** Non-empty text; re-asks until something is entered. */
    public String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            error("This value is required.");
        }
    }

    /** Optional text; empty string when skipped. */
    public String readOptional(String prompt) {
        return readLine(prompt + " (optional, Enter to skip): ");
    }

    public int readInt(String prompt, int min, int max) {
        while (true) {
            String text = readLine(prompt);
            try {
                int value = Integer.parseInt(text);
                if (value >= min && value <= max) {
                    return value;
                }
                error("Enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                error("'" + text + "' is not a valid number.");
            }
        }
    }

    public Long readId(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                long value = Long.parseLong(text);
                if (value > 0) {
                    return value;
                }
                error("An ID must be a positive number.");
            } catch (NumberFormatException e) {
                error("'" + text + "' is not a valid ID.");
            }
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String text = readLine(prompt + " [yyyy-MM-dd]: ");
            try {
                return DateUtil.parseDate(text);
            } catch (ValidationException e) {
                error(e.getMessage());
            }
        }
    }

    public YearMonth readYearMonth(String prompt) {
        while (true) {
            String text = readLine(prompt + " [yyyy-MM]: ");
            try {
                return DateUtil.parseYearMonth(text);
            } catch (ValidationException e) {
                error(e.getMessage());
            }
        }
    }

    /** Money amount; blank returns the default when one is given. */
    public BigDecimal readAmount(String prompt, BigDecimal defaultValue) {
        while (true) {
            String suffix = defaultValue == null ? ": " : " [" + defaultValue.toPlainString() + "]: ";
            String text = readLine(prompt + suffix);
            if (text.isEmpty() && defaultValue != null) {
                return defaultValue;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                error("'" + text + "' is not a valid amount.");
            }
        }
    }

    public boolean confirm(String question) {
        while (true) {
            String text = readLine(question + " (y/n): ").toLowerCase();
            if (text.equals("y") || text.equals("yes")) {
                return true;
            }
            if (text.equals("n") || text.equals("no")) {
                return false;
            }
            error("Please answer y or n.");
        }
    }

    public <E extends Enum<E>> E readEnum(String prompt, Class<E> type) {
        E[] values = type.getEnumConstants();
        for (int i = 0; i < values.length; i++) {
            out.printf("  %d. %s%n", i + 1, values[i].name());
        }
        int choice = readInt(prompt + " (1-" + values.length + "): ", 1, values.length);
        return values[choice - 1];
    }

    /**
     * Reads a password without echo when a real console exists. IDE consoles (Eclipse/IntelliJ) have no
     * System.console(), so the password is then read as normal (visible) text.
     */
    public String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword(prompt);
            if (chars == null) {
                throw new InputClosedException();
            }
            String password = new String(chars);
            java.util.Arrays.fill(chars, ' ');
            return password;
        }
        return readLine(prompt + "(visible in this console) ");
    }

    public void pause() {
        readLine("Press Enter to continue...");
    }

    // ------------------------------------------------------------------ menus and error handling

    /** Prints the menu options and returns the selected number (0 = back/exit). */
    public int menu(String title, List<String> options) {
        header(title);
        for (int i = 0; i < options.size(); i++) {
            out.printf("%d. %s%n", i + 1, options.get(i));
        }
        out.println("0. Back");
        out.println();
        return readInt("Enter your choice: ", 0, options.size());
    }

    /**
     * Runs one menu action. Application exceptions show their (user-safe) message; anything unexpected is
     * logged with its stack trace and reported generically. The program keeps running either way.
     */
    public void run(Runnable action) {
        try {
            action.run();
        } catch (InputClosedException e) {
            throw e;
        } catch (PayrollException e) {
            error(e.getMessage());
            LOG.debug("Operation failed", e);
        } catch (RuntimeException e) {
            LOG.error("Unexpected error", e);
            error("An unexpected error occurred. Details were written to logs/payroll.log.");
        }
    }
}
