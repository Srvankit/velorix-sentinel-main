import axios, { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  api,
  configureApiClient,
  httpClient,
  toApiError,
} from "@/services/apiClient";

describe("apiClient service (Task 7.3 Unit Tests)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    configureApiClient({
      getAccessToken: () => null,
      onUnauthorized: () => {},
      refreshSession: async () => null,
    });
  });

  describe("toApiError mapping", () => {
    it("preserves an already constructed ApiError", () => {
      const existing = { status: 403, code: "FORBIDDEN", message: "Forbidden" };
      expect(toApiError(existing)).toBe(existing);
    });

    it("parses structured backend ApiResponse validation errors", () => {
      const axiosError = new AxiosError("Request failed with status code 400");
      axiosError.response = {
        status: 400,
        statusText: "Bad Request",
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: {
          success: false,
          message: "Validation failed",
          errors: [
            { field: "email", message: "Email format is invalid", code: "INVALID_EMAIL" },
            { field: "password", message: "Password too short", code: "PASSWORD_TOO_SHORT" },
          ],
        },
      };

      const result = toApiError(axiosError);
      expect(result.status).toBe(400);
      expect(result.code).toBe("INVALID_EMAIL");
      expect(result.message).toBe("Validation failed");
      expect(result.fieldErrors).toEqual({
        email: "Email format is invalid",
        password: "Password too short",
      });
    });

    it("maps timeout errors (ECONNABORTED) to API_TIMEOUT with status 0", () => {
      const axiosError = new AxiosError("Timeout");
      axiosError.code = "ECONNABORTED";

      const result = toApiError(axiosError);
      expect(result.status).toBe(0);
      expect(result.code).toBe("API_TIMEOUT");
      expect(result.message).toBe("The API request timed out.");
    });

    it("maps 504 Gateway Timeout to cold-start friendly message", () => {
      const axiosError = new AxiosError("Gateway Timeout");
      axiosError.response = {
        status: 504,
        statusText: "Gateway Timeout",
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: "<html>Gateway Timeout</html>",
      };

      const result = toApiError(axiosError);
      expect(result.status).toBe(504);
      expect(result.code).toBe("API_UNREACHABLE");
      expect(result.message).toContain("The backend may be starting up");
    });

    it("maps 502/503 and status 0 network drops to API_UNREACHABLE", () => {
      const axiosError = new AxiosError("Network Error");
      axiosError.response = undefined; // status 0

      const result = toApiError(axiosError);
      expect(result.status).toBe(0);
      expect(result.code).toBe("API_UNREACHABLE");
      expect(result.message).toContain("Unable to reach the Velorix Sentinel API");
    });

    it("maps unexpected non-Axios Error instances to UNKNOWN_ERROR", () => {
      const err = new Error("Something blew up unexpectedly");
      const result = toApiError(err);

      expect(result.status).toBe(0);
      expect(result.code).toBe("UNKNOWN_ERROR");
      expect(result.message).toBe("Something blew up unexpectedly");
    });
  });

  describe("Request interceptor: Token injection", () => {
    it("injects Authorization header when token is available", async () => {
      configureApiClient({
        getAccessToken: () => "sample-jwt-token-xyz",
        onUnauthorized: () => {},
      });

      const config: InternalAxiosRequestConfig = {
        headers: new AxiosHeaders(),
      };

      // Retrieve the request interceptor handler
      const requestHandlers = (httpClient.interceptors.request as unknown as {
        handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
      }).handlers;

      const fulfilled = requestHandlers[requestHandlers.length - 1].fulfilled;
      const modifiedConfig = await fulfilled(config);

      expect(modifiedConfig.headers.get("Authorization")).toBe("Bearer sample-jwt-token-xyz");
    });

    it("does not inject Authorization header when token is null", async () => {
      configureApiClient({
        getAccessToken: () => null,
        onUnauthorized: () => {},
      });

      const config: InternalAxiosRequestConfig = {
        headers: new AxiosHeaders(),
      };

      const requestHandlers = (httpClient.interceptors.request as unknown as {
        handlers: Array<{ fulfilled: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }>;
      }).handlers;

      const fulfilled = requestHandlers[requestHandlers.length - 1].fulfilled;
      const modifiedConfig = await fulfilled(config);

      expect(modifiedConfig.headers.get("Authorization")).toBeUndefined();
    });
  });

  describe("Response interceptor: 401 refresh flow", () => {
    it("calls onUnauthorized when refresh fails or returns null", async () => {
      const onUnauthorized = vi.fn();
      const refreshSession = vi.fn().mockResolvedValue(null);

      configureApiClient({
        getAccessToken: () => "expired-token",
        onUnauthorized,
        refreshSession,
      });

      const responseHandlers = (httpClient.interceptors.response as unknown as {
        handlers: Array<{ rejected: (error: unknown) => Promise<unknown> }>;
      }).handlers;

      const rejected = responseHandlers[responseHandlers.length - 1].rejected;

      const axiosError = new AxiosError("Unauthorized");
      axiosError.response = {
        status: 401,
        statusText: "Unauthorized",
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: { message: "Token expired" },
      };
      axiosError.config = {
        headers: new AxiosHeaders(),
      } as InternalAxiosRequestConfig;

      await expect(rejected(axiosError)).rejects.toMatchObject({
        status: 401,
      });

      expect(refreshSession).toHaveBeenCalledTimes(1);
      expect(onUnauthorized).toHaveBeenCalledTimes(1);
    });

    it("skips auth refresh when skipAuthRefresh is set", async () => {
      const onUnauthorized = vi.fn();
      const refreshSession = vi.fn().mockResolvedValue("new-token");

      configureApiClient({
        getAccessToken: () => null,
        onUnauthorized,
        refreshSession,
      });

      const responseHandlers = (httpClient.interceptors.response as unknown as {
        handlers: Array<{ rejected: (error: unknown) => Promise<unknown> }>;
      }).handlers;

      const rejected = responseHandlers[responseHandlers.length - 1].rejected;

      const axiosError = new AxiosError("Unauthorized");
      axiosError.response = {
        status: 401,
        statusText: "Unauthorized",
        headers: {},
        config: { headers: new AxiosHeaders() },
        data: { message: "Invalid credentials" },
      };
      axiosError.config = {
        headers: new AxiosHeaders(),
        skipAuthRefresh: true,
      } as InternalAxiosRequestConfig;

      await expect(rejected(axiosError)).rejects.toMatchObject({
        status: 401,
      });

      expect(refreshSession).not.toHaveBeenCalled();
      expect(onUnauthorized).toHaveBeenCalledTimes(1);
    });
  });
});
