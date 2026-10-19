package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.entity.ActivityLog;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit entries in their own transaction so an audit failure can never
 * roll back or break the authentication flow that produced it.
 */
@Service
public class ActivityLogServiceImpl implements ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogServiceImpl.class);
    private static final int MAX_DESCRIPTION_LENGTH = 512;

    private final ActivityLogRepository activityLogRepository;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public ActivityLogServiceImpl(
            ActivityLogRepository activityLogRepository,
            ObjectProvider<HttpServletRequest> requestProvider) {
        this.activityLogRepository = activityLogRepository;
        this.requestProvider = requestProvider;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, ActivityType type, String description, boolean successful) {
        try {
            HttpServletRequest request = requestProvider.getIfAvailable();
            activityLogRepository.save(ActivityLog.builder()
                    .user(user)
                    .activityType(type)
                    .description(truncate(description))
                    .ipAddress(RequestUtils.clientIp(request))
                    .userAgent(RequestUtils.userAgent(request))
                    .successful(successful)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Unable to persist activity log [{}]: {}", type, ex.getMessage());
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_DESCRIPTION_LENGTH ? value.substring(0, MAX_DESCRIPTION_LENGTH) : value;
    }
}
