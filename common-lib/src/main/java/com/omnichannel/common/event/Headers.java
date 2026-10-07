package com.omnichannel.common.event;

/** Trusted identity headers added by the API gateway. */
public final class Headers {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_PREFIX = "X-User-";

    private Headers() {
    }
}
