package com.mca.payroll.dao;

import com.mca.payroll.entity.Department;
import org.hibernate.Session;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Database access for departments (named queries + parameterised HQL). */
public class DepartmentDao extends BaseDao<Department> {

    public DepartmentDao() {
        super(Department.class);
    }

    public List<Department> findAllOrdered(Session session) {
        return session.createNamedQuery("Department.findAll", Department.class).list();
    }

    /** Exact (case-insensitive) match on the unique department name. */
    public Optional<Department> findByName(Session session, String name) {
        return session.createNamedQuery("Department.findByName", Department.class)
                .setParameter("name", name)
                .uniqueResultOptional();
    }

    /** Partial, case-insensitive search. */
    public List<Department> searchByName(Session session, String fragment) {
        return session.createQuery(
                        "select d from Department d where lower(d.departmentName) like :pattern escape '!' order by d.departmentName",
                        Department.class)
                .setParameter("pattern", "%" + escapeLike(fragment).toLowerCase(Locale.ROOT) + "%")
                .list();
    }

    public long countEmployees(Session session, Long departmentId) {
        Long count = session.createNamedQuery("Employee.countInDepartment", Long.class)
                .setParameter("departmentId", departmentId)
                .getSingleResult();
        return count == null ? 0L : count;
    }

    /** Escapes % and _ (escape character '!') so user text is matched literally inside LIKE patterns. */
    static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
