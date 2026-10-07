package com.omnichannel.common.security;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;

import java.util.Arrays;

/** Helpers for the trusted X-User-Roles header (comma separated) set by the gateway. */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String CUSTOMER = "CUSTOMER";

    private Roles() {
    }

    public static boolean isAdmin(String rolesHeader) {
        return rolesHeader != null && Arrays.asList(rolesHeader.split(",")).contains(ADMIN);
    }

    public static void requireAdmin(String rolesHeader) {
        if (!isAdmin(rolesHeader)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}
