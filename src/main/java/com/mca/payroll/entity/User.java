package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * A login account (table: users).
 *
 * <p>Only a BCrypt hash is stored - never the password. An account may optionally be linked to an
 * employee (unidirectional, optional One-to-One owned by this side; the unique FK guarantees one
 * account per employee). Employees with role EMPLOYEE use that link to see only their own data.</p>
 */
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_employee", columnNames = "employee_id")
        })
@NamedQueries({
        @NamedQuery(name = "User.findByUsername",
                query = "select u from User u where lower(u.username) = lower(:username)"),
        @NamedQuery(name = "User.countAll", query = "select count(u) from User u")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "employee_id", foreignKey = @ForeignKey(name = "fk_user_employee"))
    private Employee employee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    protected User() {
        // required by Hibernate
    }

    public User(String username, String passwordHash, Role role, Employee employee) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.employee = employee;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Employee getEmployee() { return employee; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return username != null && username.equalsIgnoreCase(other.getUsername());
    }

    @Override
    public int hashCode() {
        return username == null ? User.class.hashCode() : username.toLowerCase(Locale.ROOT).hashCode();
    }

    /** Never prints the password hash. */
    @Override
    public String toString() {
        return "User{id=" + userId + ", username='" + username + "', role=" + role + ", active=" + active + '}';
    }
}
