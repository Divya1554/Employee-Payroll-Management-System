# Employee Payroll Management System Using Hibernate ORM
### Project report content – Master of Computer Applications (MCA)

*(Replace the bracketed items with your institution, guide and personal details.)*

**Submitted by:** [Student name], [Enrolment no.]  **Guide:** [Name, designation]  **University / Institute:** [Name]  **Year:** [Year]

---

## Abstract
Payroll processing in many small and medium organisations is still done with spreadsheets, which leads to calculation
errors, duplicate payments, lost history and weak access control. This project develops a standalone, console-based
**Employee Payroll Management System** in Core Java using **Hibernate ORM 6** and **MySQL 8**. It manages departments,
employees, salary structures, attendance, leave and monthly payroll, produces payslips and management reports, and
protects data with BCrypt-hashed passwords and role-based authorisation. Payroll is calculated with `BigDecimal` under a
configurable policy, is stored as an immutable snapshot after finalisation, and is processed inside database transactions
with rollback on failure. The system demonstrates entity mapping, relationships, HQL, aggregate queries, Criteria API,
pagination, optimistic locking and transaction management in a realistic enterprise scenario.

**Keywords:** Hibernate, ORM, JPA, MySQL, payroll, transactions, BCrypt, layered architecture.

## 1. Introduction
### 1.1 Background
Payroll turns attendance and salary agreements into payable amounts. Errors affect employees directly and have legal and
financial consequences, so the process must be accurate, repeatable and auditable.
### 1.2 Problem statement
Manual / spreadsheet payroll suffers from (a) formula mistakes, (b) accidental duplicate or overwritten records, (c) loss of
salary history when a salary is revised, (d) no control over who may approve or view salaries, and (e) no reliable link
between leave, attendance and salary deductions.
### 1.3 Objectives
1. Store organisational and payroll data in a normalised relational database accessed only through Hibernate ORM.
2. Automate monthly payroll with attendance-based proration under a configurable policy.
3. Preserve history: salary revisions and finalised payroll must never be altered retroactively.
4. Guarantee consistency using transactions, unique constraints, foreign keys and optimistic locking.
5. Provide role-based access (ADMIN, HR, EMPLOYEE) with secure password storage.
6. Apply a layered architecture with automated tests.
### 1.4 Scope
Single-organisation, single-user console application; monthly payroll; leave and attendance as inputs to payroll.
Out of scope: statutory tax slabs, bank file generation, web/mobile interfaces, multi-user concurrency control beyond optimistic locking.

## 2. System analysis
### 2.1 Existing system and limitations
Spreadsheets / paper registers (see 1.2).
### 2.2 Proposed system
A transactional Java application with a relational back end, enforcing business rules in a service layer.
### 2.3 Feasibility
*Technical* – open-source stack (Java 17, Hibernate, MySQL, Maven). *Economic* – no licence cost. *Operational* – simple menu-driven interface.
### 2.4 Requirements
**Functional:** the nine modules (departments, employees, salary structures, attendance, leave, payroll, payslips, reports, users).
**Non-functional:** correctness of money arithmetic (BigDecimal), data integrity, security, maintainability (layers), testability, portability (standard JDBC/JPA).
### 2.5 Software and hardware requirements
Java 17, Maven 3.8+, MySQL 8, any IDE (Eclipse / IntelliJ IDEA); 4 GB RAM, 1 GB disk.

## 3. System design
### 3.1 Architecture
Console → Service → DAO → Hibernate/JDBC → MySQL, with cross-cutting utilities, DTOs and exceptions (see `ARCHITECTURE.md`).
### 3.2 Database design
Seven tables with primary and foreign keys, unique constraints, indexes and DECIMAL money columns (see `ER_DIAGRAM.md`).
Normalisation: every table is in 3NF; payroll deliberately stores a *snapshot* of salary values (controlled denormalisation) so history is immutable.
### 3.3 Use cases and class design
See `USE_CASES.md` and `CLASS_DIAGRAM.md`.
### 3.4 Payroll algorithm
1. Determine the working days of the month (calendar days minus configured weekly offs).
2. Classify every working day once from attendance: present, paid leave or unpaid (absent, unpaid leave, half-day fraction, days before joining, missing records per policy).
3. Gross = Basic + HRA + Medical + Conveyance + Other.
4. Unpaid deduction = base × unpaid days ÷ payable-day basis, rounded HALF_UP to two decimals.
5. Total deductions = PF + Professional tax + (Unpaid deduction + manual deductions).
6. Net = Gross − Total deductions; reject if negative (unless allowed by policy).
7. Persist a DRAFT row with all figures in one transaction; finalise later (immutable).
### 3.5 Security design
BCrypt password hashes, generic login errors, role checks at the start of each sensitive service method, employees limited to own data, finalisation restricted to ADMIN, leave approval restricted to ADMIN/HR (not own).

## 4. Implementation
### 4.1 Technology
Java 17 · Hibernate ORM 6.4 (Session / Transaction API, HQL, Criteria API) · MySQL 8 + Connector/J · Maven · Log4j 2 · BCrypt · JUnit 5.
### 4.2 Package structure
`entity`, `dao`, `service`, `dto`, `exception`, `util`, `console` (see README §4).
### 4.3 Hibernate features used
Annotations (`@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@Enumerated`, `@Embeddable`, `@Version`);
lazy loading and fetch joins; named and parameterised HQL; aggregates (COUNT, SUM, GROUP BY, HAVING); pagination and sorting; Criteria API; transactions with commit/rollback.
### 4.4 Key design decisions
* Transaction boundary in the service layer through `HibernateUtil.inTransaction`.
* DTOs returned to the console so entities never leave a session.
* No cascading, so history cannot be deleted by accident; deactivation instead of deletion.
* Policy values in a properties file instead of hard-coded statutory rules.
* Approved leave is converted to attendance, giving payroll a single source of truth and preventing double deductions.

## 5. Testing
### 5.1 Strategy
Unit tests (no database) for calculations and rules; integration tests against a separate MySQL database for persistence behaviour.
### 5.2 Test cases (excerpt)
| ID | Scenario | Expected result |
|----|----------|-----------------|
| T1 | ₹30,000 salary, 30 payable days, 2 unpaid days | Deduction 2,000.00, net 28,000.00 |
| T2 | Gross/deduction/net with typical salary | 45,350.00 / 3,950.00 / 41,400.00 (with ₹150 manual deduction) |
| T3 | Manual deduction larger than gross | `InvalidPayrollException`, nothing stored |
| T4 | Half day, paid leave, weekly off, before joining | Counted exactly once as per rules |
| T5 | Duplicate employee code / e-mail | `DuplicateEmployeeException` |
| T6 | Duplicate attendance (service and DB constraint) | Rejected both ways |
| T7 | Overlapping leave, quota exceeded, invalid dates | `InvalidLeaveRequestException` |
| T8 | Second payroll for same employee and month | `PayrollAlreadyExistsException`, one row |
| T9 | Finalised payroll after salary revision | Figures unchanged; regeneration refused |
| T10 | Exception after persist inside a transaction | Rolled back, 0 rows |
| T11 | HR finalises payroll; employee reads another's payroll | `UnauthorizedOperationException` |
| T12 | Login with wrong password / unknown user | Same generic message |
### 5.3 Results
Unit tests: 53 tests covering T1–T4, T11 and validation rules. Integration tests (T5–T10, T12) are run with `mvn test -Pintegration`
on MySQL. *(Add your own screenshots of the test run here.)*

## 6. Results and screens
Include screenshots of: login, main menu, employee list, salary structure, payroll summary, payslip, reports, and the MySQL tables
(see `SAMPLE_IO.md` for the expected output).

## 7. Limitations and future enhancements
Limitations: console UI, single user, no statutory tax slabs, one-year-bounded leave requests, built-in connection pool.
Future work: web/REST front end, statutory deduction rule tables, bank transfer file, email payslips, holiday calendar,
multi-year leave carry-forward, audit-trail table, HikariCP pooling, Flyway migrations.

## 8. Conclusion
The system shows how Hibernate ORM, a relational database and a layered architecture can deliver a reliable payroll
application: accurate BigDecimal arithmetic, protected history, transactional safety and role-based security, backed by automated tests.

## References
1. Hibernate ORM 6 User Guide – https://hibernate.org/orm/documentation/6.4/
2. Jakarta Persistence 3.1 Specification – https://jakarta.ee/specifications/persistence/3.1/
3. MySQL 8.0 Reference Manual – https://dev.mysql.com/doc/refman/8.0/en/
4. Bauer, C., King, G., Gregory, G. – *Java Persistence with Hibernate*, Manning.
5. Provos, N., Mazières, D. – *A Future-Adaptable Password Scheme* (bcrypt), USENIX 1999.
6. Apache Maven – https://maven.apache.org/guides/
