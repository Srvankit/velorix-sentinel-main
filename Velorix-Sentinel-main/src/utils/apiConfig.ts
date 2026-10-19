/**
 * Centralised API configuration.
 *
 * The frontend and backend are deployed independently, so the API origin is
 * taken from `VITE_API_BASE_URL` and never inferred from the page origin.
 *
 * This module resolves that value once, validates it, and reports a precise
 * problem so the UI can render a professional configuration message instead of
 * failing with an opaque network error.
 */

export type ApiConfigProblemKind = "missing" | "invalid-url" | "unreachable-localhost";

export interface ApiConfigProblem {
  kind: ApiConfigProblemKind;
  title: string;
  message: string;
}

export interface ApiConfig {
  /** Absolute (or same-origin) base URL used by the HTTP client. */
  baseUrl: string;
  /** True when the API is expected to be reachable from this browser. */
  isUsable: boolean;
  /** Set when the configuration cannot work in the current environment. */
  problem: ApiConfigProblem | null;
  /** True when the app is served from a remote host (e.g. a hosted preview). */
  isRemoteHost: boolean;
}

const LOCAL_HOSTNAMES = new Set(["localhost", "127.0.0.1", "0.0.0.0", "[::1]", "::1"]);

function isLocalHostname(hostname: string): boolean {
  return LOCAL_HOSTNAMES.has(hostname.toLowerCase());
}

/** True when the document is being served from something other than the local machine. */
function detectRemoteHost(): boolean {
  if (typeof window === "undefined") return false;
  return !isLocalHostname(window.location.hostname);
}

function parseAbsolute(url: string): URL | null {
  try {
    return new URL(url);
  } catch {
    return null;
  }
}

/** Deployed Velorix Sentinel API. Used when no environment override is provided. */
export const DEFAULT_API_BASE_URL = "https://velorix-sentinel-backend.onrender.com/api";

export function resolveApiConfig(rawValue: string | undefined, isRemoteHost: boolean): ApiConfig {
  const configured = rawValue?.trim();

  if (!configured) {
    // Use the Render origin in production. Netlify's reverse proxy does not
    // preserve browser preflight headers reliably for JSON authentication calls.
    return {
      baseUrl: DEFAULT_API_BASE_URL,
      isUsable: true,
      isRemoteHost,
      problem: null,
    };
  }

  // Relative values are only valid when a same-origin reverse proxy serves the API.
  if (configured.startsWith("/")) {
    return {
      baseUrl: configured.replace(/\/+$/, ""),
      isUsable: true,
      isRemoteHost,
      problem: null,
    };
  }

  const parsed = parseAbsolute(configured);
  if (!parsed || !/^https?:$/.test(parsed.protocol)) {
    return {
      baseUrl: DEFAULT_API_BASE_URL,
      isUsable: false,
      isRemoteHost,
      problem: {
        kind: "invalid-url",
        title: "API URL is invalid",
        message:
          `VITE_API_BASE_URL ("${configured}") is not a valid http(s) URL. ` +
          "Use an absolute URL such as https://your-api.onrender.com/api.",
      },
    };
  }

  const baseUrl = configured.replace(/\/+$/, "");

  // A remotely served frontend can never reach a backend on the viewer's machine.
  if (isRemoteHost && isLocalHostname(parsed.hostname)) {
    return {
      baseUrl,
      isUsable: false,
      isRemoteHost,
      problem: {
        kind: "unreachable-localhost",
        title: "This preview cannot reach your local backend",
        message:
          "The Velorix Sentinel frontend is running remotely, but the API is configured at " +
          `${parsed.origin}, which only exists on your own machine. Set VITE_API_BASE_URL to the ` +
          `deployed API (${DEFAULT_API_BASE_URL}) and redeploy the frontend.`,
      },
    };
  }

  return { baseUrl, isUsable: true, isRemoteHost, problem: null };
}

export const apiConfig: ApiConfig = resolveApiConfig(
  import.meta.env.VITE_API_BASE_URL as string | undefined,
  detectRemoteHost(),
);

export const API_BASE_URL = apiConfig.baseUrl;
/** Generous: the hosted API may be cold-starting on its first request. */
export const API_TIMEOUT_MS = 30_000;
