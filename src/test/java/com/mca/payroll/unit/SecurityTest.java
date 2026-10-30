package com.mca.payroll.unit;

import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.UnauthorizedOperationException;
import com.mca.payroll.service.AttendanceService;
import com.mca.payroll.service.DepartmentService;
import com.mca.payroll.service.EmployeeService;
import com.mca.payroll.service.LeaveService;
import com.mca.payroll.service.PayrollService;
import com.mca.payroll.service.ReportService;
import com.mca.payroll.service.UserService;
import com.mca.payroll.dto.DepartmentRequest;
import com.mca.payroll.util.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unauthorized operations are refused by the service layer BEFORE any database access, so these
 * tests need no database.
 */
class SecurityTest {

    private final AttendanceService attendance = new AttendanceService();
    private final PayrollService payroll = new PayrollService(attendance);
    private final LeaveService leave = new LeaveService(attendance);

    @AfterEach
    void clear() {
        SecurityContext.clear();
    }

    private void signIn(Role role, Long employeeId) {
        SecurityContext.setCurrentUser(new AuthenticatedUser(99L, role.name().toLowerCase(), role, employeeId));
    }

    @Test
    @DisplayName("Nobody signed in: every service refuses")
    void notSignedIn() {
        assertThrows(UnauthorizedOperationException.class, () -> new DepartmentService().getAllDepartments());
        assertThrows(UnauthorizedOperationException.class, () -> new EmployeeService().getAllEmployees());
        assertThrows(UnauthorizedOperationException.class, () -> payroll.getPayrollByEmployee(1L));
    }

    @Test
    @DisplayName("EMPLOYEE cannot manage departments, employees, reports or users")
    void employeeCannotManage() {
        signIn(Role.EMPLOYEE, 5L);
        assertThrows(UnauthorizedOperationException.class,
                () -> new DepartmentService().addDepartment(new DepartmentRequest("X", "d", "l")));
        assertThrows(UnauthorizedOperationException.class, () -> new EmployeeService().getAllEmployees());
        assertThrows(UnauthorizedOperationException.class, () -> new ReportService().employeeCounts());
        assertThrows(UnauthorizedOperationException.class, () -> new UserService().listUsers());
    }

    @Test
    @DisplayName("EMPLOYEE cannot view another employee's payroll or leave history")
    void employeeCannotSeeOthers() {
        signIn(Role.EMPLOYEE, 5L);
        assertThrows(UnauthorizedOperationException.class, () -> payroll.getPayrollByEmployee(6L));
        assertThrows(UnauthorizedOperationException.class, () -> leave.getLeaveHistory(6L));
        assertThrows(UnauthorizedOperationException.class, () -> leave.getLeaveBalances(6L, 2025));
        assertThrows(UnauthorizedOperationException.class, () -> attendance.getAttendanceByEmployee(6L));
    }

    @Test
    @DisplayName("EMPLOYEE cannot generate, finalize or pay payroll, or approve/reject leave")
    void employeeCannotRunSensitiveOperations() {
        signIn(Role.EMPLOYEE, 5L);
        assertThrows(UnauthorizedOperationException.class, () -> payroll.generatePayroll(5L, YearMonth.of(2025, 4), null));
        assertThrows(UnauthorizedOperationException.class, () -> payroll.finalizePayroll(1L));
        assertThrows(UnauthorizedOperationException.class, () -> payroll.markAsPaid(1L));
        assertThrows(UnauthorizedOperationException.class, () -> leave.approveLeave(1L, null));
        assertThrows(UnauthorizedOperationException.class, () -> leave.rejectLeave(1L, "no"));
    }

    @Test
    @DisplayName("HR may generate payroll and review leave but cannot finalize or pay payroll")
    void hrCannotFinalize() {
        signIn(Role.HR, null);
        assertThrows(UnauthorizedOperationException.class, () -> payroll.finalizePayroll(1L));
        assertThrows(UnauthorizedOperationException.class, () -> payroll.finalizeMonth(YearMonth.of(2025, 4)));
        assertThrows(UnauthorizedOperationException.class, () -> payroll.markAsPaid(1L));
        assertThrows(UnauthorizedOperationException.class, () -> new UserService().listUsers());
        assertThrows(UnauthorizedOperationException.class, () -> new EmployeeService().deleteEmployee(1L));
    }

    @Test
    @DisplayName("Role checks use the authenticated user, and self-access is allowed")
    void selfAccessAllowed() {
        signIn(Role.EMPLOYEE, 5L);
        assertEquals(5L, SecurityContext.requireSelfOrStaff(5L).employeeId());
        signIn(Role.HR, null);
        assertDoesNotThrow(() -> SecurityContext.requireSelfOrStaff(123L));
        assertDoesNotThrow(() -> SecurityContext.requireRole(Role.ADMIN, Role.HR));
    }
}
