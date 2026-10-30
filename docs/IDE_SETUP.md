# Running in Eclipse or IntelliJ IDEA

## Common preparation
1. Install **JDK 17+**, **MySQL 8** and make sure MySQL is running.
2. Unzip the project. The folder that contains `pom.xml` is the project root.
3. Set the database password as an environment variable **or** create `config/application-local.properties`
   (copy the `.example` file). See README §1.

## IntelliJ IDEA
1. **File → Open…** → select the project's `pom.xml` → *Open as Project*. Wait for Maven to import.
2. **File → Project Structure → Project SDK** = 17 (or newer), language level 17.
3. Open `src/main/java/com/mca/payroll/Main.java`, click the green arrow next to `main` → *Run 'Main.main()'*.
4. Environment variables: **Run → Edit Configurations → Main → Environment variables**:
   `PAYROLL_DB_USER=root;PAYROLL_DB_PASSWORD=your-password`.
5. Working directory must be the project root (default) so `config/` and `logs/` are found.
6. Tests: right-click `src/test/java` → *Run 'All Tests'* (unit tests). For integration tests run
   `mvn test -Pintegration` from the Maven tool window (**Profiles → integration**).
7. IntelliJ's Run console has no real terminal, so passwords are visible while typing. For a demo with real
   passwords use *Terminal* → `mvn compile exec:java`.

## Eclipse
1. **File → Import → Maven → Existing Maven Projects** → choose the project root → *Finish*.
2. Right-click project → **Properties → Java Compiler** → compliance 17. **Maven → Update Project** (Alt+F5).
3. Right-click `Main.java` → **Run As → Java Application**.
4. Environment variables: **Run → Run Configurations → Main → Environment** → *New* for `PAYROLL_DB_USER`,
   `PAYROLL_DB_PASSWORD`. Working directory: *Other* → `${workspace_loc:/employee-payroll-system}`.
5. Tests: right-click `src/test/java` → **Run As → JUnit Test**.
6. Eclipse's console is also not a real terminal – passwords are visible.

## Command line (no IDE)
```bash
mvn compile exec:java                     # run
mvn package && java -jar target/employee-payroll-system-1.0.0.jar
mvn test                                  # unit tests
mvn test -Pintegration                    # + MySQL tests
```

## Verifying the database
```sql
USE employee_payroll_db;
SHOW TABLES;                       -- 7 tables
SELECT employee_code, first_name, employment_status FROM employees;
SELECT employee_id, payroll_month, payroll_year, gross_salary, net_salary, payroll_status FROM payroll;
SELECT username, role, LEFT(password_hash, 7) AS hash_prefix FROM users;   -- hash starts with $2a$ / $2b$
SHOW CREATE TABLE attendance;      -- shows uk_attendance_employee_date
```
