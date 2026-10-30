package com.mca.payroll.dao;

import com.mca.payroll.entity.User;
import org.hibernate.Session;

import java.util.List;
import java.util.Optional;

/** Database access for login accounts. */
public class UserDao extends BaseDao<User> {

    public UserDao() {
        super(User.class);
    }

    public Optional<User> findByUsername(Session session, String username) {
        return session.createNamedQuery("User.findByUsername", User.class)
                .setParameter("username", username)
                .uniqueResultOptional();
    }

    public Optional<User> findByEmployeeId(Session session, Long employeeId) {
        return session.createQuery("select u from User u where u.employee.employeeId = :employeeId", User.class)
                .setParameter("employeeId", employeeId)
                .uniqueResultOptional();
    }

    public List<User> findAllWithEmployee(Session session) {
        return session.createQuery("select u from User u left join fetch u.employee order by u.username", User.class)
                .list();
    }

    public long countUsers(Session session) {
        Long count = session.createNamedQuery("User.countAll", Long.class).getSingleResult();
        return count == null ? 0L : count;
    }
}
