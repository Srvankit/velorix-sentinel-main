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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Single-use, short-lived (60-second) authorization code issued after successful
 * OAuth authentication to exchange for a full JWT session.
 *
 * <p>The raw code is never persisted; only its SHA-256 digest is stored.</p>
 */
@Entity
@Table(
        name = "oauth_exchange_codes",
        indexes = {
                @Index(name = "idx_oauth_exchange_codes_hash", columnList = "code_hash", unique = true),
                @Index(name = "idx_oauth_exchange_codes_user", columnList = "user_id"),
                @Index(name = "idx_oauth_exchange_codes_expires", columnList = "expires_at")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthExchangeCode extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_oauth_exchange_codes_user"))
    private User user;

    /** SHA-256 hash of the one-time code — the raw value is never stored. */
    @ToString.Exclude
    @Column(name = "code_hash", nullable = false, unique = true, length = 128)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isUsable(Instant now) {
        return !isConsumed() && !isExpired(now);
    }
}
