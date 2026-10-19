package com.velorix.sentinel.entity.enums;

import com.velorix.sentinel.constants.SecurityConstants;

/**
 * Platform roles. Authority names are prefixed with {@code ROLE_} for Spring Security.
 */
public enum Role {

    ADMIN,
    ANALYST,
    USER;

    public String authority() {
        return SecurityConstants.ROLE_PREFIX + name();
    }
}
