import { api } from "./apiClient";
import type {
  AuthSession,
  AuthUser,
  ForgotPasswordRequest,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  ResendVerificationRequest,
  ResetPasswordRequest,
  VerifyEmailRequest,
} from "@/types/auth";

/**
 * Authentication endpoints exposed by the Spring Security / JWT backend.
 *
 * Paths are relative to `API_BASE_URL` (defaults to `/api`), so they resolve to
 * `/api/auth/*` exactly as the backend contract specifies.
 */
export const AUTH_ENDPOINTS = {
  register: "/auth/register",
  login: "/auth/login",
  forgotPassword: "/auth/forgot-password",
  resetPassword: "/auth/reset-password",
  verifyEmail: "/auth/verify-email",
  resendVerification: "/auth/resend-verification",
  refresh: "/auth/refresh",
  logout: "/auth/logout",
  me: "/auth/me",
} as const;

export const authService = {
  /** POST /api/auth/register — creates the account, returns the new profile. */
  register: (payload: RegisterRequest) =>
    api.post<AuthUser, RegisterRequest>(AUTH_ENDPOINTS.register, payload),

  /** POST /api/auth/login */
  login: (payload: LoginRequest) =>
    api.post<AuthSession, LoginRequest>(AUTH_ENDPOINTS.login, payload),

  /** POST /api/auth/forgot-password */
  forgotPassword: (payload: ForgotPasswordRequest) =>
    api.post<void, ForgotPasswordRequest>(AUTH_ENDPOINTS.forgotPassword, payload),

  /** POST /api/auth/reset-password */
  resetPassword: (payload: ResetPasswordRequest) =>
    api.post<void, ResetPasswordRequest>(AUTH_ENDPOINTS.resetPassword, payload),

  /** POST /api/auth/verify-email */
  verifyEmail: (payload: VerifyEmailRequest) =>
    api.post<AuthUser, VerifyEmailRequest>(AUTH_ENDPOINTS.verifyEmail, payload),

  /** POST /api/auth/resend-verification */
  resendVerification: (payload: ResendVerificationRequest) =>
    api.post<void, ResendVerificationRequest>(AUTH_ENDPOINTS.resendVerification, payload),

  /** POST /api/auth/refresh — rotates the token pair. */
  refresh: (payload: RefreshTokenRequest) =>
    api.post<AuthSession, RefreshTokenRequest>(AUTH_ENDPOINTS.refresh, payload, {
      skipAuthRefresh: true,
    }),

  /** POST /api/auth/logout — revokes the supplied refresh token. */
  logout: (payload: RefreshTokenRequest) =>
    api.post<void, RefreshTokenRequest>(AUTH_ENDPOINTS.logout, payload, {
      skipAuthRefresh: true,
    }),

  /** GET /api/auth/me */
  currentUser: () => api.get<AuthUser>(AUTH_ENDPOINTS.me),
};
