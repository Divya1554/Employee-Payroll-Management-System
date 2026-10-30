package com.mca.payroll.dao;

import org.hibernate.Session;

import java.util.List;
import java.util.Optional;

/**
 * Generic persistence operations shared by all DAOs.
 *
 * <p>DAOs never open sessions or transactions - the service layer owns the transaction boundary
 * (see HibernateUtil.inTransaction) and passes the active {@link Session} in. This keeps database
 * access separate from business logic and lets one service operation span several DAOs atomically.</p>
 */
public abstract class BaseDao<T> {

    private final Class<T> entityClass;

    protected BaseDao(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public Optional<T> findById(Session session, Long id) {
        return Optional.ofNullable(session.get(entityClass, id));
    }

    /** Lazy proxy: no SELECT until a property is used. Good for setting a foreign key. */
    public T getReference(Session session, Long id) {
        return session.getReference(entityClass, id);
    }

    public void save(Session session, T entity) {
        session.persist(entity);
    }

    /** Copies the state of a detached entity onto the managed instance. */
    public T update(Session session, T entity) {
        return session.merge(entity);
    }

    public void delete(Session session, T entity) {
        session.remove(entity);
    }

    /** The entity class name comes from code, never from user input. */
    public List<T> findAll(Session session) {
        return session.createQuery("from " + entityClass.getSimpleName(), entityClass).list();
    }

    public long count(Session session) {
        Long total = session.createQuery("select count(x) from " + entityClass.getSimpleName() + " x", Long.class)
                .getSingleResult();
        return total == null ? 0L : total;
    }
}
