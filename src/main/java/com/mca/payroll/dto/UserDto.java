package com.mca.payroll.dto;

import com.mca.payroll.entity.Role;

import java.time.LocalDateTime;

/** Read-only view of a login account (never contains the password hash). */
public record UserDto(Long id, String username, Role role, boolean active, Long employeeId,
                      String employeeCode, LocalDateTime lastLoginAt) {
}
