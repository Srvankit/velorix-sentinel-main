package com.velorix.sentinel.entity.enums;

/**
 * Auditable activity categories written to {@code activity_logs}.
 */
public enum ActivityType {

    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    REGISTRATION,
    EMAIL_VERIFICATION,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    TOKEN_REFRESHED,
    TOKEN_REVOKED,
    PROFILE_UPDATED,
    ROLE_CHANGED,
    ACCOUNT_DEACTIVATED,
    FILE_UPLOADED,
    FILE_UPLOAD_FAILED,
    FILE_DELETED,
    ANALYSIS_STARTED,
    ANALYSIS_COMPLETED,
    ANALYSIS_FAILED
}
