package com.velorix.sentinel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Persisted refresh token enabling rotation and server side revocation.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at"),
                @Index(name = "idx_refresh_tokens_family_id", columnList = "family_id"),
                @Index(name = "idx_refresh_tokens_previous_hash", columnList = "previous_token_hash")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_user"))
    private User user;

    /** SHA-256 hash of the issued refresh token - the raw value is never stored. */
    @ToString.Exclude
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    /** Unique identifier for the token family (session chain). */
    @Builder.Default
    @Column(name = "family_id", nullable = false)
    private UUID familyId = UUID.randomUUID();

    /** SHA-256 hash of the parent token that this token replaced upon rotation. */
    @ToString.Exclude
    @Column(name = "previous_token_hash", length = 128)
    private String previousTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isUsable(Instant now) {
        return !revoked && !isExpired(now);
    }
}
