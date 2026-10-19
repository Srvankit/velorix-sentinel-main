package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.VerificationToken;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    @Modifying
    @Query("update VerificationToken vt set vt.consumedAt = :now "
            + "where vt.user.id = :userId and vt.purpose = :purpose and vt.consumedAt is null")
    int consumeAllForUser(
            @Param("userId") UUID userId,
            @Param("purpose") TokenPurpose purpose,
            @Param("now") Instant now);

    @Modifying
    @Query("delete from VerificationToken vt where vt.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
