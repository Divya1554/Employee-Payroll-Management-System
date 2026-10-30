package com.mca.payroll.service;

import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.UserDao;
import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.dto.UserDto;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.Role;
import com.mca.payroll.entity.User;
import com.mca.payroll.exception.AuthenticationFailedException;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.DuplicateRecordException;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.exception.ResourceNotFoundException;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.PasswordHasher;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/** User-account management. Everything except changing your own password is ADMIN only. */
public class UserService {

    private static final Logger LOG = LogManager.getLogger(UserService.class);

    private final UserDao userDao;
    private final EmployeeDao employeeDao;

    public UserService() {
        this(new UserDao(), new EmployeeDao());
    }

    public UserService(UserDao userDao, EmployeeDao employeeDao) {
        this.userDao = userDao;
        this.employeeDao = employeeDao;
    }

    private int minPasswordLength() {
        return AppConfig.getInstance().getInt("security.password.min.length", 8);
    }

    /** Creates an account. Role EMPLOYEE must be linked to an employee; ADMIN/HR accounts may be unlinked. */
    public UserDto createUser(String username, String password, Role role, Long employeeId) {
        SecurityContext.requireRole(Role.ADMIN);
        String name = InputValidator.validateUsername(username);
        InputValidator.validatePassword(password, minPasswordLength());
        if (role == null) {
            throw new ValidationException("Role is required.");
        }
        if (role == Role.EMPLOYEE && employeeId == null) {
            throw new ValidationException("An EMPLOYEE account must be linked to an employee.");
        }
        String hash = PasswordHasher.hash(password);

        return HibernateUtil.inTransaction(session -> {
            if (userDao.findByUsername(session, name).isPresent()) {
                throw new DuplicateRecordException("Username '" + name + "' is already taken.");
            }
            Employee employee = null;
            if (employeeId != null) {
                employee = employeeDao.findById(session, employeeId)
                        .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
                if (!employee.isActive()) {
                    throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode() + " is inactive.");
                }
                if (userDao.findByEmployeeId(session, employeeId).isPresent()) {
                    throw new DuplicateRecordException("Employee " + employee.getEmployeeCode()
                            + " already has a login account.");
                }
            }
            User user = new User(name, hash, role, employee);
            userDao.save(session, user);
            session.flush();
            LOG.info("User created: {} ({})", name, role);
            return DtoMapper.toDto(user);
        });
    }

    public List<UserDto> listUsers() {
        SecurityContext.requireRole(Role.ADMIN);
        return HibernateUtil.readOnly(session ->
                userDao.findAllWithEmployee(session).stream().map(DtoMapper::toDto).toList());
    }

    /** Activates or deactivates an account. You cannot deactivate yourself or the last active ADMIN. */
    public UserDto setActive(Long userId, boolean active) {
        AuthenticatedUser admin = SecurityContext.requireRole(Role.ADMIN);
        return HibernateUtil.inTransaction(session -> {
            User user = userDao.findById(session, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));
            if (!active) {
                if (user.getUserId().equals(admin.userId())) {
                    throw new BusinessRuleViolationException("You cannot deactivate your own account.");
                }
                if (user.getRole() == Role.ADMIN && countActiveAdmins(session) <= 1) {
                    throw new BusinessRuleViolationException("At least one active ADMIN account must remain.");
                }
            }
            user.setActive(active);
            session.flush();
            LOG.info("User {} {}", user.getUsername(), active ? "activated" : "deactivated");
            return DtoMapper.toDto(user);
        });
    }

    public void resetPassword(Long userId, String newPassword) {
        SecurityContext.requireRole(Role.ADMIN);
        InputValidator.validatePassword(newPassword, minPasswordLength());
        String hash = PasswordHasher.hash(newPassword);
        HibernateUtil.inTransaction(session -> {
            User user = userDao.findById(session, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));
            user.setPasswordHash(hash);
            LOG.info("Password reset for {}", user.getUsername());
            return null;
        });
    }

    /** Any signed-in user may change their own password after proving the current one. */
    public void changeOwnPassword(String currentPassword, String newPassword) {
        AuthenticatedUser me = SecurityContext.requireAuthenticated();
        InputValidator.validatePassword(newPassword, minPasswordLength());
        String hash = PasswordHasher.hash(newPassword);
        HibernateUtil.inTransaction(session -> {
            User user = userDao.findById(session, me.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", me.userId()));
            if (!PasswordHasher.verify(currentPassword, user.getPasswordHash())) {
                throw new AuthenticationFailedException("The current password is incorrect.");
            }
            user.setPasswordHash(hash);
            LOG.info("Password changed by {}", user.getUsername());
            return null;
        });
    }

    private long countActiveAdmins(org.hibernate.Session session) {
        Long count = session.createQuery(
                        "select count(u) from User u where u.role = :role and u.active = true", Long.class)
                .setParameter("role", Role.ADMIN)
                .getSingleResult();
        return count == null ? 0L : count;
    }
}
