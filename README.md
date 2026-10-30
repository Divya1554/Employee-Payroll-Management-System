# Employee Payroll Management System

**Core Java · Hibernate ORM 6 · MySQL 8 · Maven** — MCA major project

A standalone, console-based payroll application. It demonstrates Hibernate's native
`SessionFactory` / `Session` / `Transaction` APIs, JPA entity mapping, HQL, named queries,
aggregate queries, Criteria API, pagination, optimistic locking and transaction management,
together with real payroll business rules.

No Spring, no REST/web framework, no front end. Libraries: Hibernate ORM 6.4, MySQL Connector/J,
BCrypt (password hashing), Log4j 2 (logging), JUnit 5 (tests).

---

## 1. Quick start

### Prerequisites
| Tool | Version |
|------|---------|
| JDK | 17 or newer |
| Maven | 3.8+ |
| MySQL Server | 8.x (running on localhost:3306) |

### Steps
1. **Create the database** (optional – the app can create it itself):
   ```bash
   mysql -u root -p < sql/01_create_database.sql
   ```
2. **Configure the database password** – never put it in source files. Choose one:
   * environment variables (highest priority)
     ```bash
     # Linux / macOS
     export PAYROLL_DB_USER=root
     export PAYROLL_DB_PASSWORD='your-password'
     # Windows PowerShell
     $env:PAYROLL_DB_USER="root"; $env:PAYROLL_DB_PASSWORD="your-password"
     ```
   * or copy `config/application-local.properties.example` to
     `config/application-local.properties` and edit it (this file is git-ignored).
3. **Run**
   ```bash
   mvn compile exec:java
   ```
   Hibernate creates the tables on first start (`hibernate.hbm2ddl.auto=update`).
4. **First start:** there are no users yet, so the program asks you to create the
   **administrator** account (password: min 8 characters, letters and digits). Then sign in.
5. *(Optional)* load sample departments, employees, salaries and September-2026 attendance:
   ```bash
   mysql -u root -p employee_payroll_db < sql/03_seed_data.sql
   ```
6. Try it: **Payroll Processing → Generate payroll for all active employees → 2026-09**, then
   **Payslip Management → Display payslip**.

Runnable jar: `mvn package` then `java -jar target/employee-payroll-system-1.0.0.jar`
(dependencies are copied to `target/lib`).

Eclipse / IntelliJ instructions: [docs/IDE_SETUP.md](docs/IDE_SETUP.md).
Problems? [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

---

## 2. Features (9 modules)

1. **Departments** – add, list, search by ID/name, update, delete (only when empty); unique names.
2. **Employees** – add, list (paged), search by ID / name / department / designation / joining date,
   multi-filter search (Criteria API), update, deactivate / reactivate, guarded delete.
3. **Salary structures** – create/revise (history kept: the old row is closed, never overwritten),
   correct, assign a copy to another employee, view per employee. All money is `BigDecimal`.
4. **Attendance** – record, update, month view, monthly summary, absent days, approved-leave days,
   bulk entry; unique per employee+date (service check **and** DB constraint).
5. **Leave** – submit, approve, reject, history, balances. Overlaps, date ranges and annual quotas are
   validated. Approval writes `PAID_LEAVE` / `UNPAID_LEAVE` attendance for each working day.
6. **Payroll** – generate (one / all), regenerate drafts, finalize, mark paid, history, paging.
7. **Payslips** – readable text payslip on screen or saved to `payslips/*.txt`; company header from properties.
8. **Reports** – HQL `COUNT`, `SUM`, `GROUP BY`, `HAVING`, left joins.
9. **Authentication & roles** – BCrypt hashes, `ADMIN` / `HR` / `EMPLOYEE`, checks inside the service layer.

### Role matrix
| Operation | ADMIN | HR | EMPLOYEE |
|-----------|:----:|:--:|:--------:|
| Departments, employees, salary structures, attendance, reports | ✔ | ✔ | – |
| Generate / regenerate payroll | ✔ | ✔ | – |
| **Finalize payroll, mark paid** | ✔ | – | – |
| **Approve / reject leave** | ✔ | ✔ (not own) | – |
| Delete employee (no history only), user management | ✔ | – | – |
| Submit own leave; view own profile, attendance, leave, salary, **finalized** payroll & payslips | ✔ | ✔ | ✔ (own only) |

The role always comes from the authenticated user's database row — it is never typed in.

---

## 3. Payroll rules (all configurable in `application.properties`)

```
Gross            = Basic + HRA + Medical + Conveyance + Other allowances
Unpaid deduction = base × unpaid days ÷ payable-day basis        (base = GROSS or BASIC)
Other deductions = Unpaid deduction + manual other deductions
Total deductions = Provident fund + Professional tax + Other deductions
Net salary       = Gross − Total deductions
```
* **Payroll period** = one calendar month.
* **Payable-day basis** (`payroll.payable.days.mode`): `CALENDAR_DAYS` (28–31), `WORKING_DAYS`, or `FIXED` (e.g. 30).
* Example from the specification (`FIXED`, 30 days): ₹30,000 ÷ 30 × 2 unpaid days = **₹2,000** → ₹28,000.
  This is a *configurable policy*, not a statutory formula. PF and professional tax are **stored amounts**
  (or an optional percentage of basic) — no statutory rates are hard-coded.
* **No double deduction:** every working day is classified exactly once from the attendance table
  (`present + paid leave + unpaid = working days`). Approved leave is converted to attendance when approved.
* Half day: `payroll.halfday.unpaid.fraction` (default 0.5) of a day is unpaid.
  Days before the joining date count as unpaid. Weekly offs (`payroll.weekly.off`) are ignored.
  Days without a record follow `payroll.missing.attendance` (`PRESENT` or `ABSENT`).
* **Rounding:** `BigDecimal`, scale 2, `HALF_UP` (configurable).
* **Negative net salary** is rejected unless `payroll.allow.negative.net=true`.
* **Duplicates** are blocked per employee+month+year (code check + unique constraint).
* **Snapshot & immutability:** salary figures are copied into the payroll row. `FINALIZED`/`PAID` rows are never
  modified by regeneration or by later salary revisions; the attendance of a finalized month is locked.
* **Transactions:** every service operation runs in one transaction — commit on success, rollback on any failure,
  session always closed (`HibernateUtil.inTransaction`).
* The salary structure used is the one **in force on the last day of the month**.

---

## 4. Project structure
```
employee-payroll-system/
├── pom.xml
├── README.md
├── config/application-local.properties.example
├── sql/   01_create_database.sql · 02_schema.sql · 03_seed_data.sql
├── docs/  ARCHITECTURE · ER_DIAGRAM · CLASS_DIAGRAM · USE_CASES · PROJECT_REPORT
│          SAMPLE_IO · IDE_SETUP · TROUBLESHOOTING · DEVELOPMENT_PHASES
└── src/
    ├── main/java/com/mca/payroll/
    │   ├── Main.java
    │   ├── entity/     Department, Employee, SalaryStructure, SalaryComponents(@Embeddable),
    │   │               Attendance, LeaveRequest, Payroll, User + enums
    │   ├── dao/        BaseDao + one DAO per entity (HQL, named queries, Criteria API)
    │   ├── service/    business rules, PayrollCalculator, AttendanceCalculator, PayrollPolicy, auth
    │   ├── dto/        records returned to the console layer + DtoMapper
    │   ├── exception/  custom exceptions
    │   ├── util/       HibernateUtil, AppConfig, validators, money/date helpers, security context
    │   └── console/    menus (one per module), ConsoleIO, TableFormatter
    ├── main/resources/ hibernate.cfg.xml · application.properties · log4j2.xml
    └── test/java/com/mca/payroll/
        ├── unit/         JUnit 5, no database
        └── integration/  JUnit 5, needs MySQL (tag "integration")
```

## 5. Hibernate concepts demonstrated (where to look)
| Concept | Location |
|---------|----------|
| `@Entity @Table`, `@Id @GeneratedValue(IDENTITY)`, `@Column` constraints | `entity/*` |
| `@ManyToOne`, `@OneToMany(mappedBy)`, optional `@OneToOne` | `Employee`, `Department`, `User` |
| `@Enumerated(STRING)` (stored as VARCHAR) | all status fields |
| `@Embeddable` / `@Embedded` | `SalaryComponents` in `SalaryStructure` |
| `@Version` optimistic locking | all mutable entities |
| Unique constraints, indexes, foreign keys | `@Table(...)`, `sql/02_schema.sql` |
| Named queries | `Department`, `Employee`, `SalaryStructure`, `Payroll`, `User` |
| Parameterised HQL, fetch joins, sorting | `dao/*` |
| Pagination (`setFirstResult/setMaxResults`) | `EmployeeDao.findPage`, `PayrollDao.findPage` |
| Criteria API (dynamic filter) | `EmployeeDao.search` |
| Aggregates (`COUNT/SUM/GROUP BY/HAVING`) | `ReportService` |
| SessionFactory, Session, Transaction, commit/rollback | `HibernateUtil` |
| Lazy loading everywhere, no cascade | all relationships |

## 6. Tests
```bash
mvn test                      # unit tests only (no database)
mvn test -Pintegration        # + MySQL integration tests (separate database employee_payroll_test)
```
Integration tests use `PAYROLL_TEST_DB_URL`, `PAYROLL_TEST_DB_USER`, `PAYROLL_TEST_DB_PASSWORD`
(default `jdbc:mysql://localhost:3306/employee_payroll_test`, user `root`) and **drop that test database's tables**
when finished — never point them at real data.

| Area | Test class |
|------|-----------|
| Salary calculation, rounding, negative net | `PayrollCalculatorTest` |
| Attendance summaries | `AttendanceCalculatorTest` |
| Leave validation | `LeaveValidationTest` |
| Validation rules / salary rules | `InputValidatorTest`, `SalaryValidationTest` |
| Unauthorized operations | `SecurityTest` |
| Password hashing | `PasswordHasherTest` |
| Employee creation, duplicate code, department assignment | `EmployeeIntegrationTest` |
| Duplicate attendance (service + DB constraint), leave flow | `AttendanceLeaveIntegrationTest` |
| Payroll generation, duplicates, **rollback**, **history preservation** | `PayrollIntegrationTest` |
| Login, hashed storage, linked employee accounts | `AuthIntegrationTest` |

## 7. Security notes
* Passwords: BCrypt (cost 12 by default). Only hashes are stored; `toString()` never prints them.
* Login errors are generic ("Invalid username or password"); unknown users cost the same time as known ones.
* DB credentials come from environment variables / an ignored local file; error messages never show them.
* All queries are parameterised; user text used in `LIKE` is escaped.
* In an IDE console `System.console()` is null, so passwords are typed visibly — use a real terminal for demos with real passwords.

## 8. Known limitations
* Single-user console application (one signed-in user at a time).
* A leave request must lie within one calendar year; no public-holiday calendar (use weekly offs and attendance).
* PF / professional tax are stored amounts, not statutory slabs — by design.
* The built-in Hibernate connection pool is for learning/demo; use HikariCP for production.

## 9. Verification status (please read)
The project was written and checked in an offline build sandbox that could **not download Maven dependencies**
(Maven Central was blocked), so it has **not been run against the real Hibernate/MySQL jars**. What was checked:

| Check | Result |
|-------|--------|
| All 100+ main and test sources compile (against minimal API stand-ins for Jakarta Persistence / Hibernate / Log4j / BCrypt) | ✔ no errors |
| 53 unit tests executed (payroll formulas incl. the ₹30,000 example, attendance, leave rules, validation, authorization, entities) | ✔ all passed |
| Real `mvn test` with Hibernate 6.4.4 / JUnit 5 jars | ⏳ run it on your machine first |
| MySQL integration tests, `sql/*.sql` scripts, interactive console session | ⏳ not executed here |

**Your first three commands:** `mvn clean test` → `mvn test -Pintegration` (with MySQL running) → `mvn compile exec:java`.
If anything differs from this README, `docs/TROUBLESHOOTING.md` lists the usual causes.

More: [docs/PROJECT_REPORT.md](docs/PROJECT_REPORT.md) (MCA report text), [docs/SAMPLE_IO.md](docs/SAMPLE_IO.md).
