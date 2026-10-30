package com.mca.payroll.service;

import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.LeaveRequestDao;
import com.mca.payroll.dao.PayrollDao;
import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.dto.LeaveBalanceDto;
import com.mca.payroll.dto.LeaveRequestDto;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.LeaveRequest;
import com.mca.payroll.entity.LeaveStatus;
import com.mca.payroll.entity.LeaveType;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.exception.InvalidLeaveRequestException;
import com.mca.payroll.exception.ResourceNotFoundException;
import com.mca.payroll.exception.UnauthorizedOperationException;
import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.SecurityContext;
import com.mca.payroll.util.WorkingCalendar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Leave management.
 *
 * <p><b>Payroll rule:</b> when a request is approved, every working day in its range is written to the
 * attendance table as PAID_LEAVE (paid types) or UNPAID_LEAVE (type UNPAID). Payroll reads only
 * attendance, so approved leave is counted exactly once and unpaid leave is never deducted twice.</p>
 *
 * <p>Limits: a request must lie within one calendar year (balances are per year); paid leave types
 * have an annual quota (leave.quota.TYPE); a manager cannot approve their own request.</p>
 */
public class LeaveService {

    private static final Logger LOG = LogManager.getLogger(LeaveService.class);

    private final LeaveRequestDao leaveDao;
    private final EmployeeDao employeeDao;
    private final PayrollDao payrollDao;
    private final AttendanceService attendanceService;
    private final PayrollPolicy policy;
    private final AppConfig config;

    public LeaveService(AttendanceService attendanceService) {
        this(new LeaveRequestDao(), new EmployeeDao(), new PayrollDao(), attendanceService,
                PayrollPolicy.fromConfig(AppConfig.getInstance()), AppConfig.getInstance());
    }

    public LeaveService(LeaveRequestDao leaveDao, EmployeeDao employeeDao, PayrollDao payrollDao,
                        AttendanceService attendanceService, PayrollPolicy policy, AppConfig config) {
        this.leaveDao = leaveDao;
        this.employeeDao = employeeDao;
        this.payrollDao = payrollDao;
        this.attendanceService = attendanceService;
        this.policy = policy;
        this.config = config;
    }

    // ------------------------------------------------------------------ submit

    /** Staff may submit for anyone; an EMPLOYEE only for themselves. */
    public LeaveRequestDto submitLeaveRequest(Long employeeId, LeaveType type, LocalDate start, LocalDate end,
                                              String reason) {
        SecurityContext.requireSelfOrStaff(employeeId);
        String text = InputValidator.requiredText(reason, 255, "Reason");
        if (type == null) {
            throw new InvalidLeaveRequestException("Leave type is required.");
        }
        if (start == null || end == null) {
            throw new InvalidLeaveRequestException("Start and end dates are required.");
        }

        return HibernateUtil.inTransaction(session -> {
            Employee employee = employeeDao.findById(session, employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            if (!employee.isActive()) {
                throw new InvalidLeaveRequestException("Employee " + employee.getEmployeeCode() + " is inactive.");
            }
            int days = validateRange(start, end, employee.getDateOfJoining());

            List<LeaveRequest> overlapping = leaveDao.findOverlapping(session, employeeId, start, end);
            if (!overlapping.isEmpty()) {
                LeaveRequest other = overlapping.get(0);
                throw new InvalidLeaveRequestException("This request overlaps leave request #" + other.getLeaveId()
                        + " (" + other.getStartDate() + " to " + other.getEndDate() + ", " + other.getLeaveStatus() + ").");
            }
            checkBalance(session, employeeId, type, start.getYear(), days, 0);

            LeaveRequest request = new LeaveRequest(employee, type, start, end, days, text);
            leaveDao.save(session, request);
            session.flush();
            LOG.info("Leave requested: {} {} {}..{} ({} days)", employee.getEmployeeCode(), type, start, end, days);
            return DtoMapper.toDto(request);
        });
    }

    /** Pure validation of dates; returns the number of working days. Public for unit tests. */
    public int validateRange(LocalDate start, LocalDate end, LocalDate dateOfJoining) {
        if (end.isBefore(start)) {
            throw new InvalidLeaveRequestException("The end date must not be before the start date.");
        }
        if (start.getYear() != end.getYear()) {
            throw new InvalidLeaveRequestException(
                    "A leave request must stay within one calendar year. Submit separate requests for each year.");
        }
        if (start.isBefore(dateOfJoining)) {
            throw new InvalidLeaveRequestException("Leave cannot start before the joining date (" + dateOfJoining + ").");
        }
        int maxBackdate = config.getInt("leave.max.backdate.days", 30);
        if (start.isBefore(LocalDate.now().minusDays(maxBackdate))) {
            throw new InvalidLeaveRequestException("Leave cannot start more than " + maxBackdate + " days in the past.");
        }
        int days = WorkingCalendar.countWorkingDays(start, end, policy.getWeeklyOffs());
        if (days == 0) {
            throw new InvalidLeaveRequestException("The selected dates contain no working days (only weekly offs).");
        }
        int maxDays = config.getInt("leave.max.request.days", 60);
        if (days > maxDays) {
            throw new InvalidLeaveRequestException("A single request may not exceed " + maxDays + " working days.");
        }
        return days;
    }

    // ------------------------------------------------------------------ review

    public LeaveRequestDto approveLeave(Long leaveId, String remarks) {
        AuthenticatedUser reviewer = SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String note = InputValidator.optionalText(remarks, 255, "Remarks");

        return HibernateUtil.inTransaction(session -> {
            LeaveRequest request = loadPending(session, leaveId, reviewer);
            Employee employee = request.getEmployee();
            if (!employee.isActive()) {
                throw new InvalidLeaveRequestException("Employee " + employee.getEmployeeCode() + " is inactive.");
            }
            // Re-check the balance now: other requests may have been approved since submission.
            checkBalance(session, employee.getEmployeeId(), request.getLeaveType(), request.getStartDate().getYear(),
                    request.getNumberOfDays(), request.getNumberOfDays());

            List<LocalDate> days = WorkingCalendar.workingDays(request.getStartDate(), request.getEndDate(),
                    policy.getWeeklyOffs());
            for (LocalDate day : days) {
                if (payrollDao.countNonDraft(session, employee.getEmployeeId(), day.getMonthValue(), day.getYear()) > 0) {
                    throw new InvalidLeaveRequestException("Payroll for " + YearMonth.from(day)
                            + " is already finalized, so leave in that month cannot be approved.");
                }
            }
            AttendanceStatus status = request.getLeaveType().isPaid()
                    ? AttendanceStatus.PAID_LEAVE : AttendanceStatus.UNPAID_LEAVE;
            for (LocalDate day : days) {
                attendanceService.upsert(session, employee, day, status, "Approved leave #" + request.getLeaveId());
            }
            markReviewed(request, LeaveStatus.APPROVED, reviewer, note);
            session.flush();
            LOG.info("Leave approved: #{} by {}", leaveId, reviewer.username());
            return DtoMapper.toDto(request);
        });
    }

    public LeaveRequestDto rejectLeave(Long leaveId, String remarks) {
        AuthenticatedUser reviewer = SecurityContext.requireRole(Role.ADMIN, Role.HR);
        String note = InputValidator.requiredText(remarks, 255, "Rejection reason");
        return HibernateUtil.inTransaction(session -> {
            LeaveRequest request = loadPending(session, leaveId, reviewer);
            markReviewed(request, LeaveStatus.REJECTED, reviewer, note);
            session.flush();
            LOG.info("Leave rejected: #{} by {}", leaveId, reviewer.username());
            return DtoMapper.toDto(request);
        });
    }

    private LeaveRequest loadPending(Session session, Long leaveId, AuthenticatedUser reviewer) {
        LeaveRequest request = leaveDao.findByIdWithEmployee(session, leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", leaveId));
        if (request.getLeaveStatus() != LeaveStatus.PENDING) {
            throw new InvalidLeaveRequestException("Leave request #" + leaveId + " is already "
                    + request.getLeaveStatus() + ".");
        }
        if (reviewer.employeeId() != null && reviewer.employeeId().equals(request.getEmployee().getEmployeeId())) {
            throw new UnauthorizedOperationException("You cannot review your own leave request.");
        }
        return request;
    }

    private void markReviewed(LeaveRequest request, LeaveStatus status, AuthenticatedUser reviewer, String remarks) {
        request.setLeaveStatus(status);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(reviewer.username());
        request.setReviewRemarks(remarks);
    }

    // ------------------------------------------------------------------ queries

    public List<LeaveRequestDto> getAllLeaveRequests() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session ->
                leaveDao.findAllWithEmployee(session).stream().map(DtoMapper::toDto).toList());
    }

    public List<LeaveRequestDto> getPendingLeaveRequests() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session ->
                leaveDao.findByStatus(session, LeaveStatus.PENDING).stream().map(DtoMapper::toDto).toList());
    }

    /** Leave history of one employee (an EMPLOYEE sees only their own). */
    public List<LeaveRequestDto> getLeaveHistory(Long employeeId) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            if (employeeDao.findById(session, employeeId).isEmpty()) {
                throw new EmployeeNotFoundException(employeeId);
            }
            return leaveDao.findByEmployee(session, employeeId).stream().map(DtoMapper::toDto).toList();
        });
    }

    public List<LeaveBalanceDto> getLeaveBalances(Long employeeId, int year) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            if (employeeDao.findById(session, employeeId).isEmpty()) {
                throw new EmployeeNotFoundException(employeeId);
            }
            List<LeaveBalanceDto> balances = new ArrayList<>();
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31);
            for (LeaveType type : LeaveType.values()) {
                int approved = leaveDao.sumDays(session, employeeId, type, LeaveStatus.APPROVED, from, to);
                int pending = leaveDao.sumDays(session, employeeId, type, LeaveStatus.PENDING, from, to);
                Integer quota = quotaFor(type);
                balances.add(new LeaveBalanceDto(type, quota, approved, pending,
                        quota == null ? null : quota - approved - pending));
            }
            return balances;
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Annual quota of a paid type, or null for unlimited (unpaid) leave. */
    public Integer quotaFor(LeaveType type) {
        if (!type.isPaid()) {
            return null;
        }
        return config.getInt("leave.quota." + type.name(), 0);
    }

    /**
     * Throws when the requested days do not fit the remaining quota.
     *
     * @param ownPendingDays days of the request being approved that are already counted as pending
     */
    private void checkBalance(Session session, Long employeeId, LeaveType type, int year, int days,
                              int ownPendingDays) {
        Integer quota = quotaFor(type);
        if (quota == null) {
            return;
        }
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        int approved = leaveDao.sumDays(session, employeeId, type, LeaveStatus.APPROVED, from, to);
        int pending = leaveDao.sumDays(session, employeeId, type, LeaveStatus.PENDING, from, to) - ownPendingDays;
        int available = quota - approved - pending;
        if (days > available) {
            throw new InvalidLeaveRequestException("Insufficient " + type + " leave balance for " + year
                    + ": requested " + days + " day(s), available " + Math.max(available, 0)
                    + " (quota " + quota + ", approved " + approved + ", pending " + pending + ").");
        }
    }
}
