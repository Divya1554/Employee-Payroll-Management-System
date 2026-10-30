package com.mca.payroll.service;

import com.mca.payroll.dao.DepartmentDao;
import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.UserDao;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.dto.EmployeeDto;
import com.mca.payroll.dto.EmployeeFilter;
import com.mca.payroll.dto.EmployeeRequest;
import com.mca.payroll.dto.PageResult;
import com.mca.payroll.entity.Department;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.EmploymentStatus;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.DepartmentNotFoundException;
import com.mca.payroll.exception.DuplicateEmployeeException;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Business rules for employees. ADMIN and HR manage employees; an EMPLOYEE may only read their own
 * record. Employees are deactivated rather than deleted; permanent deletion is allowed only for an
 * employee with no history at all (mistaken entry).
 */
public class EmployeeService {

    private static final Logger LOG = LogManager.getLogger(EmployeeService.class);

    private final EmployeeDao employeeDao;
    private final DepartmentDao departmentDao;
    private final UserDao userDao;
    private final Supplier<LocalDate> today;

    public EmployeeService() {
        this(new EmployeeDao(), new DepartmentDao(), new UserDao(), LocalDate::now);
    }

    public EmployeeService(EmployeeDao employeeDao, DepartmentDao departmentDao, UserDao userDao,
                           Supplier<LocalDate> today) {
        this.employeeDao = employeeDao;
        this.departmentDao = departmentDao;
        this.userDao = userDao;
        this.today = today;
    }

    // ------------------------------------------------------------------ create / update

    public EmployeeDto addEmployee(EmployeeRequest request) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        ValidatedEmployee v = validate(request);
        if (request.departmentId() == null) {
            throw new ValidationException("Department is required.");
        }

        return HibernateUtil.inTransaction(session -> {
            Department department = departmentDao.findById(session, request.departmentId())
                    .orElseThrow(() -> new DepartmentNotFoundException(request.departmentId()));
            assertUnique(session, v.code, v.email, null);

            Employee employee = new Employee(v.code, v.firstName, v.lastName, v.email, v.phone,
                    request.dateOfBirth(), request.dateOfJoining(), v.designation, department);
            employeeDao.save(session, employee);
            session.flush();
            LOG.info("Employee created: {}", employee);
            return DtoMapper.toDto(employee);
        });
    }

    /** Updates everything except the employee code. */
    public EmployeeDto updateEmployee(Long id, EmployeeRequest request) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        ValidatedEmployee v = validate(request, false);

        return HibernateUtil.inTransaction(session -> {
            Employee employee = loadWithDepartment(session, id);
            assertUnique(session, null, v.email, employee);

            if (request.departmentId() != null
                    && !request.departmentId().equals(employee.getDepartment().getDepartmentId())) {
                Department department = departmentDao.findById(session, request.departmentId())
                        .orElseThrow(() -> new DepartmentNotFoundException(request.departmentId()));
                employee.setDepartment(department);
            }
            employee.setFirstName(v.firstName);
            employee.setLastName(v.lastName);
            employee.setEmail(v.email);
            employee.setPhone(v.phone);
            employee.setDateOfBirth(request.dateOfBirth());
            employee.setDateOfJoining(request.dateOfJoining());
            employee.setDesignation(v.designation);
            session.flush();
            LOG.info("Employee updated: {}", employee);
            return DtoMapper.toDto(employee);
        });
    }

    /** Sets the employee INACTIVE and disables a linked login account. History is kept. */
    public EmployeeDto deactivateEmployee(Long id) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.inTransaction(session -> {
            Employee employee = loadWithDepartment(session, id);
            if (!employee.isActive()) {
                throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode() + " is already inactive.");
            }
            employee.setEmploymentStatus(EmploymentStatus.INACTIVE);
            userDao.findByEmployeeId(session, id).ifPresent(user -> user.setActive(false));
            session.flush();
            LOG.info("Employee deactivated: {}", employee);
            return DtoMapper.toDto(employee);
        });
    }

    public EmployeeDto reactivateEmployee(Long id) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.inTransaction(session -> {
            Employee employee = loadWithDepartment(session, id);
            if (employee.isActive()) {
                throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode() + " is already active.");
            }
            employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
            session.flush();
            LOG.info("Employee reactivated: {}", employee);
            return DtoMapper.toDto(employee);
        });
    }

    /**
     * Permanently deletes an employee. Refused (ADMIN only) when the employee has any payroll,
     * attendance, leave, salary or login history - deactivate instead.
     */
    public void deleteEmployee(Long id) {
        SecurityContext.requireRole(Role.ADMIN);
        HibernateUtil.inTransaction(session -> {
            Employee employee = loadWithDepartment(session, id);
            long payroll = employeeDao.countPayroll(session, id);
            if (payroll > 0) {
                throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode() + " has "
                        + payroll + " payroll record(s) and cannot be deleted. Deactivate the employee instead.");
            }
            long history = employeeDao.countAttendance(session, id) + employeeDao.countLeaveRequests(session, id)
                    + employeeDao.countSalaryStructures(session, id);
            if (history > 0 || userDao.findByEmployeeId(session, id).isPresent()) {
                throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode()
                        + " has attendance, leave, salary or login records and cannot be deleted. "
                        + "Deactivate the employee instead.");
            }
            employeeDao.delete(session, employee);
            LOG.info("Employee deleted: {}", employee);
            return null;
        });
    }

    // ------------------------------------------------------------------ queries

    public List<EmployeeDto> getAllEmployees() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> toDtos(employeeDao.search(session,
                new EmployeeFilter(null, null, null, null, null, null))));
    }

    /** Paginated list sorted by employee code. Page numbers start at 0. */
    public PageResult<EmployeeDto> getEmployeesPage(int page, int pageSize) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new ValidationException("Page must be >= 0 and page size between 1 and 200.");
        }
        return HibernateUtil.readOnly(session -> new PageResult<>(
                toDtos(employeeDao.findPage(session, page, pageSize)), page, pageSize, employeeDao.count(session)));
    }

    /** ADMIN/HR may read anyone; an EMPLOYEE only their own record. */
    public EmployeeDto getEmployeeById(Long id) {
        SecurityContext.requireSelfOrStaff(id);
        return HibernateUtil.readOnly(session -> DtoMapper.toDto(loadWithDepartment(session, id)));
    }

    public EmployeeDto getEmployeeByCode(String code) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String normalized = InputValidator.validateEmployeeCode(code);
        return HibernateUtil.readOnly(session -> DtoMapper.toDto(
                employeeDao.findByCode(session, normalized)
                        .orElseThrow(() -> new EmployeeNotFoundException("No employee with code " + normalized + "."))));
    }

    public List<EmployeeDto> searchByName(String fragment) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String text = InputValidator.requireNotBlank(fragment, "Search text");
        return HibernateUtil.readOnly(session -> toDtos(employeeDao.searchByName(session, text)));
    }

    public List<EmployeeDto> getByDepartment(Long departmentId) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> {
            departmentDao.findById(session, departmentId)
                    .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
            return toDtos(employeeDao.findByDepartment(session, departmentId));
        });
    }

    public List<EmployeeDto> getByDesignation(String designation) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String text = InputValidator.requireNotBlank(designation, "Designation");
        return HibernateUtil.readOnly(session -> toDtos(employeeDao.findByDesignation(session, text)));
    }

    public List<EmployeeDto> getByJoiningDate(LocalDate from, LocalDate to) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        if (from == null || to == null || to.isBefore(from)) {
            throw new ValidationException("Provide a valid date range (the end date must not be before the start date).");
        }
        return HibernateUtil.readOnly(session -> toDtos(employeeDao.findByJoiningDateBetween(session, from, to)));
    }

    /** Dynamic multi-criteria search (Criteria API). */
    public List<EmployeeDto> search(EmployeeFilter filter) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> toDtos(employeeDao.search(session, filter)));
    }

    // ------------------------------------------------------------------ helpers

    private record ValidatedEmployee(String code, String firstName, String lastName, String email,
                                     String phone, String designation) {
    }

    private ValidatedEmployee validate(EmployeeRequest r) {
        return validate(r, true);
    }

    private ValidatedEmployee validate(EmployeeRequest r, boolean requireCode) {
        String code = requireCode ? InputValidator.validateEmployeeCode(r.employeeCode()) : null;
        String first = InputValidator.validatePersonName(r.firstName(), "First name");
        String last = InputValidator.validatePersonName(r.lastName(), "Last name");
        String email = InputValidator.validateEmail(r.email());
        String phone = InputValidator.validatePhone(r.phone());
        String designation = InputValidator.requiredText(r.designation(), 100, "Designation");
        LocalDate now = today.get();
        InputValidator.validateDateOfJoining(r.dateOfJoining(), now);
        InputValidator.validateDateOfBirth(r.dateOfBirth(), r.dateOfJoining(), now);
        return new ValidatedEmployee(code, first, last, email, phone, designation);
    }

    /** Code and e-mail must be unique (the database constraints are the final guard). */
    private void assertUnique(Session session, String code, String email, Employee self) {
        if (code != null && employeeDao.findByCode(session, code).isPresent()) {
            throw new DuplicateEmployeeException("Employee code " + code + " is already in use.");
        }
        employeeDao.findByEmail(session, email).ifPresent(other -> {
            if (self == null || !other.getEmployeeId().equals(self.getEmployeeId())) {
                throw new DuplicateEmployeeException("E-mail " + email + " is already used by employee "
                        + other.getEmployeeCode() + ".");
            }
        });
    }

    private Employee loadWithDepartment(Session session, Long id) {
        return employeeDao.findByIdWithDepartment(session, id).orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private static List<EmployeeDto> toDtos(List<Employee> employees) {
        return employees.stream().map(DtoMapper::toDto).toList();
    }
}
