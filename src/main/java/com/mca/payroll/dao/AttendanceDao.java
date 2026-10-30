package com.mca.payroll.dao;

import com.mca.payroll.entity.Attendance;
import com.mca.payroll.entity.AttendanceStatus;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Database access for attendance records. */
public class AttendanceDao extends BaseDao<Attendance> {

    public AttendanceDao() {
        super(Attendance.class);
    }

    public Optional<Attendance> findByEmployeeAndDate(Session session, Long employeeId, LocalDate date) {
        return session.createQuery(
                        "select a from Attendance a where a.employee.employeeId = :employeeId "
                                + "and a.attendanceDate = :onDate", Attendance.class)
                .setParameter("employeeId", employeeId)
                .setParameter("onDate", date)
                .uniqueResultOptional();
    }

    public Optional<Attendance> findByIdWithEmployee(Session session, Long attendanceId) {
        return session.createQuery(
                        "select a from Attendance a join fetch a.employee where a.attendanceId = :id", Attendance.class)
                .setParameter("id", attendanceId)
                .uniqueResultOptional();
    }

    public List<Attendance> findByEmployee(Session session, Long employeeId) {
        return session.createQuery(
                        "select a from Attendance a join fetch a.employee where a.employee.employeeId = :employeeId "
                                + "order by a.attendanceDate desc", Attendance.class)
                .setParameter("employeeId", employeeId)
                .list();
    }

    /** Records of one employee between two dates (inclusive), oldest first. */
    public List<Attendance> findByEmployeeBetween(Session session, Long employeeId, LocalDate from, LocalDate to) {
        return session.createQuery(
                        "select a from Attendance a join fetch a.employee where a.employee.employeeId = :employeeId "
                                + "and a.attendanceDate between :fromDate and :toDate order by a.attendanceDate", Attendance.class)
                .setParameter("employeeId", employeeId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .list();
    }

    public List<Attendance> findByEmployeeBetweenWithStatus(Session session, Long employeeId, LocalDate from,
                                                            LocalDate to, AttendanceStatus status) {
        return session.createQuery(
                        "select a from Attendance a join fetch a.employee where a.employee.employeeId = :employeeId "
                                + "and a.attendanceDate between :fromDate and :toDate and a.attendanceStatus = :status "
                                + "order by a.attendanceDate", Attendance.class)
                .setParameter("employeeId", employeeId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .setParameter("status", status)
                .list();
    }
}
