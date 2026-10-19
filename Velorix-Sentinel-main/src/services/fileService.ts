import { api, httpClient } from "./apiClient";
import type { ApiResponse, PageResponse } from "@/types/api";
import type { UploadedFile, UploadedFileQuery } from "@/types/file";

/** Capture ingestion endpoints exposed by `/api/v1/files`. */
export const FILE_ENDPOINTS = {
  base: "/v1/files",
  upload: "/v1/files/upload",
  byId: (id: string) => `/v1/files/${id}`,
} as const;

export const fileService = {
  /** GET /api/v1/files — paginated, owner-scoped capture listing. */
  list: (query: UploadedFileQuery = {}, signal?: AbortSignal) =>
    api.get<PageResponse<UploadedFile>>(FILE_ENDPOINTS.base, {
      params: {
        status: query.status,
        search: query.search || undefined,
        page: query.page,
        size: query.size,
        sort: query.sort,
        direction: query.direction,
      },
      signal,
    }),

  /** GET /api/v1/files/{id} */
  getById: (id: string) => api.get<UploadedFile>(FILE_ENDPOINTS.byId(id)),

  /** DELETE /api/v1/files/{id} */
  remove: (id: string) => api.delete<void>(FILE_ENDPOINTS.byId(id)),

  /**
   * POST /api/v1/files/upload — real multipart upload with byte-level progress.
   *
   * The browser sets the multipart boundary itself, so the JSON default content
   * type is explicitly removed for this request.
   */
  async upload(
    file: File,
    options: { onProgress?: (percent: number) => void; signal?: AbortSignal } = {},
  ): Promise<UploadedFile> {
    const body = new FormData();
    body.append("file", file);

    const response = await httpClient.post<ApiResponse<UploadedFile>>(FILE_ENDPOINTS.upload, body, {
      headers: { "Content-Type": undefined },
      signal: options.signal,
      timeout: 0,
      onUploadProgress: (event) => {
        if (!options.onProgress) return;
        const total = event.total ?? file.size;
        if (!total) return;
        options.onProgress(Math.min(100, Math.round((event.loaded / total) * 100)));
      },
    });

    return response.data.data;
  },
};
