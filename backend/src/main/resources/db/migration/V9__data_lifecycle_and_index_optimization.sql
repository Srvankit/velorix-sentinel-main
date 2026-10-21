-- V9: Data Lifecycle & Packet Index Optimization (Task 2.3)
--
-- 1. Redundant Standalone Index Elimination:
--    V3:73 defines CONSTRAINT uk_packets_analysis_number UNIQUE (analysis_id, packet_number),
--    which automatically creates a backing unique b-tree index on (analysis_id, packet_number).
--    V5:37 subsequently created idx_packets_analysis_number ON packets (analysis_id, packet_number),
--    which is a 100% duplicate standalone index on the exact same columns in the exact same order.
--    Dropping this duplicate index eliminates redundant write amplification and disk overhead
--    on bulk packet ingestion without impacting query plans or uniqueness guarantees.
--
-- 2. Preserved Indexes:
--    - uk_packets_analysis_number: Preserved to enforce uniqueness and support compound lookups.
--    - idx_packets_analysis: Preserved because a single-column index on analysis_id has a smaller
--      page footprint than the compound index, and there is no production profiling evidence
--      justifying its removal.
--    - idx_packets_analysis_protocol: Preserved for protocol histogram and filtering queries.
--    - idx_packets_analysis_timestamp: Preserved for time-ordered packet paging.
--    - idx_packets_suspicious: Preserved for quick suspicious packet filtering.

DROP INDEX IF EXISTS idx_packets_analysis_number;
