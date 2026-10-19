import { createContext } from "react";

import type { AuthSession, AuthUser, LoginRequest, RegisterRequest, UserRole } from "@/types/auth";

export interface AuthContextValue {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  /** True until the persisted session has been restored on the client. */
  isInitializing: boolean;
  /** Set when the backend rejected the stored token and it could not be renewed. */
  sessionExpired: boolean;
  login: (payload: LoginRequest) => Promise<AuthUser>;
  /** Creates the account, then signs the new user in. */
  register: (payload: RegisterRequest) => Promise<AuthUser>;
  logout: () => Promise<void>;
  /** Exchange the refresh token for a fresh session. */
  refreshSession: () => Promise<void>;
  refreshUser: () => Promise<void>;
  completeOAuthLogin: (tokens: Omit<AuthSession, "user">) => Promise<void>;
  hasRole: (...roles: UserRole[]) => boolean;
  acknowledgeSessionExpiry: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
