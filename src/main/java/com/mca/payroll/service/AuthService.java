package com.mca.payroll.service;

import com.mca.payroll.dao.UserDao;
import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.Role;
import com.mca.payroll.entity.User;
import com.mca.payroll.exception.AuthenticationFailedException;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.PasswordHasher;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;

/**
 * Authentication. Passwords are verified against BCrypt hashes; the failure message is always the same
 * ("Invalid username or password") so an attacker cannot tell which part was wrong. The role of the
 * session comes from the database row of the verified user - never from console input.
 */
public class AuthService {

    private static final Logger LOG = LogManager.getLogger(AuthService.class);

    /** Lazily created BCrypt hash, so that an unknown user name costs the same time as a known one. */
    private static final class DummyHash {
        static final String VALUE = PasswordHasher.hash("not-a-real-password-1");
    }

    private final UserDao userDao;

    public AuthService() {
        this(new UserDao());
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /** True when no user exists yet (first run). */
    public boolean isFirstRun() {
        return HibernateUtil.readOnly(session -> userDao.countUsers(session) == 0);
    }

    /** First-run setup: creates the initial ADMIN account. Refused when any user already exists. */
    public void createInitialAdmin(String username, String password) {
        String name = InputValidator.validateUsername(username);
        InputValidator.validatePassword(password, AppConfig.getInstance().getInt("security.password.min.length", 8));
        String hash = PasswordHasher.hash(password);
        HibernateUtil.inTransaction(session -> {
            if (userDao.countUsers(session) > 0) {
                throw new BusinessRuleViolationException("Initial setup was already completed.");
            }
            userDao.save(session, new User(name, hash, Role.ADMIN, null));
            LOG.info("Initial admin account created: {}", name);
            return null;
        });
    }

    public AuthenticatedUser login(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            throw new AuthenticationFailedException("Invalid username or password.");
        }
        AuthenticatedUser user = HibernateUtil.inTransaction(session -> {
            User found = userDao.findByUsername(session, username.trim()).orElse(null);
            String hash = found == null ? DummyHash.VALUE : found.getPasswordHash();
            boolean passwordOk = PasswordHasher.verify(password, hash);
            if (found == null || !passwordOk || !found.isActive()) {
                LOG.warn("Failed login attempt for '{}'", username.trim());
                return null;
            }
            found.setLastLoginAt(LocalDateTime.now());
            Employee employee = found.getEmployee();
            return new AuthenticatedUser(found.getUserId(), found.getUsername(), found.getRole(),
                    employee == null ? null : employee.getEmployeeId());
        });
        if (user == null) {
            throw new AuthenticationFailedException("Invalid username or password.");
        }
        SecurityContext.setCurrentUser(user);
        LOG.info("User signed in: {} ({})", user.username(), user.role());
        return user;
    }

    public void logout() {
        AuthenticatedUser user = SecurityContext.currentUserOrNull();
        if (user != null) {
            LOG.info("User signed out: {}", user.username());
        }
        SecurityContext.clear();
    }
}
