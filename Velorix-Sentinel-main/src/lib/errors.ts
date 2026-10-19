/**
 * Centralised error classification.
 *
 * Every failure the UI can surface is normalised into one `AppErrorKind` with a
 * user-friendly title and message, so components never have to interpret raw
 * Axios/HTTP details.
 */

import { apiConfig } from "@/utils/apiConfig";
import type { ApiError } from "@/types/api";

export type AppErrorKind =
  | "configuration"
  | "backend-unavailable"
  | "timeout"
  | "authentication"
  | "forbidden"
  | "not-found"
  | "validation"
  | "conflict"
  | "rate-limited"
  | "server"
  | "unknown";

export interface DescribedError {
  kind: AppErrorKind;
  title: string;
  message: string;
  /** True when retrying the exact same request can plausibly succeed. */
  retryable: boolean;
  fieldErrors?: Record<string, string>;
}

function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === "object" &&
    value !== null &&
    "status" in value &&
    "code" in value &&
    "message" in value
  );
}

const GENERIC: DescribedError = {
  kind: "unknown",
  title: "Something went wrong",
  message: "An unexpected error occurred. Please try again.",
  retryable: true,
};

/** Turns any thrown value into a classified, presentable error. */
export function describeError(error: unknown): DescribedError {
  if (!error) return GENERIC;

  if (!isApiError(error)) {
    const message = error instanceof Error ? error.message : String(error);
    return { ...GENERIC, message: message || GENERIC.message };
  }

  const { status, code, message, fieldErrors } = error;

  if (code === "API_CONFIG_ERROR") {
    return {
      kind: "configuration",
      title: apiConfig.problem?.title ?? "API is not configured",
      message: apiConfig.problem?.message ?? message,
      retryable: false,
    };
  }

  if (code === "API_TIMEOUT") {
    return {
      kind: "timeout",
      title: "The API took too long to respond",
      message:
        "The request timed out before the Velorix Sentinel API answered. Check that the backend is running and responsive, then try again.",
      retryable: true,
    };
  }

  if (code === "API_UNREACHABLE" || status === 0) {
    const origin = typeof window !== "undefined" ? window.location.origin : "this origin";
    return {
      kind: "backend-unavailable",
      title: "Backend unavailable",
      message:
        `Velorix Sentinel could not reach the API at ${apiConfig.baseUrl}. ` +
        `The service may be starting up, or it may not allow browser requests from ${origin} ` +
        "(CORS_ALLOWED_ORIGINS on the backend). Please retry in a moment.",
      retryable: true,
    };
  }

  if (status === 401) {
    return {
      kind: "authentication",
      title: "Authentication required",
      message: message || "Your session has expired. Please sign in again.",
      retryable: false,
    };
  }

  if (status === 403) {
    return {
      kind: "forbidden",
      title: "Access denied",
      message: message || "Your account does not have permission to perform this action.",
      retryable: false,
    };
  }

  if (status === 404) {
    return {
      kind: "not-found",
      title: "Not found",
      message: message || "The requested resource no longer exists.",
      retryable: false,
    };
  }

  if (status === 409) {
    return {
      kind: "conflict",
      title: "Conflict",
      message: message || "This action conflicts with existing data.",
      retryable: false,
    };
  }

  if (status === 422 || status === 400) {
    return {
      kind: "validation",
      title: "Check the submitted details",
      message: message || "Some of the submitted values are invalid.",
      retryable: false,
      fieldErrors,
    };
  }

  if (status === 429) {
    return {
      kind: "rate-limited",
      title: "Too many requests",
      message: message || "You've made too many requests. Wait a moment and try again.",
      retryable: true,
    };
  }

  if (status >= 500) {
    return {
      kind: "server",
      title: "Server error",
      message: message || "The API failed while handling this request. Please try again shortly.",
      retryable: true,
    };
  }

  return { ...GENERIC, message: message || GENERIC.message, fieldErrors };
}

/** Convenience helper for compact surfaces (toasts, inline banners). */
export function errorMessage(error: unknown): string {
  return describeError(error).message;
}
