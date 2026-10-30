# Troubleshooting common Hibernate and MySQL errors

The application shows a short, safe message on screen; the full stack trace is in **`logs/payroll.log`**.
Look there first.

| Symptom / message | Cause | Fix |
|---|---|---|
| `Could not connect to the database…` at start | MySQL not running, wrong URL/user/password | Start MySQL (`sudo systemctl start mysql` / Windows service *MySQL80*). Check the three settings (README §1). Test: `mysql -u root -p` |
| log: `Communications link failure` / `Connection refused` | Server down or wrong port/host | Verify port 3306 (`SHOW VARIABLES LIKE 'port';`), firewall, URL `jdbc:mysql://localhost:3306/…` |
| log: `Access denied for user 'root'@'localhost'` | Wrong password or user lacks rights | Set `PAYROLL_DB_PASSWORD`; or `GRANT ALL ON employee_payroll_db.* TO 'user'@'localhost';` |
| log: `Unknown database 'employee_payroll_db'` | Database missing and URL lacks `createDatabaseIfNotExist=true` | Run `sql/01_create_database.sql` or restore the URL parameter |
| log: `Public Key Retrieval is not allowed` | MySQL 8 `caching_sha2_password` without SSL | Keep `allowPublicKeyRetrieval=true&useSSL=false` in the URL (local development only) |
| log: `The server time zone value … is unrecognized` | OS time zone name unknown to the driver | Keep `serverTimezone=UTC` in the URL |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | Connector/J not on the classpath | Run through Maven (`mvn compile exec:java`) or refresh Maven dependencies in the IDE; for the jar keep `target/lib` next to it |
| `HHH000342: Could not obtain connection to query metadata` | Same as connection errors above | Fix the connection first |
| `Unable to determine Dialect` | No connection when Hibernate started | Fix connection; Hibernate 6 detects the MySQL dialect automatically |
| `org.hibernate.AnnotationException: … Unknown mapping` / `Unknown entity` | Entity missing in `hibernate.cfg.xml` | Add `<mapping class="…"/>` |
| `MappingException`/`DuplicateMappingException` | Two entities with the same table or name | Keep one entity per table |
| `Table 'employee_payroll_db.xxx' doesn't exist` | Schema not created | Use `hibernate.hbm2ddl.auto=update`, or run `sql/02_schema.sql` |
| `Schema-validation: wrong column type …` (with `validate`) | Hand-edited schema differs from entities | Re-run `02_schema.sql` on an empty DB, or switch to `update` |
| `Duplicate entry 'EMP001' for key 'uk_employee_code'` | Unique constraint hit (shown to the user as "duplicate or still-referenced record") | Use another code/e-mail. Services check first, so this appears only on races |
| `Cannot delete or update a parent row: a foreign key constraint fails` | Deleting a row that is referenced | Intended protection – deactivate employees; empty a department first |
| "The record was changed by someone else…" (`OptimisticLockException`) | `@Version` mismatch – two sessions edited the same row | Reload the record and repeat |
| `LazyInitializationException` | Lazy association read after the session closed | Convert to a DTO inside the service (`DtoMapper`) or use a fetch join. The console layer must never touch entities |
| `NonUniqueResultException` | Query expected one row | Check data and unique constraints |
| `PersistentObjectException: detached entity passed to persist` | `persist` called on an existing row | Load the row and modify it, or use `merge` |
| `TransientPropertyValueException: object references an unsaved transient instance` | FK points to an entity that was never saved | Save the parent first or use `session.getReference` |
| `QuerySyntaxException … unexpected token` | HQL typo – HQL uses **entity and property names**, not table/column names | Compare with the entity class. A parameter name must not be an HQL keyword (`:from`, `:date`, `:year`…) |
| `Incorrect string value` / garbled text | Wrong character set | `utf8mb4` database and `characterEncoding=UTF-8` in the URL |
| `java.lang.UnsupportedClassVersionError` / `release 17 not supported` | JDK older than 17 | Install JDK 17+; in the IDE select it as project SDK; `mvn -version` must show 17+ |
| `Password … (visible in this console)` | IDE console has no `System.console()` | Expected; use a real terminal for private passwords |
| Log4j: `ERROR StatusLogger … No Log4j 2 configuration file found` | `log4j2.xml` not on the classpath | Make sure `src/main/resources` is a source folder (Maven → Update Project) |
| `Tests are skipped` | Integration tests are excluded by default | `mvn test -Pintegration` |
| Integration tests fail to connect | Test DB settings missing | Set `PAYROLL_TEST_DB_URL/USER/PASSWORD` |
| Forgot the admin password | – | With MySQL access: `DELETE FROM users;` and restart – the app offers first-run setup again (only in development!) |

## Useful MySQL checks
```sql
SHOW DATABASES;  USE employee_payroll_db;  SHOW TABLES;
SHOW CREATE TABLE payroll\G
SELECT * FROM information_schema.table_constraints WHERE table_schema = 'employee_payroll_db';
SHOW PROCESSLIST;     -- hanging transactions
```

## See the SQL Hibernate generates
Set `<property name="hibernate.show_sql">true</property>` in `hibernate.cfg.xml` (SQL prints to the console), or set the
`org.hibernate.SQL` logger to `debug` in `log4j2.xml` (SQL goes to `logs/payroll.log`).
