package com.mca.payroll.util;

import com.mca.payroll.dto.AuthenticatedUser;
import com.mca.payroll.entity.Role;
import com.mca.payroll.exception.UnauthorizedOperationException;

import java.util.Arrays;

/**
 * Holds the signed-in user of this (single-user, console) application and performs the
 * role checks that every sensitive service method starts with.
 *
 * <p>The role always comes from the authenticated {@link AuthenticatedUser} that
 * AuthService produced after verifying a password - it is never typed in at a prompt.</p>
 */
public final class SecurityContext {

    private static volatile AuthenticatedUser currentUser;

    private SecurityContext() {
    }

    public static void setCurrentUser(AuthenticatedUser user) {
        currentUser = user;
    }

    public static void clear() {
        currentUser = null;
    }

    public static AuthenticatedUser currentUserOrNull() {
        return currentUser;
    }

    /** The signed-in user; throws when nobody is signed in. */
    public static AuthenticatedUser requireAuthenticated() {
        AuthenticatedUser user = currentUser;
        if (user == null) {
            throw new UnauthorizedOperationException("You must sign in to perform this operation.");
        }
        return user;
    }

    /** The signed-in user must have one of the given roles. */
    public static AuthenticatedUser requireRole(Role... allowedRoles) {
        AuthenticatedUser user = requireAuthenticated();
        for (Role role : allowedRoles) {
            if (user.role() == role) {
                return user;
            }
        }
        throw new UnauthorizedOperationException("Your role (" + user.role()
                + ") is not allowed to perform this operation. Required: " + Arrays.toString(allowedRoles) + ".");
    }

    /** True for ADMIN and HR. */
    public static boolean isStaff() {
        AuthenticatedUser user = currentUser;
        return user != null && user.isStaff();
    }

    /**
     * ADMIN/HR may access any employee's data; an EMPLOYEE may access only their own.
     *
     * @return the signed-in user
     */
    public static AuthenticatedUser requireSelfOrStaff(Long employeeId) {
        AuthenticatedUser user = requireAuthenticated();
        if (user.isStaff()) {
            return user;
        }
        if (user.employeeId() != null && user.employeeId().equals(employeeId)) {
            return user;
        }
        throw new UnauthorizedOperationException("You can only access your own records.");
    }

    /** Name recorded in audit columns (generated_by, reviewed_by ...). */
    public static String currentUsername() {
        AuthenticatedUser user = currentUser;
        return user == null ? "system" : user.username();
    }
}
