package com.mca.payroll.dto;

/** An employee with unpaid-leave days in a month. */
public record UnpaidLeaveRow(String employeeCode, String employeeName, long unpaidLeaveDays) {
}
