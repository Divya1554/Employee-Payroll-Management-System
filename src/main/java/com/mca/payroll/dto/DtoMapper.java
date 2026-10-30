package com.mca.payroll.dto;

import com.mca.payroll.entity.Attendance;
import com.mca.payroll.entity.Department;
import com.mca.payroll.entity.Employee;
import com.mca.payroll.entity.LeaveRequest;
import com.mca.payroll.entity.Payroll;
import com.mca.payroll.entity.SalaryComponents;
import com.mca.payroll.entity.SalaryStructure;
import com.mca.payroll.entity.User;

/**
 * Converts entities to read-only DTOs. It must be called while the Hibernate session is still
 * open because it touches lazy associations (department, employee). After conversion the
 * console layer never needs the session - this avoids LazyInitializationException and keeps
 * entities out of the presentation layer.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static DepartmentDto toDto(Department d) {
        return new DepartmentDto(d.getDepartmentId(), d.getDepartmentName(), d.getDescription(), d.getLocation());
    }

    public static EmployeeDto toDto(Employee e) {
        Department d = e.getDepartment();
        return new EmployeeDto(e.getEmployeeId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(),
                e.getEmail(), e.getPhone(), e.getDateOfBirth(), e.getDateOfJoining(), e.getDesignation(),
                e.getEmploymentStatus(), d.getDepartmentId(), d.getDepartmentName(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static SalaryStructureDto toDto(SalaryStructure s) {
        Employee e = s.getEmployee();
        SalaryComponents c = s.getComponents();
        return new SalaryStructureDto(s.getSalaryStructureId(), e.getEmployeeId(), e.getEmployeeCode(),
                e.getFullName(), c.getBasicSalary(), c.getHouseRentAllowance(), c.getMedicalAllowance(),
                c.getConveyanceAllowance(), c.getOtherAllowances(), c.getProvidentFundDeduction(),
                c.getProfessionalTax(), c.grossSalary(), s.getEffectiveFrom(), s.getEffectiveTo());
    }

    public static AttendanceDto toDto(Attendance a) {
        Employee e = a.getEmployee();
        return new AttendanceDto(a.getAttendanceId(), e.getEmployeeId(), e.getEmployeeCode(), e.getFullName(),
                a.getAttendanceDate(), a.getAttendanceStatus(), a.getRemarks());
    }

    public static LeaveRequestDto toDto(LeaveRequest l) {
        Employee e = l.getEmployee();
        return new LeaveRequestDto(l.getLeaveId(), e.getEmployeeId(), e.getEmployeeCode(), e.getFullName(),
                l.getLeaveType(), l.getStartDate(), l.getEndDate(), l.getNumberOfDays(), l.getReason(),
                l.getLeaveStatus(), l.getRequestedAt(), l.getReviewedAt(), l.getReviewedBy(), l.getReviewRemarks());
    }

    public static PayrollDto toDto(Payroll p) {
        Employee e = p.getEmployee();
        return new PayrollDto(p.getPayrollId(), e.getEmployeeId(), e.getEmployeeCode(), e.getFullName(),
                e.getDepartment().getDepartmentName(), e.getDesignation(),
                p.getPayrollMonth(), p.getPayrollYear(), p.getWorkingDays(), p.getPresentDays(),
                p.getPaidLeaveDays(), p.getUnpaidLeaveDays(), p.getPayableDaysBasis(),
                p.getBasicSalary(), p.getHouseRentAllowance(), p.getMedicalAllowance(),
                p.getConveyanceAllowance(), p.getOtherAllowances(), p.getGrossSalary(),
                p.getProvidentFundDeduction(), p.getProfessionalTax(), p.getUnpaidDeduction(),
                p.getOtherDeductions(), p.getTotalDeductions(), p.getNetSalary(),
                p.getPayrollStatus(), p.getGeneratedAt(), p.getFinalizedAt(), p.getGeneratedBy());
    }

    public static UserDto toDto(User u) {
        Employee e = u.getEmployee();
        return new UserDto(u.getUserId(), u.getUsername(), u.getRole(), u.isActive(),
                e == null ? null : e.getEmployeeId(), e == null ? null : e.getEmployeeCode(), u.getLastLoginAt());
    }
}
