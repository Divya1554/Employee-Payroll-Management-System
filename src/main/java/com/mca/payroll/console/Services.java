package com.mca.payroll.console;

import com.mca.payroll.service.AttendanceService;
import com.mca.payroll.service.AuthService;
import com.mca.payroll.service.DepartmentService;
import com.mca.payroll.service.EmployeeService;
import com.mca.payroll.service.LeaveService;
import com.mca.payroll.service.PayrollService;
import com.mca.payroll.service.PayslipService;
import com.mca.payroll.service.ReportService;
import com.mca.payroll.service.SalaryService;
import com.mca.payroll.service.UserService;

/** Creates the service objects once and shares them with all menus (simple manual dependency injection). */
public class Services {

    public final AuthService auth = new AuthService();
    public final UserService users = new UserService();
    public final DepartmentService departments = new DepartmentService();
    public final EmployeeService employees = new EmployeeService();
    public final SalaryService salaries = new SalaryService();
    public final AttendanceService attendance = new AttendanceService();
    public final LeaveService leave = new LeaveService(attendance);
    public final PayrollService payroll = new PayrollService(attendance);
    public final PayslipService payslips = new PayslipService(payroll);
    public final ReportService reports = new ReportService();
}
