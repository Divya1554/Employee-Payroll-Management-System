package com.mca.payroll.service;

import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.PayrollDao;
import com.mca.payroll.dao.SalaryStructureDao;
import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.dto.PageResult;
import com.mca.payroll.dto.PayrollBatchResult;
import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.dto.PayrollInput;
import com.mca.payroll.dto.PayrollResult;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.Payroll;
import com.mca.payroll.entity.PayrollStatus;
import com.mca.payroll.entity.Role;
import com.mca.payroll.entity.SalaryStructure;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.exception.InvalidPayrollException;
import com.mca.payroll.exception.PayrollAlreadyExistsException;
import com.mca.payroll.exception.ResourceNotFoundException;
import com.mca.payroll.exception.UnauthorizedOperationException;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.DateUtil;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly payroll processing - the core module.
 *
 * <ul>
 *   <li><b>Transactions:</b> every operation runs in one transaction; any failure rolls it back so a
 *       half-processed payroll can never be stored.</li>
 *   <li><b>Duplicates:</b> one row per (employee, month, year) - checked in code and enforced by a
 *       database unique constraint.</li>
 *   <li><b>Snapshot:</b> salary amounts are copied into the payroll row; later salary revisions do not
 *       change it.</li>
 *   <li><b>Immutability:</b> only DRAFT rows can be regenerated. FINALIZED and PAID rows are never modified
 *       by generation.</li>
 *   <li><b>Salary structure used:</b> the one in force on the last day of the payroll month (a revision
 *       that starts mid-month therefore applies to that whole month).</li>
 *   <li><b>Roles:</b> generate/regenerate: ADMIN, HR. Finalize and mark paid: ADMIN. An EMPLOYEE can see only
 *       their own FINALIZED/PAID payroll.</li>
 * </ul>
 */
public class PayrollService {

    private static final Logger LOG = LogManager.getLogger(PayrollService.class);

    private final PayrollDao payrollDao;
    private final EmployeeDao employeeDao;
    private final SalaryStructureDao salaryDao;
    private final AttendanceService attendanceService;
    private final PayrollCalculator calculator;

    public PayrollService(AttendanceService attendanceService) {
        this(new PayrollDao(), new EmployeeDao(), new SalaryStructureDao(), attendanceService,
                new PayrollCalculator(PayrollPolicy.fromConfig(AppConfig.getInstance())));
    }

    public PayrollService(PayrollDao payrollDao, EmployeeDao employeeDao, SalaryStructureDao salaryDao,
                          AttendanceService attendanceService, PayrollCalculator calculator) {
        this.payrollDao = payrollDao;
        this.employeeDao = employeeDao;
        this.salaryDao = salaryDao;
        this.attendanceService = attendanceService;
        this.calculator = calculator;
    }

    // ------------------------------------------------------------------ generate

    /** Generates a DRAFT payroll for one employee. Fails if payroll for that month already exists. */
    public PayrollDto generatePayroll(Long employeeId, YearMonth period, BigDecimal otherDeductions) {
        AuthenticatedUser user = SecurityContext.requireRole(Role.ADMIN, Role.HR);
        validatePeriod(period);
        return HibernateUtil.inTransaction(session -> {
            Employee employee = employeeDao.findByIdWithDepartment(session, employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            if (payrollDao.findByEmployeeAndPeriod(session, employeeId, period.getMonthValue(), period.getYear()).isPresent()) {
                throw new PayrollAlreadyExistsException("Payroll for " + employee.getEmployeeCode() + " for "
                        + DateUtil.formatPeriod(period) + " already exists.");
            }
            Payroll payroll = createPayroll(session, employee, period, otherDeductions, user.username());
            session.flush();
            LOG.info("Payroll generated: {} {} net {}", employee.getEmployeeCode(), period, payroll.getNetSalary());
            return DtoMapper.toDto(payroll);
        });
    }

    /**
     * Generates DRAFT payroll for every ACTIVE employee who had joined by the end of the month, in ONE
     * transaction. Employees that already have payroll, or whose payroll cannot be calculated
     * (no salary structure, negative net ...), are skipped and reported; an unexpected database error
     * rolls back the whole batch.
     */
    public PayrollBatchResult generatePayrollForAll(YearMonth period) {
        AuthenticatedUser user = SecurityContext.requireRole(Role.ADMIN, Role.HR);
        validatePeriod(period);
        return HibernateUtil.inTransaction(session -> {
            List<PayrollDto> generated = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (Employee employee : employeeDao.findActiveJoinedOnOrBefore(session, period.atEndOfMonth())) {
                if (payrollDao.findByEmployeeAndPeriod(session, employee.getEmployeeId(), period.getMonthValue(),
                        period.getYear()).isPresent()) {
                    skipped.add(employee.getEmployeeCode() + ": payroll already exists");
                    continue;
                }
                try {
                    // createPayroll validates and calculates BEFORE persisting, so a skipped employee leaves no trace.
                    Payroll payroll = createPayroll(session, employee, period, BigDecimal.ZERO, user.username());
                    generated.add(DtoMapper.toDto(payroll));
                } catch (InvalidPayrollException ex) {
                    skipped.add(employee.getEmployeeCode() + ": " + ex.getMessage());
                }
            }
            session.flush();
            LOG.info("Batch payroll {}: {} generated, {} skipped", period, generated.size(), skipped.size());
            return new PayrollBatchResult(period, generated, skipped);
        });
    }

    /** Recalculates an existing DRAFT payroll (for example after attendance corrections). */
    public PayrollDto regenerateDraftPayroll(Long employeeId, YearMonth period, BigDecimal otherDeductions) {
        AuthenticatedUser user = SecurityContext.requireRole(Role.ADMIN, Role.HR);
        validatePeriod(period);
        return HibernateUtil.inTransaction(session -> {
            Payroll payroll = payrollDao.findByEmployeeAndPeriod(session, employeeId, period.getMonthValue(), period.getYear())
                    .orElseThrow(() -> new InvalidPayrollException("No payroll exists for employee " + employeeId
                            + " in " + DateUtil.formatPeriod(period) + ". Generate it first."));
            if (payroll.getPayrollStatus() != PayrollStatus.DRAFT) {
                throw new InvalidPayrollException("Payroll is " + payroll.getPayrollStatus()
                        + " and can no longer be regenerated.");
            }
            Employee employee = employeeDao.findByIdWithDepartment(session, employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            PayrollResult result = calculate(session, employee, period, otherDeductions);
            apply(payroll, result);
            payroll.setGeneratedAt(LocalDateTime.now());
            payroll.setGeneratedBy(user.username());
            session.flush();
            LOG.info("Draft payroll regenerated: {} {}", employee.getEmployeeCode(), period);
            return DtoMapper.toDto(payroll);
        });
    }

    // ------------------------------------------------------------------ finalize / pay

    public PayrollDto finalizePayroll(Long payrollId) {
        AuthenticatedUser user = SecurityContext.requireRole(Role.ADMIN);
        return HibernateUtil.inTransaction(session -> {
            Payroll payroll = load(session, payrollId);
            finalizeRow(payroll, user);
            session.flush();
            return DtoMapper.toDto(payroll);
        });
    }

    /** Finalizes every DRAFT payroll of a month in one transaction; returns how many were finalized. */
    public int finalizeMonth(YearMonth period) {
        AuthenticatedUser user = SecurityContext.requireRole(Role.ADMIN);
        validatePeriod(period);
        return HibernateUtil.inTransaction(session -> {
            List<Payroll> drafts = payrollDao.findByPeriodAndStatus(session, period.getMonthValue(), period.getYear(),
                    PayrollStatus.DRAFT);
            drafts.forEach(p -> finalizeRow(p, user));
            session.flush();
            LOG.info("Payroll {} finalized: {} record(s)", period, drafts.size());
            return drafts.size();
        });
    }

    public PayrollDto markAsPaid(Long payrollId) {
        SecurityContext.requireRole(Role.ADMIN);
        return HibernateUtil.inTransaction(session -> {
            Payroll payroll = load(session, payrollId);
            if (payroll.getPayrollStatus() != PayrollStatus.FINALIZED) {
                throw new InvalidPayrollException("Only FINALIZED payroll can be marked as PAID (current status: "
                        + payroll.getPayrollStatus() + ").");
            }
            payroll.setPayrollStatus(PayrollStatus.PAID);
            payroll.setPaidAt(LocalDateTime.now());
            session.flush();
            LOG.info("Payroll marked paid: #{}", payrollId);
            return DtoMapper.toDto(payroll);
        });
    }

    // ------------------------------------------------------------------ queries

    public PayrollDto getPayrollById(Long payrollId) {
        SecurityContext.requireAuthenticated();
        return HibernateUtil.readOnly(session -> {
            Payroll payroll = payrollDao.findByIdWithDetails(session, payrollId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payroll", payrollId));
            assertVisible(payroll);
            return DtoMapper.toDto(payroll);
        });
    }

    /** Payroll history of an employee (newest first). An EMPLOYEE sees only own FINALIZED/PAID rows. */
    public List<PayrollDto> getPayrollByEmployee(Long employeeId) {
        AuthenticatedUser user = SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            if (employeeDao.findById(session, employeeId).isEmpty()) {
                throw new EmployeeNotFoundException(employeeId);
            }
            return payrollDao.findByEmployee(session, employeeId).stream()
                    .filter(p -> user.isStaff() || p.getPayrollStatus() != PayrollStatus.DRAFT)
                    .map(DtoMapper::toDto).toList();
        });
    }

    public List<PayrollDto> getPayrollByMonth(YearMonth period) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        validatePeriod(period);
        return HibernateUtil.readOnly(session ->
                payrollDao.findByPeriod(session, period.getMonthValue(), period.getYear())
                        .stream().map(DtoMapper::toDto).toList());
    }

    public PayrollDto getPayrollForEmployeeAndMonth(Long employeeId, YearMonth period) {
        AuthenticatedUser user = SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            Payroll payroll = payrollDao.findByEmployeeAndPeriod(session, employeeId, period.getMonthValue(), period.getYear())
                    .orElseThrow(() -> new InvalidPayrollException("No payroll exists for employee " + employeeId
                            + " in " + DateUtil.formatPeriod(period) + "."));
            assertVisible(payroll);
            // reload with department for the DTO
            return DtoMapper.toDto(payrollDao.findByIdWithDetails(session, payroll.getPayrollId()).orElseThrow());
        });
    }

    /** One page of all payroll records, newest first. */
    public PageResult<PayrollDto> getAllPayroll(int page, int pageSize) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        if (page < 0 || pageSize < 1 || pageSize > 200) {
            throw new ValidationException("Page must be >= 0 and page size between 1 and 200.");
        }
        return HibernateUtil.readOnly(session -> new PageResult<>(
                payrollDao.findPage(session, page, pageSize).stream().map(DtoMapper::toDto).toList(),
                page, pageSize, payrollDao.count(session)));
    }

    // ------------------------------------------------------------------ internals

    /** Calculates, then persists a new DRAFT row. Nothing is persisted if the calculation fails. */
    private Payroll createPayroll(Session session, Employee employee, YearMonth period, BigDecimal otherDeductions,
                                  String username) {
        PayrollResult result = calculate(session, employee, period, otherDeductions);
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayrollMonth(period.getMonthValue());
        payroll.setPayrollYear(period.getYear());
        payroll.setPayrollStatus(PayrollStatus.DRAFT);
        payroll.setGeneratedAt(LocalDateTime.now());
        payroll.setGeneratedBy(username);
        apply(payroll, result);
        payrollDao.save(session, payroll);
        return payroll;
    }

    private PayrollResult calculate(Session session, Employee employee, YearMonth period, BigDecimal otherDeductions) {
        if (!employee.isActive()) {
            throw new InvalidPayrollException("Employee " + employee.getEmployeeCode() + " is inactive.");
        }
        if (employee.getDateOfJoining().isAfter(period.atEndOfMonth())) {
            throw new InvalidPayrollException("Employee " + employee.getEmployeeCode() + " joined on "
                    + employee.getDateOfJoining() + ", after " + DateUtil.formatPeriod(period) + ".");
        }
        SalaryStructure structure = salaryDao.findEffective(session, employee.getEmployeeId(), period.atEndOfMonth())
                .orElseThrow(() -> new InvalidPayrollException("Employee " + employee.getEmployeeCode()
                        + " has no salary structure in force for " + DateUtil.formatPeriod(period) + "."));
        PayrollInput input = new PayrollInput(period, employee.getDateOfJoining(), structure.getComponents(),
                attendanceService.loadRecords(session, employee.getEmployeeId(), period), otherDeductions);
        return calculator.calculate(input);
    }

    /** Copies the calculated figures (the salary snapshot) into the payroll row. */
    private void apply(Payroll p, PayrollResult r) {
        p.setWorkingDays(r.workingDays());
        p.setPresentDays(r.presentDays());
        p.setPaidLeaveDays(r.paidLeaveDays());
        p.setUnpaidLeaveDays(r.unpaidDays());
        p.setPayableDaysBasis(r.payableDaysBasis());
        p.setBasicSalary(r.basicSalary());
        p.setHouseRentAllowance(r.houseRentAllowance());
        p.setMedicalAllowance(r.medicalAllowance());
        p.setConveyanceAllowance(r.conveyanceAllowance());
        p.setOtherAllowances(r.otherAllowances());
        p.setGrossSalary(r.grossSalary());
        p.setProvidentFundDeduction(r.providentFundDeduction());
        p.setProfessionalTax(r.professionalTax());
        p.setUnpaidDeduction(r.unpaidDeduction());
        p.setOtherDeductions(r.otherDeductions());
        p.setTotalDeductions(r.totalDeductions());
        p.setNetSalary(r.netSalary());
    }

    private void finalizeRow(Payroll payroll, AuthenticatedUser user) {
        if (payroll.getPayrollStatus() != PayrollStatus.DRAFT) {
            throw new InvalidPayrollException("Payroll #" + payroll.getPayrollId() + " is already "
                    + payroll.getPayrollStatus() + ".");
        }
        if (payroll.getNetSalary().signum() < 0 && !calculator.getPolicy().isAllowNegativeNet()) {
            throw new InvalidPayrollException("Payroll #" + payroll.getPayrollId() + " has a negative net salary.");
        }
        payroll.setPayrollStatus(PayrollStatus.FINALIZED);
        payroll.setFinalizedAt(LocalDateTime.now());
        payroll.setFinalizedBy(user.username());
    }

    private Payroll load(Session session, Long payrollId) {
        return payrollDao.findByIdWithDetails(session, payrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll", payrollId));
    }

    private void assertVisible(Payroll payroll) {
        AuthenticatedUser user = SecurityContext.requireAuthenticated();
        if (user.isStaff()) {
            return;
        }
        boolean own = user.employeeId() != null && user.employeeId().equals(payroll.getEmployee().getEmployeeId());
        if (!own || payroll.getPayrollStatus() == PayrollStatus.DRAFT) {
            throw new UnauthorizedOperationException("You can only view your own finalized payroll.");
        }
    }

    private void validatePeriod(YearMonth period) {
        if (period == null) {
            throw new ValidationException("Payroll month is required.");
        }
        if (period.isAfter(YearMonth.from(LocalDate.now()).plusMonths(1))) {
            throw new ValidationException("Payroll cannot be generated for a month more than one month ahead.");
        }
        if (period.getYear() < 2000) {
            throw new ValidationException("Payroll year must be 2000 or later.");
        }
    }
}
