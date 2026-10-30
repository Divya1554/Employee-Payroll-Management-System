# Database design – ER diagram description

Database: `employee_payroll_db` (MySQL 8, InnoDB, utf8mb4). Script: `sql/02_schema.sql`.

```mermaid
erDiagram
    DEPARTMENTS ||--o{ EMPLOYEES : "has (FK department_id)"
    EMPLOYEES ||--o{ SALARY_STRUCTURES : "salary history"
    EMPLOYEES ||--o{ ATTENDANCE : "daily records"
    EMPLOYEES ||--o{ LEAVE_REQUESTS : "requests"
    EMPLOYEES ||--o{ PAYROLL : "monthly payroll"
    EMPLOYEES |o--o| USERS : "optional login (unique FK)"

    DEPARTMENTS {
        bigint department_id PK
        varchar department_name UK
        varchar description
        varchar location
        int version
    }
    EMPLOYEES {
        bigint employee_id PK
        varchar employee_code UK
        varchar email UK
        varchar first_name
        varchar last_name
        date date_of_birth
        date date_of_joining
        varchar designation
        varchar employment_status
        bigint department_id FK
        datetime created_at
        datetime updated_at
    }
    SALARY_STRUCTURES {
        bigint salary_structure_id PK
        bigint employee_id FK
        decimal basic_salary
        decimal house_rent_allowance
        decimal medical_allowance
        decimal conveyance_allowance
        decimal other_allowances
        decimal provident_fund_deduction
        decimal professional_tax
        date effective_from
        date effective_to
    }
    ATTENDANCE {
        bigint attendance_id PK
        bigint employee_id FK
        date attendance_date
        varchar attendance_status
        varchar remarks
    }
    LEAVE_REQUESTS {
        bigint leave_id PK
        bigint employee_id FK
        varchar leave_type
        date start_date
        date end_date
        int number_of_days
        varchar leave_status
        datetime reviewed_at
    }
    PAYROLL {
        bigint payroll_id PK
        bigint employee_id FK
        int payroll_month
        int payroll_year
        decimal gross_salary
        decimal total_deductions
        decimal net_salary
        varchar payroll_status
    }
    USERS {
        bigint user_id PK
        varchar username UK
        varchar password_hash
        varchar role
        bit active
        bigint employee_id FK_UK
    }
```

## Relationships
| Relationship | Type | Owner (FK) | Cascade / fetch |
|--------------|------|-----------|-----------------|
| Department → Employee | One-to-Many | `employees.department_id` | none, LAZY (`mappedBy`) |
| Employee → Department | Many-to-One | Employee | none, LAZY, `optional=false` |
| Employee → Attendance | One-to-Many | `attendance.employee_id` | none, LAZY |
| Employee → LeaveRequest | One-to-Many | `leave_requests.employee_id` | none, LAZY |
| Employee → Payroll | One-to-Many | `payroll.employee_id` | none, LAZY |
| Employee → SalaryStructure | One-to-Many (history rows, validity period) | `salary_structures.employee_id` | none, LAZY |
| User → Employee | optional One-to-One | `users.employee_id` (unique) | none, LAZY |

No cascade is used anywhere: deleting a parent can never silently delete history.

## Constraints and indexes
| Table | Unique | Indexes | Checks |
|-------|--------|---------|--------|
| departments | `department_name` | – | – |
| employees | `employee_code`, `email` | department, designation, joining date, status | – |
| salary_structures | `(employee_id, effective_from)` | `(employee_id, effective_from, effective_to)` | all amounts ≥ 0 |
| attendance | **`(employee_id, attendance_date)`** | date | – |
| leave_requests | – | `(employee_id, start_date)`, status | `end_date >= start_date` |
| payroll | **`(employee_id, payroll_month, payroll_year)`** | `(year, month)`, status | month 1–12 |
| users | `username`, `employee_id` | – | – |

Money columns are `DECIMAL(12,2)`; statuses are `VARCHAR(20)` strings (Java enums, `@Enumerated(STRING)`);
timestamps are `DATETIME(6)`; every mutable table has an optimistic-locking `version` column.

## Salary history and payroll snapshot
* A revision inserts a **new** `salary_structures` row and sets `effective_to` of the previous one to the day before.
* `payroll` copies every salary amount (basic, HRA, …, PF, PT) plus attendance figures and the payable-day basis.
  Later salary edits therefore never change an existing payroll.
