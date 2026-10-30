-- =====================================================================
-- Employee Payroll Management System - step 1: create the database
-- Run as a MySQL user that may create databases:
--   mysql -u root -p < sql/01_create_database.sql
-- (The application can also create the database itself because the JDBC URL
--  contains createDatabaseIfNotExist=true.)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS employee_payroll_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- Optional: a dedicated least-privilege account instead of root.
-- Replace the password and keep it OUT of source control.
-- CREATE USER IF NOT EXISTS 'payroll_app'@'localhost' IDENTIFIED BY 'ChangeMe_Str0ng!';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
--     ON employee_payroll_db.* TO 'payroll_app'@'localhost';
-- FLUSH PRIVILEGES;
