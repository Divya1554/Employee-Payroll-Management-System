-- =====================================================================
-- Employee Payroll Management System - step 3 (OPTIONAL): sample data
--
-- Run AFTER the tables exist (start the application once, or run 02_schema.sql):
--   mysql -u root -p employee_payroll_db < sql/03_seed_data.sql
--
-- No user accounts are inserted on purpose: the first start of the application asks you
-- to create the administrator, so no default password ever exists in this project.
-- Sample attendance covers September 2026 so you can generate payroll for 2026-09.
-- =====================================================================
USE employee_payroll_db;

INSERT INTO departments (department_name, description, location, version) VALUES
    ('Information Technology', 'Software development and IT support', 'Pune',      0),
    ('Human Resources',        'Recruitment, payroll and employee relations', 'Pune', 0),
    ('Finance',                'Accounts, audit and budgeting',          'Mumbai',    0),
    ('Marketing',              'Brand, campaigns and sales support',     'Bengaluru', 0);

INSERT INTO employees (employee_code, first_name, last_name, email, phone, date_of_birth, date_of_joining,
                       designation, employment_status, department_id, created_at, updated_at, version)
SELECT v.code, v.fn, v.ln, v.email, v.phone, v.dob, v.doj, v.des, 'ACTIVE', d.department_id, NOW(6), NOW(6), 0
FROM (
    SELECT 'EMP001' code, 'Rahul'  fn, 'Sharma' ln, 'rahul.sharma@example.com'  email, '9876543210' phone,
           DATE '1992-03-14' dob, DATE '2021-06-01' doj, 'Senior Developer'   des, 'Information Technology' dept UNION ALL
    SELECT 'EMP002', 'Priya',  'Patel',  'priya.patel@example.com',  '9876543211', DATE '1994-08-22', DATE '2022-01-10', 'Software Engineer', 'Information Technology' UNION ALL
    SELECT 'EMP003', 'Anita',  'Desai',  'anita.desai@example.com',  '9876543212', DATE '1989-11-05', DATE '2019-04-15', 'HR Manager',        'Human Resources'        UNION ALL
    SELECT 'EMP004', 'Vikram', 'Singh',  'vikram.singh@example.com', '9876543213', DATE '1991-07-30', DATE '2020-09-01', 'Accountant',        'Finance'                UNION ALL
    SELECT 'EMP005', 'Sneha',  'Iyer',   'sneha.iyer@example.com',   '9876543214', DATE '1996-02-18', DATE '2023-03-20', 'Marketing Executive','Marketing'           UNION ALL
    SELECT 'EMP006', 'Arjun',  'Mehta',  'arjun.mehta@example.com',  '9876543215', DATE '1993-12-09', DATE '2022-08-08', 'Software Engineer', 'Information Technology'
) v JOIN departments d ON d.department_name = v.dept;

-- Salary structures (effective from 2025-01-01, still current)
INSERT INTO salary_structures (employee_id, basic_salary, house_rent_allowance, medical_allowance,
                               conveyance_allowance, other_allowances, provident_fund_deduction,
                               professional_tax, effective_from, effective_to, created_at, created_by, version)
SELECT e.employee_id, s.basic, s.hra, s.med, s.conv, s.oth, s.pf, 200.00, DATE '2025-01-01', NULL, NOW(6), 'seed', 0
FROM (
    SELECT 'EMP001' code, 60000.00 basic, 24000.00 hra, 1250.00 med, 1600.00 conv, 2000.00 oth, 7200.00 pf UNION ALL
    SELECT 'EMP002', 45000.00, 18000.00, 1250.00, 1600.00, 1500.00, 5400.00 UNION ALL
    SELECT 'EMP003', 55000.00, 22000.00, 1250.00, 1600.00, 2000.00, 6600.00 UNION ALL
    SELECT 'EMP004', 35000.00, 14000.00, 1250.00, 1600.00, 1000.00, 4200.00 UNION ALL
    SELECT 'EMP005', 30000.00, 12000.00, 1250.00, 1600.00,  500.00, 3600.00 UNION ALL
    SELECT 'EMP006', 42000.00, 16800.00, 1250.00, 1600.00, 1200.00, 5040.00
) s JOIN employees e ON e.employee_code = s.code;

-- Sample attendance, September 2026 (days not listed follow payroll.missing.attendance = PRESENT)
INSERT INTO attendance (employee_id, attendance_date, attendance_status, remarks)
SELECT e.employee_id, a.d, a.st, a.rem
FROM (
    SELECT 'EMP002' code, DATE '2026-09-03' d, 'ABSENT'      st, 'Not informed'   rem UNION ALL
    SELECT 'EMP002', DATE '2026-09-04', 'ABSENT',       'Not informed'   UNION ALL
    SELECT 'EMP004', DATE '2026-09-10', 'UNPAID_LEAVE', 'Personal work'  UNION ALL
    SELECT 'EMP004', DATE '2026-09-11', 'UNPAID_LEAVE', 'Personal work'  UNION ALL
    SELECT 'EMP005', DATE '2026-09-07', 'PAID_LEAVE',   'Approved leave' UNION ALL
    SELECT 'EMP006', DATE '2026-09-15', 'HALF_DAY',     'Doctor visit'
) a JOIN employees e ON e.employee_code = a.code;
