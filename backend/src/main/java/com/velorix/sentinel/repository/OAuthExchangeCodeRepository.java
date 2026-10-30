package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.OAuthExchangeCode;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link OAuthExchangeCode}.
 */
@Repository
public interface OAuthExchangeCodeRepository extends JpaRepository<OAuthExchangeCode, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM OAuthExchangeCode c JOIN FETCH c.user WHERE c.codeHash = :codeHash")
    Optional<OAuthExchangeCode> findByCodeHashWithLock(@Param("codeHash") String codeHash);

    Optional<OAuthExchangeCode> findByCodeHash(String codeHash);

    @Modifying
    @Query("DELETE FROM OAuthExchangeCode c WHERE c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
