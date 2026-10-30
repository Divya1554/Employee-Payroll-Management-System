package com.mca.payroll.dao;

import com.mca.payroll.entity.LeaveRequest;
import com.mca.payroll.entity.LeaveStatus;
import com.mca.payroll.entity.LeaveType;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Database access for leave requests. */
public class LeaveRequestDao extends BaseDao<LeaveRequest> {

    public LeaveRequestDao() {
        super(LeaveRequest.class);
    }

    public Optional<LeaveRequest> findByIdWithEmployee(Session session, Long leaveId) {
        return session.createQuery(
                        "select l from LeaveRequest l join fetch l.employee where l.leaveId = :id", LeaveRequest.class)
                .setParameter("id", leaveId)
                .uniqueResultOptional();
    }

    public List<LeaveRequest> findAllWithEmployee(Session session) {
        return session.createQuery(
                        "select l from LeaveRequest l join fetch l.employee order by l.startDate desc, l.leaveId desc",
                        LeaveRequest.class)
                .list();
    }

    public List<LeaveRequest> findByStatus(Session session, LeaveStatus status) {
        return session.createQuery(
                        "select l from LeaveRequest l join fetch l.employee where l.leaveStatus = :status "
                                + "order by l.startDate, l.leaveId", LeaveRequest.class)
                .setParameter("status", status)
                .list();
    }

    public List<LeaveRequest> findByEmployee(Session session, Long employeeId) {
        return session.createQuery(
                        "select l from LeaveRequest l join fetch l.employee where l.employee.employeeId = :employeeId "
                                + "order by l.startDate desc, l.leaveId desc", LeaveRequest.class)
                .setParameter("employeeId", employeeId)
                .list();
    }

    /** Pending or approved requests of the employee that touch the date range [start, end]. */
    public List<LeaveRequest> findOverlapping(Session session, Long employeeId, LocalDate start, LocalDate end) {
        return session.createQuery(
                        "select l from LeaveRequest l where l.employee.employeeId = :employeeId "
                                + "and l.leaveStatus in (:statuses) and l.startDate <= :rangeEnd and l.endDate >= :rangeStart",
                        LeaveRequest.class)
                .setParameter("employeeId", employeeId)
                .setParameterList("statuses", List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))
                .setParameter("rangeStart", start)
                .setParameter("rangeEnd", end)
                .list();
    }

    /** Total working days of requests with the given status and type that start within [from, to]. */
    public int sumDays(Session session, Long employeeId, LeaveType type, LeaveStatus status,
                       LocalDate from, LocalDate to) {
        Long total = session.createQuery(
                        "select sum(l.numberOfDays) from LeaveRequest l where l.employee.employeeId = :employeeId "
                                + "and l.leaveType = :leaveType and l.leaveStatus = :status "
                                + "and l.startDate between :fromDate and :toDate", Long.class)
                .setParameter("employeeId", employeeId)
                .setParameter("leaveType", type)
                .setParameter("status", status)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getSingleResult();
        return total == null ? 0 : total.intValue();
    }
}
