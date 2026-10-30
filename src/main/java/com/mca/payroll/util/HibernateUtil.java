package com.mca.payroll.util;

import com.mca.payroll.exception.DatabaseOperationException;
import com.mca.payroll.exception.PayrollException;
import jakarta.persistence.OptimisticLockException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.hibernate.StaleObjectStateException;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Map;
import java.util.function.Function;

/**
 * Creates and owns the single {@link SessionFactory} and provides the two helpers that every
 * service uses to run database work:
 *
 * <ul>
 *   <li>{@link #inTransaction(Function)} - opens a Session, begins a Transaction, commits on success,
 *       rolls back on any failure and always closes the Session;</li>
 *   <li>{@link #readOnly(Function)} - same, for queries (the session is marked read-only so
 *       Hibernate skips dirty checking).</li>
 * </ul>
 *
 * <p>Connection details come from environment variables / configuration files, never from source code:
 * PAYROLL_DB_URL, PAYROLL_DB_USER, PAYROLL_DB_PASSWORD (or db.url, db.username, db.password).</p>
 */
public final class HibernateUtil {

    private static final Logger LOG = LogManager.getLogger(HibernateUtil.class);

    private static final Object LOCK = new Object();
    private static SessionFactory sessionFactory;
    private static StandardServiceRegistry serviceRegistry;

    private HibernateUtil() {
    }

    /** The shared SessionFactory (built on first use). */
    public static SessionFactory getSessionFactory() {
        synchronized (LOCK) {
            if (sessionFactory == null) {
                build(Map.of());
            }
            return sessionFactory;
        }
    }

    /**
     * Closes the current SessionFactory (if any) and builds a new one with extra settings that
     * override the defaults. Used by integration tests to point Hibernate at a test database.
     */
    public static void reinitialize(Map<String, Object> overrides) {
        synchronized (LOCK) {
            shutdownInternal();
            build(overrides);
        }
    }

    /** Releases the connection pool. Call once when the application exits. */
    public static void shutdown() {
        synchronized (LOCK) {
            shutdownInternal();
        }
    }

    private static void build(Map<String, Object> overrides) {
        AppConfig config = AppConfig.getInstance();
        String url = firstNonBlank(System.getenv("PAYROLL_DB_URL"), config.get("db.url"));
        String user = firstNonBlank(System.getenv("PAYROLL_DB_USER"), config.get("db.username"));
        String password = firstNonBlank(System.getenv("PAYROLL_DB_PASSWORD"), config.get("db.password"));

        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder()
                .configure("hibernate.cfg.xml");
        if (url != null) {
            builder.applySetting("jakarta.persistence.jdbc.url", url);
            builder.applySetting("hibernate.connection.url", url);
        }
        if (user != null) {
            builder.applySetting("jakarta.persistence.jdbc.user", user);
            builder.applySetting("hibernate.connection.username", user);
        }
        if (password != null) {
            builder.applySetting("jakarta.persistence.jdbc.password", password);
            builder.applySetting("hibernate.connection.password", password);
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            builder.applySetting(entry.getKey(), entry.getValue());
        }

        StandardServiceRegistry registry = builder.build();
        try {
            sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
            serviceRegistry = registry;
            LOG.info("Hibernate SessionFactory created");
        } catch (RuntimeException ex) {
            StandardServiceRegistryBuilder.destroy(registry);
            // Log the real cause (never the password) and show the user a safe message.
            LOG.error("Could not create the Hibernate SessionFactory", ex);
            throw new DatabaseOperationException(
                    "Could not connect to the database. Check that MySQL is running and that the URL, "
                            + "user name and password are configured (see README). Details: logs/payroll.log",
                    ex, false);
        }
    }

    private static void shutdownInternal() {
        if (sessionFactory != null) {
            try {
                sessionFactory.close();
            } catch (RuntimeException ex) {
                LOG.warn("Error while closing the SessionFactory", ex);
            }
            sessionFactory = null;
        }
        if (serviceRegistry != null) {
            try {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            } catch (RuntimeException ex) {
                LOG.warn("Error while destroying the service registry", ex);
            }
            serviceRegistry = null;
        }
    }

    /**
     * Runs {@code work} in one transaction: commit on success, rollback on any failure,
     * session always closed. Application exceptions ({@link PayrollException}) are re-thrown
     * unchanged; any other failure becomes a {@link DatabaseOperationException}.
     */
    public static <T> T inTransaction(Function<Session, T> work) {
        return execute(work, false);
    }

    /** Runs a query-only unit of work in a read-only transaction. */
    public static <T> T readOnly(Function<Session, T> work) {
        return execute(work, true);
    }

    private static <T> T execute(Function<Session, T> work, boolean readOnly) {
        Session session = getSessionFactory().openSession();
        Transaction tx = null;
        try {
            if (readOnly) {
                session.setDefaultReadOnly(true);
            }
            tx = session.beginTransaction();
            T result = work.apply(session);
            tx.commit();
            return result;
        } catch (RuntimeException ex) {
            rollbackQuietly(tx, ex);
            if (ex instanceof PayrollException) {
                throw ex;
            }
            throw translate(ex);
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
    }

    private static void rollbackQuietly(Transaction tx, RuntimeException original) {
        if (tx != null && tx.isActive()) {
            try {
                tx.rollback();
                LOG.info("Transaction rolled back");
            } catch (RuntimeException rollbackFailure) {
                // Keep the original failure as the main one; the rollback problem is attached to it.
                original.addSuppressed(rollbackFailure);
                LOG.error("Rollback failed", rollbackFailure);
            }
        }
    }

    private static DatabaseOperationException translate(RuntimeException ex) {
        LOG.error("Database operation failed", ex);
        if (causedBy(ex, OptimisticLockException.class) || causedBy(ex, StaleObjectStateException.class)) {
            return new DatabaseOperationException(
                    "The record was changed by someone else while you were working on it. "
                            + "Please reload it and try again.", ex, false);
        }
        if (causedBy(ex, ConstraintViolationException.class)) {
            return new DatabaseOperationException(
                    "The operation was rejected by a database constraint (duplicate or still-referenced record).",
                    ex, true);
        }
        return new DatabaseOperationException(
                "A database error occurred. Details were written to logs/payroll.log.", ex, false);
    }

    private static boolean causedBy(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 20) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
