package com.mca.payroll.dto;

import com.mca.payroll.entity.LeaveStatus;
import com.mca.payroll.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only view of a leave request. */
public record LeaveRequestDto(Long id, Long employeeId, String employeeCode, String employeeName,
                              LeaveType leaveType, LocalDate startDate, LocalDate endDate, int numberOfDays,
                              String reason, LeaveStatus status, LocalDateTime requestedAt,
                              LocalDateTime reviewedAt, String reviewedBy, String reviewRemarks) {
}
