-- =====================================================================
-- Employee Payroll Management System - step 2: schema (MySQL 8.x)
--
-- This script mirrors the Hibernate entity mappings exactly. You can either
--   (a) let Hibernate create the tables (hibernate.hbm2ddl.auto=update, the default), or
--   (b) run this script and set hibernate.hbm2ddl.auto=validate in hibernate.cfg.xml.
--
--   mysql -u root -p employee_payroll_db < sql/02_schema.sql
-- =====================================================================
USE employee_payroll_db;

-- ---------------------------------------------------------------- departments
CREATE TABLE IF NOT EXISTS departments (
    department_id    BIGINT       NOT NULL AUTO_INCREMENT,
    department_name  VARCHAR(100) NOT NULL,
    description      VARCHAR(255) NULL,
    location         VARCHAR(100) NULL,
    version          INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (department_id),
    CONSTRAINT uk_department_name UNIQUE (department_name)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------- employees
CREATE TABLE IF NOT EXISTS employees (
    employee_id        BIGINT       NOT NULL AUTO_INCREMENT,
    employee_code      VARCHAR(30)  NOT NULL,
    first_name         VARCHAR(50)  NOT NULL,
    last_name          VARCHAR(50)  NOT NULL,
    email              VARCHAR(150) NOT NULL,
    phone              VARCHAR(20)  NULL,
    date_of_birth      DATE         NOT NULL,
    date_of_joining    DATE         NOT NULL,
    designation        VARCHAR(100) NOT NULL,
    employment_status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    department_id      BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (employee_id),
    CONSTRAINT uk_employee_code  UNIQUE (employee_code),
    CONSTRAINT uk_employee_email UNIQUE (email),
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES departments (department_id),
    INDEX idx_employee_department  (department_id),
    INDEX idx_employee_designation (designation),
    INDEX idx_employee_joining     (date_of_joining),
    INDEX idx_employee_status      (employment_status)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------- salary_structures
CREATE TABLE IF NOT EXISTS salary_structures (
    salary_structure_id       BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id               BIGINT         NOT NULL,
    basic_salary              DECIMAL(12, 2) NOT NULL,
    house_rent_allowance      DECIMAL(12, 2) NOT NULL,
    medical_allowance         DECIMAL(12, 2) NOT NULL,
    conveyance_allowance      DECIMAL(12, 2) NOT NULL,
    other_allowances          DECIMAL(12, 2) NOT NULL,
    provident_fund_deduction  DECIMAL(12, 2) NOT NULL,
    professional_tax          DECIMAL(12, 2) NOT NULL,
    effective_from            DATE           NOT NULL,
    effective_to              DATE           NULL,
    created_at                DATETIME(6)    NOT NULL,
    created_by                VARCHAR(50)    NULL,
    version                   INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (salary_structure_id),
    CONSTRAINT uk_salary_employee_from UNIQUE (employee_id, effective_from),
    CONSTRAINT fk_salary_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_salary_non_negative CHECK (
        basic_salary >= 0 AND house_rent_allowance >= 0 AND medical_allowance >= 0 AND
        conveyance_allowance >= 0 AND other_allowances >= 0 AND
        provident_fund_deduction >= 0 AND professional_tax >= 0),
    INDEX idx_salary_employee_period (employee_id, effective_from, effective_to)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------- attendance
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id      BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id        BIGINT       NOT NULL,
    attendance_date    DATE         NOT NULL,
    attendance_status  VARCHAR(20)  NOT NULL,
    remarks            VARCHAR(255) NULL,
    PRIMARY KEY (attendance_id),
    CONSTRAINT uk_attendance_employee_date UNIQUE (employee_id, attendance_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    INDEX idx_attendance_date (attendance_date)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------- leave_requests
CREATE TABLE IF NOT EXISTS leave_requests (
    leave_id         BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id      BIGINT       NOT NULL,
    leave_type       VARCHAR(20)  NOT NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    number_of_days   INT          NOT NULL,
    reason           VARCHAR(255) NOT NULL,
    leave_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_at     DATETIME(6)  NOT NULL,
    reviewed_at      DATETIME(6)  NULL,
    reviewed_by      VARCHAR(50)  NULL,
    review_remarks   VARCHAR(255) NULL,
    version          INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (leave_id),
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_leave_dates CHECK (end_date >= start_date),
    INDEX idx_leave_employee_start (employee_id, start_date),
    INDEX idx_leave_status (leave_status)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------- payroll
CREATE TABLE IF NOT EXISTS payroll (
    payroll_id                BIGINT         NOT NULL AUTO_INCREMENT,
    employee_id               BIGINT         NOT NULL,
    payroll_month             INT            NOT NULL,
    payroll_year              INT            NOT NULL,
    working_days              INT            NOT NULL,
    present_days              DECIMAL(6, 2)  NOT NULL,
    paid_leave_days           DECIMAL(6, 2)  NOT NULL,
    unpaid_leave_days         DECIMAL(6, 2)  NOT NULL,
    payable_days_basis        INT            NOT NULL,
    basic_salary              DECIMAL(12, 2) NOT NULL,
    house_rent_allowance      DECIMAL(12, 2) NOT NULL,
    medical_allowance         DECIMAL(12, 2) NOT NULL,
    conveyance_allowance      DECIMAL(12, 2) NOT NULL,
    other_allowances          DECIMAL(12, 2) NOT NULL,
    gross_salary              DECIMAL(12, 2) NOT NULL,
    provident_fund_deduction  DECIMAL(12, 2) NOT NULL,
    professional_tax          DECIMAL(12, 2) NOT NULL,
    unpaid_deduction          DECIMAL(12, 2) NOT NULL,
    other_deductions          DECIMAL(12, 2) NOT NULL,
    total_deductions          DECIMAL(12, 2) NOT NULL,
    net_salary                DECIMAL(12, 2) NOT NULL,
    payroll_status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    generated_at              DATETIME(6)    NOT NULL,
    generated_by              VARCHAR(50)    NULL,
    finalized_at              DATETIME(6)    NULL,
    finalized_by              VARCHAR(50)    NULL,
    paid_at                   DATETIME(6)    NULL,
    version                   INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (payroll_id),
    CONSTRAINT uk_payroll_employee_period UNIQUE (employee_id, payroll_month, payroll_year),
    CONSTRAINT fk_payroll_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_payroll_month CHECK (payroll_month BETWEEN 1 AND 12),
    INDEX idx_payroll_period (payroll_year, payroll_month),
    INDEX idx_payroll_status (payroll_status)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------- users
CREATE TABLE IF NOT EXISTS users (
    user_id        BIGINT       NOT NULL AUTO_INCREMENT,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,        -- BCrypt hash, never the password
    role           VARCHAR(20)  NOT NULL,
    active         BIT(1)       NOT NULL,
    employee_id    BIGINT       NULL,            -- optional one-to-one link to an employee
    created_at     DATETIME(6)  NOT NULL,
    last_login_at  DATETIME(6)  NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_employee UNIQUE (employee_id),
    CONSTRAINT fk_user_employee FOREIGN KEY (employee_id) REFERENCES employees (employee_id)
) ENGINE = InnoDB;
