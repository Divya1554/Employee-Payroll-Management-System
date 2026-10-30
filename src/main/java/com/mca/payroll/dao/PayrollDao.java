package com.mca.payroll.dao;

import com.mca.payroll.entity.Payroll;
import com.mca.payroll.entity.PayrollStatus;
import org.hibernate.Session;

import java.util.List;
import java.util.Optional;

/** Database access for payroll records. */
public class PayrollDao extends BaseDao<Payroll> {

    private static final String FETCH_EMPLOYEE_AND_DEPARTMENT =
            "select p from Payroll p join fetch p.employee e join fetch e.department ";

    public PayrollDao() {
        super(Payroll.class);
    }

    public Optional<Payroll> findByEmployeeAndPeriod(Session session, Long employeeId, int month, int year) {
        return session.createNamedQuery("Payroll.findByEmployeeAndPeriod", Payroll.class)
                .setParameter("employeeId", employeeId)
                .setParameter("periodMonth", month)
                .setParameter("periodYear", year)
                .uniqueResultOptional();
    }

    public Optional<Payroll> findByIdWithDetails(Session session, Long payrollId) {
        return session.createQuery(FETCH_EMPLOYEE_AND_DEPARTMENT + "where p.payrollId = :id", Payroll.class)
                .setParameter("id", payrollId)
                .uniqueResultOptional();
    }

    /** Payroll history of one employee, newest month first. */
    public List<Payroll> findByEmployee(Session session, Long employeeId) {
        return session.createQuery(
                        FETCH_EMPLOYEE_AND_DEPARTMENT + "where e.employeeId = :employeeId "
                                + "order by p.payrollYear desc, p.payrollMonth desc", Payroll.class)
                .setParameter("employeeId", employeeId)
                .list();
    }

    public List<Payroll> findByPeriod(Session session, int month, int year) {
        return session.createQuery(
                        FETCH_EMPLOYEE_AND_DEPARTMENT + "where p.payrollMonth = :periodMonth and p.payrollYear = :periodYear "
                                + "order by e.employeeCode", Payroll.class)
                .setParameter("periodMonth", month)
                .setParameter("periodYear", year)
                .list();
    }

    public List<Payroll> findByPeriodAndStatus(Session session, int month, int year, PayrollStatus status) {
        return session.createQuery(
                        FETCH_EMPLOYEE_AND_DEPARTMENT + "where p.payrollMonth = :periodMonth and p.payrollYear = :periodYear "
                                + "and p.payrollStatus = :status order by e.employeeCode", Payroll.class)
                .setParameter("periodMonth", month)
                .setParameter("periodYear", year)
                .setParameter("status", status)
                .list();
    }

    public List<Payroll> findByStatus(Session session, PayrollStatus status) {
        return session.createQuery(
                        FETCH_EMPLOYEE_AND_DEPARTMENT + "where p.payrollStatus = :status "
                                + "order by p.payrollYear desc, p.payrollMonth desc, e.employeeCode", Payroll.class)
                .setParameter("status", status)
                .list();
    }

    /** One page of all payroll records, newest period first. Page numbers start at 0. */
    public List<Payroll> findPage(Session session, int page, int pageSize) {
        return session.createQuery(
                        FETCH_EMPLOYEE_AND_DEPARTMENT + "order by p.payrollYear desc, p.payrollMonth desc, e.employeeCode",
                        Payroll.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .list();
    }

    /** Payroll rows of the employee that are no longer drafts and fall in the given months. */
    public long countNonDraft(Session session, Long employeeId, int month, int year) {
        Long count = session.createQuery(
                        "select count(p) from Payroll p where p.employee.employeeId = :employeeId "
                                + "and p.payrollMonth = :periodMonth and p.payrollYear = :periodYear "
                                + "and p.payrollStatus <> :draft", Long.class)
                .setParameter("employeeId", employeeId)
                .setParameter("periodMonth", month)
                .setParameter("periodYear", year)
                .setParameter("draft", PayrollStatus.DRAFT)
                .getSingleResult();
        return count == null ? 0L : count;
    }
}
