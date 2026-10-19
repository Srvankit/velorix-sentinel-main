package com.velorix.sentinel.repository.spec;

import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.enums.UploadStatus;
import jakarta.persistence.criteria.Expression;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria API predicates for the uploaded-file listing.
 *
 * <p>Built with the Criteria API rather than JPQL so every bind parameter carries an explicit
 * Java type. PostgreSQL then receives typed placeholders and never has to guess, which is what
 * previously produced {@code function lower(bytea) does not exist}.</p>
 */
public final class UploadedFileSpecifications {

    private UploadedFileSpecifications() {
    }

    /** {@code null} owner means "unrestricted" (administrators). */
    public static Specification<UploadedFile> ownedBy(UUID userId) {
        return (root, query, cb) -> userId == null
                ? cb.conjunction()
                : cb.equal(root.get("user").get("id"), userId);
    }

    /** Soft deleted captures are never visible. */
    public static Specification<UploadedFile> notDeleted() {
        return (root, query, cb) -> cb.notEqual(root.get("uploadStatus"), UploadStatus.DELETED);
    }

    public static Specification<UploadedFile> hasStatus(UploadStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("uploadStatus"), status);
    }

    /** Case-insensitive contains match on the original file name; blank/null matches everything. */
    public static Specification<UploadedFile> nameContains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            Expression<String> fileName = root.get("originalFileName");
            return cb.like(cb.lower(fileName), cb.literal(pattern));
        };
    }

    public static Specification<UploadedFile> visible(UUID userId, UploadStatus status, String search) {
        return Specification.allOf(ownedBy(userId), notDeleted(), hasStatus(status), nameContains(search));
    }
}
