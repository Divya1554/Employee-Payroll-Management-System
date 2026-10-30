package com.mca.payroll.entity;

/** Life-cycle of a payroll record: DRAFT -> FINALIZED -> PAID. */
public enum PayrollStatus {
    DRAFT,
    FINALIZED,
    PAID
}
