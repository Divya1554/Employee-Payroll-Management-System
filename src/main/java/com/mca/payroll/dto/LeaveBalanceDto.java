package com.mca.payroll.dto;

import com.mca.payroll.entity.LeaveType;

/** Leave balance of one leave type in one calendar year. quota/available are null for unlimited (unpaid) leave. */
public record LeaveBalanceDto(LeaveType leaveType, Integer quota, int approvedDays, int pendingDays,
                              Integer available) {
}
