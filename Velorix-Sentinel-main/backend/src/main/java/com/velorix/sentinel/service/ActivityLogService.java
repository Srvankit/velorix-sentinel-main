package com.velorix.sentinel.service;

import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;

/**
 * Writes the security audit trail. Never throws into the calling flow.
 */
public interface ActivityLogService {

    void record(User user, ActivityType type, String description, boolean successful);

    default void success(User user, ActivityType type, String description) {
        record(user, type, description, true);
    }

    default void failure(User user, ActivityType type, String description) {
        record(user, type, description, false);
    }
}
