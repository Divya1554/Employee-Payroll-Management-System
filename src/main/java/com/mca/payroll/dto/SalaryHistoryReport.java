package com.mca.payroll.dto;

import java.util.List;

/** Salary revisions plus monthly payroll history of one employee. */
public record SalaryHistoryReport(EmployeeDto employee, List<SalaryStructureDto> structures,
                                  List<PayrollDto> payrolls) {
}
