package com.mca.payroll.dao;

import com.mca.payroll.entity.SalaryStructure;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Database access for salary structures (salary revision history). */
public class SalaryStructureDao extends BaseDao<SalaryStructure> {

    public SalaryStructureDao() {
        super(SalaryStructure.class);
    }

    /** All revisions of an employee, newest first. */
    public List<SalaryStructure> findByEmployee(Session session, Long employeeId) {
        return session.createNamedQuery("SalaryStructure.findByEmployee", SalaryStructure.class)
                .setParameter("employeeId", employeeId)
                .list();
    }

    /** The revision in force on the given date, if any. */
    public Optional<SalaryStructure> findEffective(Session session, Long employeeId, LocalDate onDate) {
        return session.createNamedQuery("SalaryStructure.findEffective", SalaryStructure.class)
                .setParameter("employeeId", employeeId)
                .setParameter("onDate", onDate)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    /** The newest revision (highest effectiveFrom), if any. */
    public Optional<SalaryStructure> findLatest(Session session, Long employeeId) {
        return session.createNamedQuery("SalaryStructure.findByEmployee", SalaryStructure.class)
                .setParameter("employeeId", employeeId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    public List<SalaryStructure> findAllWithEmployee(Session session) {
        return session.createQuery(
                        "select s from SalaryStructure s join fetch s.employee e "
                                + "order by e.employeeCode, s.effectiveFrom desc", SalaryStructure.class)
                .list();
    }
}
