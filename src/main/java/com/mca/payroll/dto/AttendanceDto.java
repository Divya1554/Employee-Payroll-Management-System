package com.mca.payroll.dto;

import com.mca.payroll.entity.AttendanceStatus;

import java.time.LocalDate;

/** Read-only view of one attendance record. */
public record AttendanceDto(Long id, Long employeeId, String employeeCode, String employeeName,
                            LocalDate date, AttendanceStatus status, String remarks) {
}
