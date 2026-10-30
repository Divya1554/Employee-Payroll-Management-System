package com.mca.payroll.service;

import com.mca.payroll.dao.EmployeeDao;
import com.mca.payroll.dao.LeaveRequestDao;
import com.mca.payroll.dao.PayrollDao;
import com.mca.payroll.dao.SalaryStructureDao;
import com.mca.payroll.dto.DepartmentEmployeeCount;
import com.mca.payroll.dto.DepartmentPayrollExpense;
import com.mca.payroll.dto.DtoMapper;
import com.mca.payroll.dto.EmployeeCountReport;
import com.mca.payroll.dto.LeaveRequestDto;
import com.mca.payroll.dto.MonthlyPayrollSummary;
import com.mca.payroll.dto.PayrollDto;
import com.mca.payroll.dto.PayrollStatusCount;
import com.mca.payroll.dto.SalaryHistoryReport;
import com.mca.payroll.dto.SalaryStructureDto;
import com.mca.payroll.dto.UnpaidLeaveRow;
import com.mca.payroll.entity.AttendanceStatus;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.EmploymentStatus;
import com.mca.payroll.entity.LeaveStatus;
import com.mca.payroll.entity.PayrollStatus;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.EmployeeNotFoundException;
import com.mca.payroll.util.HibernateUtil;
import com.mca.payroll.util.MoneyUtil;
import com.mca.payroll.util.SecurityContext;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Management reports built with HQL aggregate queries (COUNT, SUM, GROUP BY, HAVING).
 * Only ADMIN and HR may run them.
 */
public class ReportService {

    private final EmployeeDao employeeDao;
    private final LeaveRequestDao leaveDao;
    private final PayrollDao payrollDao;
    private final SalaryStructureDao salaryDao;

    public ReportService() {
        this(new EmployeeDao(), new LeaveRequestDao(), new PayrollDao(), new SalaryStructureDao());
    }

    public ReportService(EmployeeDao employeeDao, LeaveRequestDao leaveDao, PayrollDao payrollDao,
                         SalaryStructureDao salaryDao) {
        this.employeeDao = employeeDao;
        this.leaveDao = leaveDao;
        this.payrollDao = payrollDao;
        this.salaryDao = salaryDao;
    }

    /** Total, active and inactive employees using one GROUP BY query. */
    public EmployeeCountReport employeeCounts() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> {
            List<Object[]> rows = session.createQuery(
                    "select e.employmentStatus, count(e) from Employee e group by e.employmentStatus", Object[].class)
                    .list();
            long active = 0;
            long inactive = 0;
            for (Object[] row : rows) {
                long count = ((Number) row[1]).longValue();
                if (row[0] == EmploymentStatus.ACTIVE) {
                    active = count;
                } else {
                    inactive = count;
                }
            }
            return new EmployeeCountReport(active + inactive, active, inactive);
        });
    }

    /** Employees per department, including departments with none (LEFT JOIN). */
    public List<DepartmentEmployeeCount> employeesByDepartment() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> session.createQuery(
                        "select d.departmentName, count(e) from Department d left join d.employees e "
                                + "group by d.departmentId, d.departmentName order by d.departmentName", Object[].class)
                .list().stream()
                .map(r -> new DepartmentEmployeeCount((String) r[0], ((Number) r[1]).longValue()))
                .toList());
    }

    /** Totals of all payroll records of one month (zeros when there are none). */
    public MonthlyPayrollSummary monthlyPayrollSummary(YearMonth period) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> {
            Object[] row = session.createQuery(
                            "select count(p), sum(p.grossSalary), sum(p.totalDeductions), sum(p.netSalary) "
                                    + "from Payroll p where p.payrollMonth = :periodMonth and p.payrollYear = :periodYear",
                            Object[].class)
                    .setParameter("periodMonth", period.getMonthValue())
                    .setParameter("periodYear", period.getYear())
                    .getSingleResult();
            return new MonthlyPayrollSummary(period, ((Number) row[0]).longValue(), money(row[1]), money(row[2]), money(row[3]));
        });
    }

    /** Salary revisions and monthly payroll history of one employee. */
    public SalaryHistoryReport salaryHistory(Long employeeId) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> {
            Employee employee = employeeDao.findByIdWithDepartment(session, employeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            List<SalaryStructureDto> structures = salaryDao.findByEmployee(session, employeeId)
                    .stream().map(DtoMapper::toDto).toList();
            List<PayrollDto> payrolls = payrollDao.findByEmployee(session, employeeId)
                    .stream().map(DtoMapper::toDto).toList();
            return new SalaryHistoryReport(DtoMapper.toDto(employee), structures, payrolls);
        });
    }

    /** Department-wise payroll expense for one month (GROUP BY department). */
    public List<DepartmentPayrollExpense> departmentPayrollExpenses(YearMonth period) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> session.createQuery(
                        "select d.departmentName, count(p), sum(p.grossSalary), sum(p.netSalary) "
                                + "from Payroll p join p.employee e join e.department d "
                                + "where p.payrollMonth = :periodMonth and p.payrollYear = :periodYear "
                                + "group by d.departmentId, d.departmentName order by d.departmentName", Object[].class)
                .setParameter("periodMonth", period.getMonthValue())
                .setParameter("periodYear", period.getYear())
                .list().stream()
                .map(r -> new DepartmentPayrollExpense((String) r[0], ((Number) r[1]).longValue(), money(r[2]), money(r[3])))
                .toList());
    }

    /** Employees with UNPAID_LEAVE days in the month (GROUP BY ... HAVING). */
    public List<UnpaidLeaveRow> employeesWithUnpaidLeave(YearMonth period) {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> session.createQuery(
                        "select e.employeeCode, e.firstName, e.lastName, count(a) "
                                + "from Attendance a join a.employee e "
                                + "where a.attendanceStatus = :status and a.attendanceDate between :fromDate and :toDate "
                                + "group by e.employeeId, e.employeeCode, e.firstName, e.lastName "
                                + "having count(a) > 0 order by e.employeeCode", Object[].class)
                .setParameter("status", AttendanceStatus.UNPAID_LEAVE)
                .setParameter("fromDate", period.atDay(1))
                .setParameter("toDate", period.atEndOfMonth())
                .list().stream()
                .map(r -> new UnpaidLeaveRow((String) r[0], r[1] + " " + r[2], ((Number) r[3]).longValue()))
                .toList());
    }

    public List<LeaveRequestDto> pendingLeaveRequests() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session ->
                leaveDao.findByStatus(session, LeaveStatus.PENDING).stream().map(DtoMapper::toDto).toList());
    }

    /** Number of payroll records and net total per status. */
    public List<PayrollStatusCount> payrollByStatus() {
        SecurityContext.requireRole(Role.ADMIN, Role.HR);
        return HibernateUtil.readOnly(session -> {
            List<Object[]> rows = session.createQuery(
                    "select p.payrollStatus, count(p), sum(p.netSalary) from Payroll p "
                            + "group by p.payrollStatus order by p.payrollStatus", Object[].class).list();
            return rows.stream()
                    .map(r -> new PayrollStatusCount((PayrollStatus) r[0], ((Number) r[1]).longValue(), money(r[2])))
                    .toList();
        });
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : MoneyUtil.nz((BigDecimal) value);
    }
}
