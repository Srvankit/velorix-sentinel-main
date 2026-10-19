package com.velorix.sentinel.constants;

/**
 * Security related constants shared by the JWT infrastructure.
 */
public final class SecurityConstants {

    public static final String ROLE_PREFIX = "ROLE_";

    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_TOKEN_ID = "jti";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final int BCRYPT_STRENGTH = 12;

    private SecurityConstants() {
        throw new AssertionError("No instances allowed");
    }
}
