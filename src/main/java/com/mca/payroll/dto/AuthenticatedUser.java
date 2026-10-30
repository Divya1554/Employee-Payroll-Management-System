package com.mca.payroll.dto;

import com.mca.payroll.entity.Role;

/** The signed-in user. employeeId is set for accounts linked to an employee. */
public record AuthenticatedUser(Long userId, String username, Role role, Long employeeId) {

    /** ADMIN and HR may work with all employees' data. */
    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.HR;
    }
}
