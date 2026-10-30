package com.mca.payroll.service;

import com.mca.payroll.dao.AttendanceDao;
import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.PayrollDao;
import com.mca.payroll.dto.AttendanceDto;
import com.mca.payroll.dto.AttendanceSummaryDto;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.entity.Attendance;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.BusinessRuleViolationException;
import com.mca.payroll.exception.DuplicateRecordException;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.exception.ResourceNotFoundException;
import com.mca.payroll.exception.ValidationException;
import com.mca.payroll.util.AppConfig;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.InputValidator;
import com.mca.payroll.util.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Attendance rules. ADMIN/HR record and change attendance; an EMPLOYEE may read their own.
 * A month whose payroll is already FINALIZED or PAID is locked against changes.
 */
public class AttendanceService {

    private static final Logger LOG = LogManager.getLogger(AttendanceService.class);

    private final AttendanceDao attendanceDao;
    private final EmployeeDao employeeDao;
    private final PayrollDao payrollDao;
    private final PayrollPolicy policy;

    public AttendanceService() {
        this(new AttendanceDao(), new EmployeeDao(), new PayrollDao(), PayrollPolicy.fromConfig(AppConfig.getInstance()));
    }

    public AttendanceService(AttendanceDao attendanceDao, EmployeeDao employeeDao, PayrollDao payrollDao,
                             PayrollPolicy policy) {
        this.attendanceDao = attendanceDao;
        this.employeeDao = employeeDao;
        this.payrollDao = payrollDao;
        this.policy = policy;
    }

    public AttendanceDto recordAttendance(Long employeeId, LocalDate date, AttendanceStatus status, String remarks) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        validateStatusAndDate(date, status);
        String note = InputValidator.optionalText(remarks, 255, "Remarks");

        return HibernateUtil.inTransaction(session -> {
            Employee employee = employeeDao.findById(session, employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            assertEmployeeCanHaveAttendance(employee, date);
            assertPeriodOpen(session, employeeId, date);
            if (attendanceDao.findByEmployeeAndDate(session, employeeId, date).isPresent()) {
                throw new DuplicateRecordException("Attendance for " + employee.getEmployeeCode() + " on " + date
                        + " already exists. Use 'Update attendance' to change it.");
            }
            Attendance attendance = new Attendance(employee, date, status, note);
            attendanceDao.save(session, attendance);
            session.flush();
            LOG.info("Attendance recorded: {} {} {}", employee.getEmployeeCode(), date, status);
            return DtoMapper.toDto(attendance);
        });
    }

    public AttendanceDto updateAttendance(Long attendanceId, AttendanceStatus status, String remarks) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        if (status == null) {
            throw new ValidationException("Attendance status is required.");
        }
        String note = InputValidator.optionalText(remarks, 255, "Remarks");
        return HibernateUtil.inTransaction(session -> {
            Attendance attendance = attendanceDao.findByIdWithEmployee(session, attendanceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Attendance record", attendanceId));
            assertPeriodOpen(session, attendance.getEmployee().getEmployeeId(), attendance.getAttendanceDate());
            attendance.setAttendanceStatus(status);
            attendance.setRemarks(note);
            session.flush();
            LOG.info("Attendance updated: id {} -> {}", attendanceId, status);
            return DtoMapper.toDto(attendance);
        });
    }

    /**
     * Records an approved leave day as PAID_LEAVE or UNPAID_LEAVE (creating or replacing that day's record).
     * Public entry point of the "Record approved leave" menu option.
     */
    public AttendanceDto recordApprovedLeave(Long employeeId, LocalDate date, boolean paid, String remarks) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        validateStatusAndDate(date, AttendanceStatus.PAID_LEAVE);
        String note = InputValidator.optionalText(remarks, 255, "Remarks");
        return HibernateUtil.inTransaction(session -> {
            Employee employee = employeeDao.findById(session, employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            assertEmployeeCanHaveAttendance(employee, date);
            assertPeriodOpen(session, employeeId, date);
            Attendance saved = upsert(session, employee, date,
                    paid ? AttendanceStatus.PAID_LEAVE : AttendanceStatus.UNPAID_LEAVE,
                    note == null ? "Approved leave" : note);
            return DtoMapper.toDto(saved);
        });
    }

    /** Creates the record or replaces its status. Used inside an already-open transaction. */
    Attendance upsert(Session session, Employee employee, LocalDate date, AttendanceStatus status, String remarks) {
        Optional<Attendance> existing = attendanceDao.findByEmployeeAndDate(session, employee.getEmployeeId(), date);
        if (existing.isPresent()) {
            Attendance attendance = existing.get();
            attendance.setAttendanceStatus(status);
            attendance.setRemarks(remarks);
            return attendance;
        }
        Attendance attendance = new Attendance(employee, date, status, remarks);
        attendanceDao.save(session, attendance);
        return attendance;
    }

    public List<AttendanceDto> getAttendanceByEmployee(Long employeeId) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            requireEmployee(session, employeeId);
            return attendanceDao.findByEmployee(session, employeeId).stream().map(DtoMapper::toDto).toList();
        });
    }

    public List<AttendanceDto> getAttendanceForMonth(Long employeeId, YearMonth month) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            requireEmployee(session, employeeId);
            return attendanceDao.findByEmployeeBetween(session, employeeId, month.atDay(1), month.atEndOfMonth())
                    .stream().map(DtoMapper::toDto).toList();
        });
    }

    /** Absent days (explicit ABSENT records) of one employee in one month. */
    public List<AttendanceDto> getAbsentDays(Long employeeId, YearMonth month) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            requireEmployee(session, employeeId);
            return attendanceDao.findByEmployeeBetweenWithStatus(session, employeeId, month.atDay(1),
                    month.atEndOfMonth(), AttendanceStatus.ABSENT).stream().map(DtoMapper::toDto).toList();
        });
    }

    /** Monthly summary computed with the same rules the payroll uses. */
    public AttendanceSummaryDto getMonthlySummary(Long employeeId, YearMonth month) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            Employee employee = requireEmployee(session, employeeId);
            Map<LocalDate, AttendanceStatus> records = loadRecords(session, employeeId, month);
            return AttendanceCalculator.summarize(month, employee.getDateOfJoining(), records, policy);
        });
    }

    /** date -> status map for one month (used by the payroll service as well). */
    Map<LocalDate, AttendanceStatus> loadRecords(Session session, Long employeeId, YearMonth month) {
        Map<LocalDate, AttendanceStatus> map = new HashMap<>();
        for (Attendance a : attendanceDao.findByEmployeeBetween(session, employeeId, month.atDay(1), month.atEndOfMonth())) {
            map.put(a.getAttendanceDate(), a.getAttendanceStatus());
        }
        return map;
    }

    /** Count of records per status for one month - a small HQL-free helper for the console. */
    public Map<AttendanceStatus, Integer> countByStatus(Long employeeId, YearMonth month) {
        SecurityContext.requireSelfOrStaff(employeeId);
        return HibernateUtil.readOnly(session -> {
            Map<AttendanceStatus, Integer> counts = new EnumMap<>(AttendanceStatus.class);
            for (AttendanceStatus s : AttendanceStatus.values()) {
                counts.put(s, 0);
            }
            loadRecords(session, employeeId, month).values().forEach(s -> counts.merge(s, 1, Integer::sum));
            return counts;
        });
    }

    // ------------------------------------------------------------------ helpers

    private Employee requireEmployee(Session session, Long employeeId) {
        return employeeDao.findById(session, employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private void validateStatusAndDate(LocalDate date, AttendanceStatus status) {
        if (date == null) {
            throw new ValidationException("Attendance date is required.");
        }
        if (status == null) {
            throw new ValidationException("Attendance status is required.");
        }
        if (date.isAfter(LocalDate.now().plusDays(60))) {
            throw new ValidationException("Attendance cannot be recorded more than 60 days ahead.");
        }
    }

    private void assertEmployeeCanHaveAttendance(Employee employee, LocalDate date) {
        if (!employee.isActive()) {
            throw new BusinessRuleViolationException("Employee " + employee.getEmployeeCode() + " is inactive.");
        }
        if (date.isBefore(employee.getDateOfJoining())) {
            throw new ValidationException("Attendance cannot be recorded before the joining date ("
                    + employee.getDateOfJoining() + ").");
        }
    }

    /** Refuses changes in a month whose payroll is FINALIZED or PAID. */
    void assertPeriodOpen(Session session, Long employeeId, LocalDate date) {
        if (payrollDao.countNonDraft(session, employeeId, date.getMonthValue(), date.getYear()) > 0) {
            throw new BusinessRuleViolationException("Payroll for " + YearMonth.from(date)
                    + " is already finalized for this employee, so attendance for that month is locked.");
        }
    }
}
