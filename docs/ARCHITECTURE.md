# Architecture

## Layers
```
 Console layer (console/*, Main)      menus, input validation loops, tables           -> uses DTOs only
        │
 Service layer (service/*)            business rules, role checks, TRANSACTION boundary
        │                              PayrollCalculator / AttendanceCalculator (pure, unit-tested)
 DAO layer (dao/*)                    HQL, named queries, Criteria API; receives the open Session
        │
 Entity layer (entity/*)              JPA-annotated classes mapped to MySQL tables
        │
 Hibernate ORM 6  →  JDBC (Connector/J)  →  MySQL 8

 Cross-cutting: util/* (HibernateUtil, AppConfig, validators, SecurityContext, PasswordHasher),
                dto/* (read-only records), exception/* (custom unchecked exceptions)
```

**Rules that keep the layers separate**
* DAOs never open sessions or transactions; services do, through `HibernateUtil.inTransaction(...)` / `readOnly(...)`.
* The console layer never sees an entity. `DtoMapper` copies data to records while the session is still open,
  so there is no `LazyInitializationException` and no way to bypass validation by editing a managed entity.
* Every sensitive service method starts with a role check (`SecurityContext.requireRole / requireSelfOrStaff`).

## Request flow (example: *Finalize payroll*)
`PayrollMenu` → `PayrollService.finalizePayroll(id)` → `requireRole(ADMIN)` → `HibernateUtil.inTransaction`
(open Session, begin) → `PayrollDao.findByIdWithDetails` → status check → set `FINALIZED` → flush →
commit (or rollback + close on any exception) → `DtoMapper.toDto` → menu prints the result.

## Maven dependencies (pom.xml)
| Dependency | Version | Purpose |
|-----------|---------|---------|
| `org.hibernate.orm:hibernate-core` | 6.4.4.Final | ORM (brings Jakarta Persistence 3.1, JBoss Logging) |
| `com.mysql:mysql-connector-j` | 8.3.0 (runtime) | MySQL 8 JDBC driver |
| `at.favre.lib:bcrypt` | 0.10.2 | BCrypt password hashing |
| `org.apache.logging.log4j:log4j-api / log4j-core` | 2.22.1 | logging to `logs/payroll.log` |
| `org.junit.jupiter:junit-jupiter` | 5.10.2 (test) | unit & integration tests |

Plugins: maven-compiler (release 17), surefire 3.2.5 (integration tests excluded by tag), exec-maven-plugin
(`mvn compile exec:java`), maven-jar-plugin + maven-dependency-plugin (runnable jar with `target/lib`).

## Configuration
| Source | Content |
|--------|---------|
| `hibernate.cfg.xml` | driver, pool, `hbm2ddl.auto`, entity mappings (no URL/user/password) |
| env `PAYROLL_DB_URL / PAYROLL_DB_USER / PAYROLL_DB_PASSWORD` | connection (highest priority) |
| `config/application-local.properties` (git-ignored) or `PAYROLL_CONFIG` | local overrides incl. `db.password` |
| `application.properties` | company info, payroll & leave policy, security settings |

## Initial project structure (Phase 1)
```
pom.xml
src/main/java/com/mca/payroll/{entity,dao,service,util,exception,dto,console}
src/main/resources/{hibernate.cfg.xml, application.properties, log4j2.xml}
src/test/java/com/mca/payroll/{unit,integration}
sql/  config/  docs/
```
