# Development phases (what was built, in order)

| Phase | Content | Files | How to verify |
|------|---------|-------|---------------|
| 1 | Maven project, Hibernate configuration, `HibernateUtil`, configuration loader | `pom.xml`, `hibernate.cfg.xml`, `application.properties`, `log4j2.xml`, `util/HibernateUtil`, `util/AppConfig` | `mvn compile`; run `Main` → "Connecting to the database…" and `SHOW TABLES;` lists 7 tables |
| 2 | MySQL connection and entities | `entity/*`, `sql/*.sql` | `DESCRIBE employees;` shows constraints; `SHOW CREATE TABLE attendance;` shows the unique key |
| 3 | Department and employee CRUD | `dao/DepartmentDao`, `EmployeeDao`, `service/DepartmentService`, `EmployeeService`, `console/DepartmentMenu`, `EmployeeMenu` | add rows in the menu, then `SELECT * FROM departments/employees;` |
| 4 | Salary structures with history | `dao/SalaryStructureDao`, `service/SalaryService`, `console/SalaryMenu` | revise a salary twice → 2 rows, older `effective_to` set |
| 5 | Attendance and leave | `AttendanceDao`, `LeaveRequestDao`, `AttendanceService`, `LeaveService`, calculators, menus | duplicate attendance rejected; approve leave → `attendance` rows `PAID_LEAVE` |
| 6 | Payroll calculation and transactions | `PayrollPolicy`, `PayrollCalculator`, `PayrollDao`, `PayrollService`, `PayrollMenu` | generate, regenerate draft, finalize; `SELECT * FROM payroll;` |
| 7 | Payslips and reports | `PayslipService`, `ReportService`, `PayslipMenu`, `ReportMenu` | display payslip; `payslips/*.txt`; compare report totals with `SUM()` in SQL |
| 8 | Authentication and roles | `AuthService`, `UserService`, `SecurityContext`, `PasswordHasher`, `MainMenu`, `SelfServiceMenu` | `SELECT username, role, LEFT(password_hash,7) FROM users;` |
| 9 | Validation, exceptions, tests | `util/InputValidator`, `exception/*`, `src/test/*` | `mvn test`, `mvn test -Pintegration` |
| 10 | Verification and documentation | `README.md`, `docs/*` | walk through `docs/SAMPLE_IO.md` |

Each phase compiles and runs on its own: later phases only add classes and menu entries.
