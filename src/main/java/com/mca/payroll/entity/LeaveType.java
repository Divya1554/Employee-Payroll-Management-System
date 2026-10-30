package com.mca.payroll.entity;

/**
 * Kinds of leave. Paid leave types are limited by an annual quota and are not
 * deducted from salary; UNPAID leave is deducted by the payroll calculator.
 */
public enum LeaveType {
    ANNUAL(true),
    SICK(true),
    CASUAL(true),
    UNPAID(false);

    private final boolean paid;

    LeaveType(boolean paid) {
        this.paid = paid;
    }

    public boolean isPaid() {
        return paid;
    }
}
