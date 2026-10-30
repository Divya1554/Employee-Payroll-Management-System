# Class diagram description

## Entities
```mermaid
classDiagram
    class Department { Long departmentId; String departmentName; String description; String location; Integer version }
    class Employee { Long employeeId; String employeeCode; String firstName; String lastName; String email; String phone;
                     LocalDate dateOfBirth; LocalDate dateOfJoining; String designation; EmploymentStatus employmentStatus;
                     LocalDateTime createdAt; LocalDateTime updatedAt }
    class SalaryComponents { <<Embeddable>> BigDecimal basicSalary; houseRentAllowance; medicalAllowance; conveyanceAllowance;
                             otherAllowances; providentFundDeduction; professionalTax; grossSalary() }
    class SalaryStructure { Long salaryStructureId; LocalDate effectiveFrom; LocalDate effectiveTo }
    class Attendance { Long attendanceId; LocalDate attendanceDate; AttendanceStatus attendanceStatus; String remarks }
    class LeaveRequest { Long leaveId; LeaveType leaveType; LocalDate startDate; LocalDate endDate; Integer numberOfDays;
                         LeaveStatus leaveStatus; LocalDateTime reviewedAt }
    class Payroll { Long payrollId; Integer payrollMonth; Integer payrollYear; BigDecimal grossSalary; totalDeductions; netSalary;
                    PayrollStatus payrollStatus; LocalDateTime generatedAt; LocalDateTime finalizedAt }
    class User { Long userId; String username; String passwordHash; Role role; boolean active }

    Department "1" --> "*" Employee
    Employee "1" --> "*" SalaryStructure
    Employee "1" --> "*" Attendance
    Employee "1" --> "*" LeaveRequest
    Employee "1" --> "*" Payroll
    User "0..1" --> "0..1" Employee
    SalaryStructure *-- SalaryComponents
```
Enums: `Role {ADMIN, HR, EMPLOYEE}`, `EmploymentStatus {ACTIVE, INACTIVE}`,
`AttendanceStatus {PRESENT, ABSENT, HALF_DAY, PAID_LEAVE, UNPAID_LEAVE}`, `LeaveStatus {PENDING, APPROVED, REJECTED}`,
`LeaveType {ANNUAL, SICK, CASUAL, UNPAID}`, `PayrollStatus {DRAFT, FINALIZED, PAID}`.

## Layers
```mermaid
classDiagram
    class BaseDao~T~ { findById(); save(); update(); delete(); findAll(); count() }
    BaseDao <|-- DepartmentDao
    BaseDao <|-- EmployeeDao
    BaseDao <|-- SalaryStructureDao
    BaseDao <|-- AttendanceDao
    BaseDao <|-- LeaveRequestDao
    BaseDao <|-- PayrollDao
    BaseDao <|-- UserDao

    class HibernateUtil { getSessionFactory(); inTransaction(fn); readOnly(fn); shutdown() }
    class PayrollService { generatePayroll(); generatePayrollForAll(); regenerateDraftPayroll(); finalizePayroll(); markAsPaid() }
    class PayrollCalculator { calculate(PayrollInput) PayrollResult }
    class AttendanceCalculator { summarize() AttendanceSummaryDto }
    class PayrollPolicy
    class LeaveService
    class AttendanceService
    class SalaryService
    class EmployeeService
    class DepartmentService
    class ReportService
    class AuthService
    class UserService
    class PayslipService
    class SecurityContext { requireRole(); requireSelfOrStaff() }

    PayrollService --> PayrollCalculator
    PayrollCalculator --> AttendanceCalculator
    PayrollCalculator --> PayrollPolicy
    PayrollService --> AttendanceService
    LeaveService --> AttendanceService
    PayslipService --> PayrollService
    PayrollService ..> HibernateUtil
    PayrollService ..> PayrollDao
    PayrollService ..> SecurityContext
```

## Console layer
`Main` → `MainMenu` (sign-in, top menu) → `DepartmentMenu`, `EmployeeMenu`, `SalaryMenu`, `AttendanceMenu`, `LeaveMenu`,
`PayrollMenu`, `PayslipMenu`, `ReportMenu`, `UserMenu`, `SelfServiceMenu` (EMPLOYEE role). Shared helpers:
`ConsoleIO` (validated input, error wrapper), `TableFormatter`, `Views`, `Services` (wiring).

## Exceptions
`PayrollException` (unchecked base) ← `ValidationException`, `BusinessRuleViolationException`, `DuplicateEmployeeException`,
`DuplicateRecordException`, `EmployeeNotFoundException`, `DepartmentNotFoundException`, `ResourceNotFoundException`,
`PayrollAlreadyExistsException`, `InvalidPayrollException`, `InvalidLeaveRequestException`,
`UnauthorizedOperationException`, `AuthenticationFailedException`, `DatabaseOperationException`.
