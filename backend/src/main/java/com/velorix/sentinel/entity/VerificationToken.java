package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.TokenPurpose;
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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Single use, hashed token backing email verification and password reset flows.
 */
@Entity
@Table(
        name = "verification_tokens",
        indexes = {
                @Index(name = "idx_verification_tokens_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_verification_tokens_user", columnList = "user_id"),
                @Index(name = "idx_verification_tokens_purpose", columnList = "purpose")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_verification_tokens_user"))
    private User user;

    /** SHA-256 hash of the emailed token - the raw value is never stored. */
    @ToString.Exclude
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private TokenPurpose purpose;

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
