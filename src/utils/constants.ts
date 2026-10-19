/** Application-wide constants and runtime configuration. */

export const COMPANY_NAME = "Velorix Technologies";
export const APP_NAME = "Velorix Sentinel";
export const APP_TAGLINE = "AI-powered deep packet inspection & network threat detection";
export const APP_DESCRIPTION =
  "Velorix Sentinel inspects every packet, detects network threats in real time and turns raw traffic into analyst-ready intelligence.";

/**
 * API configuration lives in a single module (`@/utils/apiConfig`) that resolves
 * and validates `VITE_API_BASE_URL`. Re-exported here for convenience.
 */
export { API_BASE_URL, API_TIMEOUT_MS, apiConfig } from "./apiConfig";

export const ROUTES = {
  home: "/",
  login: "/login",
  register: "/register",
  forgotPassword: "/forgot-password",
  resetPassword: "/reset-password",
  verifyEmail: "/verify-email",
  sessionExpired: "/session-expired",
  unauthorized: "/unauthorized",
  dashboard: "/dashboard",
  upload: "/upload",
  analysis: "/analysis",
  threats: "/threats",
  assistant: "/assistant",
  reports: "/reports",
  history: "/history",
  notifications: "/notifications",
  settings: "/settings",
  profile: "/profile",
  admin: "/admin",
} as const;

export type AppRoute = (typeof ROUTES)[keyof typeof ROUTES];
