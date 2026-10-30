package com.mca.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * One attendance record per employee per day (table: attendance).
 * The database unique constraint (employee_id, attendance_date) is the final guard against duplicates;
 * the service also checks first so users get a clear message.
 */
@Entity
@Table(name = "attendance",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_employee_date",
                columnNames = {"employee_id", "attendance_date"}),
        indexes = @Index(name = "idx_attendance_date", columnList = "attendance_date"))
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_employee"))
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private AttendanceStatus attendanceStatus;

    @Column(name = "remarks", length = 255)
    private String remarks;

    protected Attendance() {
        // required by Hibernate
    }

    public Attendance(Employee employee, LocalDate attendanceDate, AttendanceStatus attendanceStatus, String remarks) {
        this.employee = employee;
        this.attendanceDate = attendanceDate;
        this.attendanceStatus = attendanceStatus;
        this.remarks = remarks;
    }

    public Long getAttendanceId() { return attendanceId; }

    public Employee getEmployee() { return employee; }

    public LocalDate getAttendanceDate() { return attendanceDate; }

    public AttendanceStatus getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(AttendanceStatus attendanceStatus) { this.attendanceStatus = attendanceStatus; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Attendance other)) {
            return false;
        }
        return attendanceId != null && attendanceId.equals(other.getAttendanceId());
    }

    @Override
    public int hashCode() {
        return Attendance.class.hashCode();
    }

    @Override
    public String toString() {
        return "Attendance{id=" + attendanceId + ", date=" + attendanceDate + ", status=" + attendanceStatus + '}';
    }
}
