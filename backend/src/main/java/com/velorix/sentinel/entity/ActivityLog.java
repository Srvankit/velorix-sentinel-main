package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Append-only audit trail of security relevant user activity.
 */
@Entity
@Table(
        name = "activity_logs",
        indexes = {
                @Index(name = "idx_activity_logs_user", columnList = "user_id"),
                @Index(name = "idx_activity_logs_type", columnList = "activity_type"),
                @Index(name = "idx_activity_logs_created_at", columnList = "created_at")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog extends BaseEntity {

    /** Nullable: failed logins may not resolve to a known account. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "fk_activity_logs_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ActivityType activityType;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Builder.Default
    @Column(name = "successful", nullable = false)
    private boolean successful = true;
}
