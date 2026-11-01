package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(UUID userId);

    List<RefreshToken> findAllByFamilyId(UUID familyId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.familyId = :familyId AND rt.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true, rt.revokedAt = :now "
            + "where rt.user.id = :userId and rt.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);

    @Query("select rt.id from RefreshToken rt where rt.expiresAt < :cutoff")
    List<UUID> findExpiredIdsBefore(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("delete from RefreshToken rt where rt.id in :ids")
    int deleteByIdsIn(@Param("ids") List<UUID> ids);
}
