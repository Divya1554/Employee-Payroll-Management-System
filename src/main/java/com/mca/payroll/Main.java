package com.mca.payroll;

import com.mca.payroll.console.ConsoleIO;
import com.mca.payroll.console.MainMenu;
import com.mca.payroll.console.Services;
import com.mca.payroll.exception.PayrollException;
import com.mca.payroll.util.HibernateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point of the Employee Payroll Management System (console application).
 * Run with: mvn compile exec:java   (or run this class from Eclipse / IntelliJ IDEA).
 */
public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        ConsoleIO io = ConsoleIO.system();
        try {
            io.println("Connecting to the database...");
            HibernateUtil.getSessionFactory();          // fail fast with a readable message
            new MainMenu(io, new Services()).run();
        } catch (ConsoleIO.InputClosedException e) {
            io.println();
            io.println("Input closed - exiting.");
        } catch (PayrollException e) {
            io.error(e.getMessage());
            LOG.error("Application stopped", e);
        } catch (RuntimeException e) {
            LOG.error("Fatal error", e);
            io.error("A fatal error occurred. Details were written to logs/payroll.log.");
        } finally {
            HibernateUtil.shutdown();
        }
    }
}
