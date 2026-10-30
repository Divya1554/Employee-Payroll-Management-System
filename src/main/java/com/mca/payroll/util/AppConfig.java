package com.mca.payroll.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Application settings. Loaded once from:
 * <ol>
 *   <li>{@code application.properties} on the classpath (defaults, safe to commit)</li>
 *   <li>an external file that overrides the defaults - the path in system property
 *       {@code payroll.config} or environment variable {@code PAYROLL_CONFIG}, otherwise
 *       {@code config/application-local.properties} when that file exists</li>
 * </ol>
 * Blank values are treated as "not set".
 */
public final class AppConfig {

    private static final String DEFAULT_RESOURCE = "application.properties";
    private static final String DEFAULT_EXTERNAL_FILE = "config/application-local.properties";

    private static volatile AppConfig instance;

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    /** The shared configuration (created on first use). */
    public static AppConfig getInstance() {
        AppConfig local = instance;
        if (local == null) {
            synchronized (AppConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Creates a configuration from explicit properties (used by unit tests). */
    public static AppConfig of(Properties properties) {
        Properties copy = new Properties();
        copy.putAll(properties);
        return new AppConfig(copy);
    }

    /** Replaces the shared configuration (used by tests); pass null to force a reload. */
    public static void setInstance(AppConfig config) {
        synchronized (AppConfig.class) {
            instance = config;
        }
    }

    private static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DEFAULT_RESOURCE, e);
        }

        Path external = resolveExternalFile();
        if (external != null && Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read configuration file " + external, e);
            }
        }
        return new AppConfig(props);
    }

    private static Path resolveExternalFile() {
        String path = System.getProperty("payroll.config");
        if (path == null || path.isBlank()) {
            path = System.getenv("PAYROLL_CONFIG");
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT_EXTERNAL_FILE;
        }
        return Paths.get(path.trim());
    }

    /** Value of a key, or null when missing or blank. */
    public String get(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Property '" + key + "' must be a whole number but is '" + value + "'.", e);
        }
    }

    /** Value as BigDecimal, or null when the property is missing/blank. */
    public BigDecimal getBigDecimal(String key) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Property '" + key + "' must be a number but is '" + value + "'.", e);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /** Comma separated list, empty when the property is missing/blank. */
    public List<String> getList(String key) {
        List<String> result = new ArrayList<>();
        String value = get(key);
        if (value != null) {
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
