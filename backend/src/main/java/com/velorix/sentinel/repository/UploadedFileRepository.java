package com.velorix.sentinel.repository;

import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.repository.spec.UploadedFileSpecifications;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadedFileRepository
        extends JpaRepository<UploadedFile, UUID>, JpaSpecificationExecutor<UploadedFile> {

    /**
     * Owner-scoped listing is built with the Criteria API in {@link UploadedFileSpecifications}
     * and executed through {@link JpaSpecificationExecutor#findAll(
     * org.springframework.data.jpa.domain.Specification, Pageable)}. The previous JPQL variant
     * used {@code :search IS NULL}, which left the parameter untyped and made PostgreSQL resolve
     * it as {@code bytea}.
     */

    Optional<UploadedFile> findByIdAndUserId(UUID id, UUID userId);

    Optional<UploadedFile> findByStoredFileName(String storedFileName);

    Optional<UploadedFile> findByUserIdAndChecksumAndUploadStatusNot(
            UUID userId, String checksum, UploadStatus excluded);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndUploadStatusNot(UUID userId, UploadStatus excluded);

    /** Dashboard counter: live captures for the owner scope ({@code null} = all accounts). */
    default long countForOwner(UUID userId) {
        return count(UploadedFileSpecifications.visible(userId, null, null));
    }
}

