import { api } from "./apiClient";
import type {
  AuthSession,
  AuthUser,
  ChangePasswordRequest,
  DeleteAccountRequest,
  ForgotPasswordRequest,
  LoginRequest,
  OAuthExchangeRequest,
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
  oauthExchange: "/auth/oauth/exchange",
  forgotPassword: "/auth/forgot-password",
  resetPassword: "/auth/reset-password",
  verifyEmail: "/auth/verify-email",
  resendVerification: "/auth/resend-verification",
  refresh: "/auth/refresh",
  logout: "/auth/logout",
  me: "/auth/me",
  changePassword: "/auth/change-password",
  deleteAccount: "/auth/account",
} as const;

export const authService = {
  /** POST /api/auth/register — submits account registration and dispatches a verification email. */
  register: (payload: RegisterRequest) =>
    api.post<void, RegisterRequest>(AUTH_ENDPOINTS.register, payload),

  /** POST /api/auth/login */
  login: (payload: LoginRequest) =>
    api.post<AuthSession, LoginRequest>(AUTH_ENDPOINTS.login, payload),

  /** POST /api/auth/oauth/exchange — exchanges a single-use OAuth authorization code for session tokens. */
  exchangeOAuthCode: (payload: OAuthExchangeRequest) =>
    api.post<AuthSession, OAuthExchangeRequest>(AUTH_ENDPOINTS.oauthExchange, payload, {
      skipAuthRefresh: true,
    }),

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

  /** POST /api/auth/change-password */
  changePassword: (payload: ChangePasswordRequest) =>
    api.post<void, ChangePasswordRequest>(AUTH_ENDPOINTS.changePassword, payload),

  /** DELETE /api/auth/account */
  deleteAccount: (payload?: DeleteAccountRequest) =>
    api.delete<void>(AUTH_ENDPOINTS.deleteAccount, payload ? { data: payload } : undefined),
};
