/** Capture ingestion domain types (mirrors the Spring Boot file contract). */

export type UploadStatus = "UPLOADED" | "VALIDATING" | "READY_FOR_ANALYSIS" | "FAILED" | "DELETED";

export interface UploadedFile {
  id: string;
  ownerId: string;
  originalFileName: string;
  storedFileName: string;
  fileSize: number;
  fileType: string;
  uploadStatus: UploadStatus;
  checksum: string;
  createdAt: string;
  updatedAt: string;
}

export interface UploadedFileQuery {
  status?: UploadStatus;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "ASC" | "DESC";
}

/** Accepted capture extensions, kept in sync with `velorix.storage.allowed-extensions`. */
export const ACCEPTED_EXTENSIONS = [".pcap", ".pcapng"] as const;

/** Maximum accepted capture size in bytes (100 MB), mirrors the backend limit. */
export const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;

export const UPLOAD_STATUS_LABELS: Record<UploadStatus, string> = {
  UPLOADED: "Uploaded",
  VALIDATING: "Validating",
  READY_FOR_ANALYSIS: "Ready for analysis",
  FAILED: "Failed",
  DELETED: "Deleted",
};
