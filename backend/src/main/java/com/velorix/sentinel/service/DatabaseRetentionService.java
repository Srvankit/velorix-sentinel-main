package com.velorix.sentinel.service;

import java.time.Instant;

/**
 * Service managing automated lifecycle retention purging for expired tokens,
 * stale audit logs, and soft-deleted capture files.
 */
public interface DatabaseRetentionService {

    /**
     * Executes the complete retention cleanup across all supported domains.
     *
     * @return a report summarizing purged counts and status
     */
    RetentionReport runRetentionCleanup();

    /**
     * Purges expired refresh tokens older than the provided cutoff.
     *
     * @param cutoff tokens expiring before this instant are eligible for deletion
     * @param batchSize maximum records to process in a single transaction
     * @return total count of deleted refresh tokens
     */
    int purgeExpiredRefreshTokens(Instant cutoff, int batchSize);

    /**
     * Purges expired verification tokens older than the provided cutoff.
     * Unexpired tokens are never purged, regardless of whether they have been consumed.
     *
     * @param cutoff tokens expiring before this instant are eligible for deletion
     * @param batchSize maximum records to process in a single transaction
     * @return total count of deleted verification tokens
     */
    int purgeExpiredVerificationTokens(Instant cutoff, int batchSize);

    /**
     * Purges activity audit logs created before the provided cutoff.
     *
     * @param cutoff logs created before this instant are eligible for deletion
     * @param batchSize maximum records to process in a single transaction
     * @return total count of deleted activity logs
     */
    int purgeStaleActivityLogs(Instant cutoff, int batchSize);

    /**
     * Hard-deletes soft-deleted files older than the provided cutoff along with
     * associated capture binaries and cascaded analysis records.
     *
     * @param cutoff soft-deleted files with updatedAt before this instant are eligible for deletion
     * @param batchSize maximum records to process in a single transaction
     * @return total count of deleted files
     */
    int purgeSoftDeletedFiles(Instant cutoff, int batchSize);

    /**
     * Execution summary report for retention jobs.
     */
    record RetentionReport(
            boolean success,
            int refreshTokensPurged,
            int verificationTokensPurged,
            int activityLogsPurged,
            int softDeletedFilesPurged,
            String errorMessage) {

        public static RetentionReport success(
                int refreshTokens, int verificationTokens, int activityLogs, int softDeletedFiles) {
            return new RetentionReport(true, refreshTokens, verificationTokens, activityLogs, softDeletedFiles, null);
        }

        public static RetentionReport partialFailure(
                int refreshTokens, int verificationTokens, int activityLogs, int softDeletedFiles, String error) {
            return new RetentionReport(false, refreshTokens, verificationTokens, activityLogs, softDeletedFiles, error);
        }
    }
}
