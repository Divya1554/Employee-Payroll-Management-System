# Use-case descriptions

**Actors:** *Administrator* (ADMIN), *HR Officer* (HR), *Employee* (EMPLOYEE). Every use case except UC-01 requires sign-in.

| ID | Use case | Actors | Summary |
|----|----------|--------|---------|
| UC-01 | Sign in / first-run setup | all | Verifies BCrypt hash; first run creates the initial ADMIN |
| UC-02 | Manage departments | ADMIN, HR | CRUD; names unique; delete only when empty |
| UC-03 | Manage employees | ADMIN, HR | CRUD-like, deactivate instead of delete, searches |
| UC-04 | Manage salary structure | ADMIN, HR | Create/revise (history), correct, assign copy |
| UC-05 | Record attendance | ADMIN, HR | One record per employee per day |
| UC-06 | Submit leave | all (own for EMPLOYEE) | Validated range, overlap and quota checks |
| UC-07 | Review leave | ADMIN, HR | Approve (writes attendance) or reject with reason |
| UC-08 | Generate payroll | ADMIN, HR | DRAFT for one employee or all active employees |
| UC-09 | Finalize / pay payroll | ADMIN | DRAFT → FINALIZED → PAID, immutable afterwards |
| UC-10 | View payslip | all (own finalized for EMPLOYEE) | On screen or `payslips/*.txt` |
| UC-11 | View reports | ADMIN, HR | Counts, payroll totals, expenses, unpaid leave … |
| UC-12 | Manage users | ADMIN | Create, (de)activate, reset password |
| UC-13 | Self-service | EMPLOYEE | Own profile, attendance, leave, salary, payroll |

## UC-08 Generate monthly payroll (core use case)
* **Actor:** ADMIN or HR. **Pre-conditions:** employee is ACTIVE, has joined by month end, has a salary structure in force.
* **Main flow:** 1. Actor chooses employee and month (`yyyy-MM`) and optional manual deduction.
  2. System checks no payroll exists for that employee+month. 3. System loads the structure in force on the last day of the month and the month's attendance.
  4. `PayrollCalculator` computes working days, present / paid / unpaid days, gross, unpaid deduction, total deductions, net.
  5. System stores a DRAFT row containing a snapshot of all amounts — in one transaction. 6. System shows the summary.
* **Alternative flows:** payroll exists → `PayrollAlreadyExistsException`; no structure / negative net / inactive →
  `InvalidPayrollException`, transaction rolled back, nothing stored; "all employees" skips and reports such employees.
* **Post-condition:** exactly one DRAFT payroll row for the employee and month.

## UC-07 Review leave
* **Pre-conditions:** request is PENDING; reviewer is ADMIN/HR and not the requester.
* **Main flow:** reviewer selects request → system re-checks balance → checks that no affected month is finalized →
  writes `PAID_LEAVE` / `UNPAID_LEAVE` attendance for every working day → marks APPROVED with time and reviewer.
* **Alternative:** reject (reason required); already-reviewed requests are refused.

## UC-09 Finalize payroll
* **Pre-conditions:** actor is ADMIN; payroll is DRAFT with non-negative net.
* **Main flow:** status → FINALIZED, `finalizedAt/By` stored. Later: `markAsPaid` sets PAID.
* **Post-condition:** regeneration and salary revisions can no longer change the row; attendance of that month is locked.
