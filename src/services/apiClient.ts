import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";

import type { ApiError, ApiResponse } from "@/types/api";
import { API_BASE_URL, API_TIMEOUT_MS, apiConfig } from "@/utils/apiConfig";

/**
 * Centralised HTTP client.
 *
 * Responsibilities:
 *  - attach the JWT access token to every outgoing request
 *  - unwrap the backend `ApiResponse<T>` envelope
 *  - normalise every failure into an `ApiError`
 *  - transparently refresh an expired access token once, then replay the request
 *  - delegate terminal 401 handling to the registered session handlers (auth context)
 */

declare module "axios" {
  export interface AxiosRequestConfig {
    /** Opt out of the automatic refresh-and-replay (used by the auth endpoints). */
    skipAuthRefresh?: boolean;
  }
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;
/** Returns the new access token, or null when the session cannot be renewed. */
type RefreshHandler = () => Promise<string | null>;

let getAccessToken: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = () => {};
let refreshSession: RefreshHandler = async () => null;

/** Wired once by the auth context so the client stays framework-agnostic. */
export function configureApiClient(options: {
  getAccessToken: TokenProvider;
  onUnauthorized: UnauthorizedHandler;
  refreshSession?: RefreshHandler;
}): void {
  getAccessToken = options.getAccessToken;
  onUnauthorized = options.onUnauthorized;
  refreshSession = options.refreshSession ?? (async () => null);
}

export const httpClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT_MS,
  headers: { "Content-Type": "application/json" },
});

/** Thrown before any network call when the API URL cannot work in this environment. */
export const apiConfigError: ApiError | null = apiConfig.problem
  ? {
      status: 0,
      code: "API_CONFIG_ERROR",
      message: apiConfig.problem.message,
    }
  : null;

httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // Fail fast and gracefully instead of firing a request that can never succeed.
  if (!apiConfig.isUsable && apiConfigError) {
    throw apiConfigError;
  }
  const token = getAccessToken();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

export function toApiError(error: unknown): ApiError {
  if (isApiError(error)) return error;

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{
      message?: string;
      errors?: Array<{ field?: string | null; code?: string; message?: string }>;
    }>;
    const status = axiosError.response?.status ?? 0;
    const raw = axiosError.response?.data;
    /** The API always answers JSON; an HTML/string body means the request never reached it. */
    const reachedApi = typeof raw === "object" && raw !== null;
    const payload = reachedApi ? raw : undefined;
    const details = payload?.errors ?? [];

    const fieldErrors: Record<string, string> = {};
    for (const detail of details) {
      if (detail.field && detail.message) fieldErrors[detail.field] = detail.message;
    }

    if (axiosError.code === "ECONNABORTED" || axiosError.code === "ETIMEDOUT") {
      return { status: 0, code: "API_TIMEOUT", message: "The API request timed out." };
    }

    const unreachable =
      status === 0 || status === 502 || status === 503 || status === 504 ||
      (!reachedApi && (status === 404 || status >= 500));

    return {
      status,
      code: unreachable
        ? "API_UNREACHABLE"
        : (details.find((detail) => detail.code)?.code ?? `HTTP_${status}`),
      message: unreachable
        ? status === 504
          ? "The API did not respond in time. The backend may be starting up; please try again."
          : "Unable to reach the Velorix Sentinel API. Make sure the backend is running and VITE_API_BASE_URL points at it."
        : (payload?.message ?? axiosError.message),
      fieldErrors: Object.keys(fieldErrors).length > 0 ? fieldErrors : undefined,
    };
  }

  return {
    status: 0,
    code: "UNKNOWN_ERROR",
    message: error instanceof Error ? error.message : "An unexpected error occurred.",
  };
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

/** Single-flight refresh so parallel 401s trigger exactly one rotation. */
let pendingRefresh: Promise<string | null> | null = null;

function refreshOnce(): Promise<string | null> {
  pendingRefresh ??= refreshSession().finally(() => {
    pendingRefresh = null;
  });
  return pendingRefresh;
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const apiError = toApiError(error);

    if (apiError.status === 401 && axios.isAxiosError(error)) {
      const config = error.config as
        (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

      if (config && !config.skipAuthRefresh && !config._retried) {
        config._retried = true;
        const token = await refreshOnce();
        if (token) {
          config.headers.set("Authorization", `Bearer ${token}`);
          return httpClient.request(config);
        }
      }
      onUnauthorized();
    }

    return Promise.reject(apiError);
  },
);

/** Unwraps `ApiResponse<T>` envelopes, tolerating bare payloads. */
function unwrap<T>(data: ApiResponse<T> | T): T {
  if (data && typeof data === "object" && "data" in (data as ApiResponse<T>)) {
    return (data as ApiResponse<T>).data;
  }
  return data as T;
}

export const api = {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await httpClient.get<ApiResponse<T>>(url, config);
    return unwrap<T>(response.data);
  },

  async post<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
    const response = await httpClient.post<ApiResponse<T>>(url, body, config);
    return unwrap<T>(response.data);
  },

  async put<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
    const response = await httpClient.put<ApiResponse<T>>(url, body, config);
    return unwrap<T>(response.data);
  },

  async patch<T, B = unknown>(url: string, body?: B, config?: AxiosRequestConfig): Promise<T> {
    const response = await httpClient.patch<ApiResponse<T>>(url, body, config);
    return unwrap<T>(response.data);
  },

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await httpClient.delete<ApiResponse<T>>(url, config);
    return unwrap<T>(response.data);
  },
};
