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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A leave request (table: leave_requests). {@code numberOfDays} counts working days only
 * (weekly offs are excluded, see payroll.weekly.off).
 */
@Entity
@Table(name = "leave_requests",
        indexes = {
                @Index(name = "idx_leave_employee_start", columnList = "employee_id, start_date"),
                @Index(name = "idx_leave_status", columnList = "leave_status")
        })
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_id")
    private Long leaveId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_leave_employee"))
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "number_of_days", nullable = false)
    private Integer numberOfDays;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "leave_status", nullable = false, length = 20)
    private LeaveStatus leaveStatus = LeaveStatus.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    @Column(name = "review_remarks", length = 255)
    private String reviewRemarks;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected LeaveRequest() {
        // required by Hibernate
    }

    public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                        int numberOfDays, String reason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfDays = numberOfDays;
        this.reason = reason;
        this.leaveStatus = LeaveStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }

    public Long getLeaveId() { return leaveId; }

    public Employee getEmployee() { return employee; }

    public LeaveType getLeaveType() { return leaveType; }

    public LocalDate getStartDate() { return startDate; }

    public LocalDate getEndDate() { return endDate; }

    public Integer getNumberOfDays() { return numberOfDays; }

    public String getReason() { return reason; }

    public LeaveStatus getLeaveStatus() { return leaveStatus; }
    public void setLeaveStatus(LeaveStatus leaveStatus) { this.leaveStatus = leaveStatus; }

    public LocalDateTime getRequestedAt() { return requestedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewRemarks() { return reviewRemarks; }
    public void setReviewRemarks(String reviewRemarks) { this.reviewRemarks = reviewRemarks; }

    public Integer getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LeaveRequest other)) {
            return false;
        }
        return leaveId != null && leaveId.equals(other.getLeaveId());
    }

    @Override
    public int hashCode() {
        return LeaveRequest.class.hashCode();
    }

    @Override
    public String toString() {
        return "LeaveRequest{id=" + leaveId + ", type=" + leaveType + ", " + startDate + " to " + endDate
                + ", status=" + leaveStatus + '}';
    }
}
