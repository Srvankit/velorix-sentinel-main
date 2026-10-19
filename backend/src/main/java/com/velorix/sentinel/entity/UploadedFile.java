package com.velorix.sentinel.entity;

import com.velorix.sentinel.entity.enums.UploadStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A capture (PCAP/PCAPNG) uploaded by a user and stored for inspection.
 *
 * <p>The binary itself lives in object storage; this aggregate only tracks its
 * identity, integrity checksum and ingestion status.</p>
 */
@Entity
@Table(
        name = "uploaded_files",
        indexes = {
                @Index(name = "idx_uploaded_files_user", columnList = "user_id"),
                @Index(name = "idx_uploaded_files_status", columnList = "upload_status"),
                @Index(name = "idx_uploaded_files_created_at", columnList = "created_at"),
                @Index(name = "idx_uploaded_files_checksum", columnList = "checksum")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedFile extends BaseEntity {

    /** Owner of the capture. Every read is scoped to this user unless the caller is an admin. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_uploaded_files_user"))
    private User user;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** Opaque name under which the capture is persisted in storage. */
    @Column(name = "stored_file_name", nullable = false, unique = true, length = 255)
    private String storedFileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "upload_status", nullable = false, length = 24)
    private UploadStatus uploadStatus = UploadStatus.UPLOADED;

    /** SHA-256 of the stored bytes, used for integrity and duplicate detection. */
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @ToString.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "uploadedFile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Analysis> analyses = new ArrayList<>();
}
