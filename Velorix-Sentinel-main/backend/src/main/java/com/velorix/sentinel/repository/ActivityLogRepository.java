package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.ActivityLog;
import com.velorix.sentinel.entity.enums.ActivityType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    Page<ActivityLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<ActivityLog> findAllByActivityTypeOrderByCreatedAtDesc(ActivityType activityType, Pageable pageable);

    /** Platform-wide audit feed, used by administrators on the dashboard. */
    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

}
