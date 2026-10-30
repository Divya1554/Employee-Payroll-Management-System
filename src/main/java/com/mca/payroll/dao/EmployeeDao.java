package com.mca.payroll.dao;

import com.mca.payroll.dto.EmployeeFilter;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.EmploymentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Database access for employees. Demonstrates named queries, fetch joins (no N+1 on the
 * department), pagination, sorting, parameterised HQL and the Criteria API for dynamic filters.
 */
public class EmployeeDao extends BaseDao<Employee> {

    public EmployeeDao() {
        super(Employee.class);
    }

    public Optional<Employee> findByCode(Session session, String code) {
        return session.createNamedQuery("Employee.findByCode", Employee.class)
                .setParameter("code", code)
                .uniqueResultOptional();
    }

    public Optional<Employee> findByEmail(Session session, String email) {
        return session.createNamedQuery("Employee.findByEmail", Employee.class)
                .setParameter("email", email)
                .uniqueResultOptional();
    }

    /** Loads the employee together with its department in one SELECT (fetch join). */
    public Optional<Employee> findByIdWithDepartment(Session session, Long id) {
        return session.createQuery(
                        "select e from Employee e join fetch e.department where e.employeeId = :id", Employee.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    /** One page of employees, sorted by code. Page numbers start at 0. */
    public List<Employee> findPage(Session session, int page, int pageSize) {
        return session.createQuery(
                        "select e from Employee e join fetch e.department order by e.employeeCode", Employee.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .list();
    }

    public List<Employee> searchByName(Session session, String fragment) {
        String pattern = "%" + DepartmentDao.escapeLike(fragment).toLowerCase(Locale.ROOT) + "%";
        return session.createQuery(
                        "select e from Employee e join fetch e.department "
                                + "where lower(e.firstName) like :pattern escape '!' or lower(e.lastName) like :pattern escape '!' "
                                + "or lower(concat(e.firstName, ' ', e.lastName)) like :pattern escape '!' "
                                + "order by e.firstName, e.lastName", Employee.class)
                .setParameter("pattern", pattern)
                .list();
    }

    public List<Employee> findByDepartment(Session session, Long departmentId) {
        return session.createQuery(
                        "select e from Employee e join fetch e.department d "
                                + "where d.departmentId = :departmentId order by e.employeeCode", Employee.class)
                .setParameter("departmentId", departmentId)
                .list();
    }

    public List<Employee> findByDesignation(Session session, String designation) {
        return session.createNamedQuery("Employee.findByDesignation", Employee.class)
                .setParameter("designation", designation)
                .list();
    }

    public List<Employee> findByJoiningDateBetween(Session session, LocalDate from, LocalDate to) {
        return session.createQuery(
                        "select e from Employee e join fetch e.department "
                                + "where e.dateOfJoining between :fromDate and :toDate order by e.dateOfJoining, e.employeeCode",
                        Employee.class)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .list();
    }

    /** Active employees who had joined on or before the end of the payroll period. */
    public List<Employee> findActiveJoinedOnOrBefore(Session session, LocalDate periodEnd) {
        return session.createQuery(
                        "select e from Employee e join fetch e.department "
                                + "where e.employmentStatus = :status and e.dateOfJoining <= :periodEnd "
                                + "order by e.employeeCode", Employee.class)
                .setParameter("status", EmploymentStatus.ACTIVE)
                .setParameter("periodEnd", periodEnd)
                .list();
    }

    /**
     * Dynamic search with the Criteria API: only the filter fields that are set become predicates.
     */
    public List<Employee> search(Session session, EmployeeFilter filter) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> root = query.from(Employee.class);
        root.fetch("department");

        List<Predicate> predicates = new ArrayList<>();
        if (filter.nameFragment() != null && !filter.nameFragment().isBlank()) {
            String pattern = "%" + DepartmentDao.escapeLike(filter.nameFragment().trim()).toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.<String>get("firstName")), pattern, '!'),
                    cb.like(cb.lower(root.<String>get("lastName")), pattern, '!')));
        }
        if (filter.departmentId() != null) {
            predicates.add(cb.equal(root.get("department").get("departmentId"), filter.departmentId()));
        }
        if (filter.designation() != null && !filter.designation().isBlank()) {
            predicates.add(cb.equal(cb.lower(root.<String>get("designation")),
                    filter.designation().trim().toLowerCase(Locale.ROOT)));
        }
        if (filter.status() != null) {
            predicates.add(cb.equal(root.get("employmentStatus"), filter.status()));
        }
        if (filter.joinedFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("dateOfJoining"), filter.joinedFrom()));
        }
        if (filter.joinedTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("dateOfJoining"), filter.joinedTo()));
        }

        query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("employeeCode")));
        return session.createQuery(query).list();
    }

    /** True when the employee has dependent history rows (payroll, attendance, leave, salary, user). */
    public long countPayroll(Session session, Long employeeId) {
        return scalar(session, "select count(p) from Payroll p where p.employee.employeeId = :id", employeeId);
    }

    public long countAttendance(Session session, Long employeeId) {
        return scalar(session, "select count(a) from Attendance a where a.employee.employeeId = :id", employeeId);
    }

    public long countLeaveRequests(Session session, Long employeeId) {
        return scalar(session, "select count(l) from LeaveRequest l where l.employee.employeeId = :id", employeeId);
    }

    public long countSalaryStructures(Session session, Long employeeId) {
        return scalar(session, "select count(s) from SalaryStructure s where s.employee.employeeId = :id", employeeId);
    }

    private long scalar(Session session, String hql, Long employeeId) {
        Long value = session.createQuery(hql, Long.class).setParameter("id", employeeId).getSingleResult();
        return value == null ? 0L : value;
    }
}
