/**
 * Shared API contract primitives returned by the SentinelAI Spring Boot backend.
 */

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
}

/** Normalised error shape produced by the API client for every failure. */
export interface ApiError {
  status: number;
  code: string;
  message: string;
  /** Field-level validation errors keyed by field name. */
  fieldErrors?: Record<string, string>;
}

export type RequestState = "idle" | "loading" | "success" | "error";
