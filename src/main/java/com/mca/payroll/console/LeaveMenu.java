package com.mca.payroll.console;

import com.mca.payroll.dto.LeaveBalanceDto;
import com.mca.payroll.dto.LeaveRequestDto;
import com.mca.payroll.entity.LeaveType;

import java.time.LocalDate;
import java.util.List;

/** Module 5 - Leave management (staff view). */
class LeaveMenu {

    private final ConsoleIO io;
    private final Services s;

    LeaveMenu(ConsoleIO io, Services s) {
        this.io = io;
        this.s = s;
    }

    void show() {
        while (true) {
            int choice = io.menu("Leave Management", List.of("Submit leave request (for an employee)",
                    "View all leave requests", "View pending leave requests", "Approve leave", "Reject leave",
                    "View leave history of an employee", "Calculate leave balance"));
            if (choice == 0) {
                return;
            }
            io.run(() -> {
                switch (choice) {
                    case 1 -> submit(io.readId("Employee ID: "));
                    case 2 -> Views.leaves(io, s.leave.getAllLeaveRequests());
                    case 3 -> Views.leaves(io, s.leave.getPendingLeaveRequests());
                    case 4 -> approve();
                    case 5 -> reject();
                    case 6 -> Views.leaves(io, s.leave.getLeaveHistory(io.readId("Employee ID: ")));
                    case 7 -> balances(io.readId("Employee ID: "));
                    default -> io.error("Invalid choice.");
                }
            });
        }
    }

    /** Shared with the self-service menu. */
    void submit(Long employeeId) {
        LeaveType type = io.readEnum("Leave type", LeaveType.class);
        LocalDate start = io.readDate("Start date");
        LocalDate end = io.readDate("End date");
        String reason = io.readRequired("Reason: ");
        LeaveRequestDto created = s.leave.submitLeaveRequest(employeeId, type, start, end, reason);
        io.success("Leave request #" + created.id() + " submitted for " + created.numberOfDays()
                + " working day(s). Status: " + created.status() + ".");
    }

    void balances(Long employeeId) {
        int year = io.readInt("Year (e.g. " + LocalDate.now().getYear() + "): ", 2000, 2100);
        List<LeaveBalanceDto> balances = s.leave.getLeaveBalances(employeeId, year);
        TableFormatter t = new TableFormatter("Type", "Annual quota", "Approved", "Pending", "Available");
        balances.forEach(b -> t.row(b.leaveType(), b.quota() == null ? "unlimited" : b.quota(),
                b.approvedDays(), b.pendingDays(), b.available() == null ? "-" : b.available()));
        io.println(t.render());
    }

    private void approve() {
        Views.leaves(io, s.leave.getPendingLeaveRequests());
        Long id = io.readId("Leave request ID to approve: ");
        LeaveRequestDto result = s.leave.approveLeave(id, io.readOptional("Remarks"));
        io.success("Leave #" + result.id() + " approved. Attendance was updated for the leave days.");
    }

    private void reject() {
        Views.leaves(io, s.leave.getPendingLeaveRequests());
        Long id = io.readId("Leave request ID to reject: ");
        LeaveRequestDto result = s.leave.rejectLeave(id, io.readRequired("Reason for rejection: "));
        io.success("Leave #" + result.id() + " rejected.");
    }
}
