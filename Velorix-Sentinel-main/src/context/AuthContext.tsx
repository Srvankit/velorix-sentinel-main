import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

import { AuthContext, type AuthContextValue } from "@/context/auth-context";
import { authService, configureApiClient } from "@/services";
import type { AuthSession, LoginRequest, RegisterRequest, UserRole } from "@/types/auth";
import { STORAGE_KEYS, storage } from "@/utils/storage";

/**
 * JWT session provider.
 *
 * Owns the single source of truth for the authenticated session, persists it,
 * and wires the shared HTTP client so every request carries the bearer token
 * and expired access tokens are rotated transparently.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const sessionRef = useRef<AuthSession | null>(null);
  const restoredSessionRef = useRef<string | null>(null);

  sessionRef.current = session;

  const clearSession = useCallback(() => {
    setSession(null);
    sessionRef.current = null;
    storage.remove(STORAGE_KEYS.session);
  }, []);

  const persistSession = useCallback((next: AuthSession) => {
    setSession(next);
    sessionRef.current = next;
    setSessionExpired(false);
    storage.set(STORAGE_KEYS.session, next);
  }, []);

  // Restore the persisted session after hydration (localStorage is client-only).
  useEffect(() => {
    const restored = storage.get<AuthSession>(STORAGE_KEYS.session);
    sessionRef.current = restored;
    setSession(restored);
    restoredSessionRef.current = restored?.refreshToken ?? null;
    setIsInitializing(false);
  }, []);

  // Do not treat stale local storage as an authenticated session. This also
  // catches accounts revoked while the browser was offline.
  useEffect(() => {
    if (isInitializing || !session || restoredSessionRef.current !== session.refreshToken) return;
    restoredSessionRef.current = null;
    void authService.currentUser()
      .then((user) => persistSession({ ...session, user }))
      .catch(() => {
        clearSession();
        setSessionExpired(true);
      });
  }, [isInitializing, session, persistSession, clearSession]);

  // Wire the HTTP client once; it reads the live session through the ref.
  useEffect(() => {
    configureApiClient({
      getAccessToken: () => sessionRef.current?.accessToken ?? null,
      onUnauthorized: () => {
        if (sessionRef.current) setSessionExpired(true);
        clearSession();
      },
      refreshSession: async () => {
        const current = sessionRef.current;
        if (!current?.refreshToken) return null;
        try {
          const next = await authService.refresh({ refreshToken: current.refreshToken });
          persistSession(next);
          return next.accessToken;
        } catch {
          // Auto logout: the refresh token is expired or revoked.
          return null;
        }
      },
    });
  }, [clearSession, persistSession]);

  const login = useCallback(
    async (payload: LoginRequest) => {
      const next = await authService.login(payload);
      persistSession(next);
      return next.user;
    },
    [persistSession],
  );

  const register = useCallback(
    async (payload: RegisterRequest) => {
      await authService.register(payload);
      const next = await authService.login({ email: payload.email, password: payload.password });
      persistSession(next);
      return next.user;
    },
    [persistSession],
  );

  const logout = useCallback(async () => {
    const current = sessionRef.current;
    try {
      if (current?.refreshToken) {
        await authService.logout({ refreshToken: current.refreshToken });
      }
    } catch {
      /* the local session is cleared regardless of the network result */
    } finally {
      clearSession();
      setSessionExpired(false);
    }
  }, [clearSession]);

  const refreshSession = useCallback(async () => {
    const current = sessionRef.current;
    if (!current?.refreshToken) return;
    try {
      persistSession(await authService.refresh({ refreshToken: current.refreshToken }));
    } catch (error) {
      setSessionExpired(true);
      clearSession();
      throw error;
    }
  }, [persistSession, clearSession]);

  const refreshUser = useCallback(async () => {
    const current = sessionRef.current;
    if (!current) return;
    const user = await authService.currentUser();
    persistSession({ ...current, user });
  }, [persistSession]);

  const completeOAuthLogin = useCallback(
    async (tokens: Omit<AuthSession, "user">) => {
      const user = await authService.currentUser();
      persistSession({ ...tokens, user });
    },
    [persistSession],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      accessToken: session?.accessToken ?? null,
      isAuthenticated: Boolean(session?.accessToken),
      isInitializing,
      sessionExpired,
      login,
      register,
      logout,
      refreshSession,
      refreshUser,
      completeOAuthLogin,
      acknowledgeSessionExpiry: () => setSessionExpired(false),
      hasRole: (...roles: UserRole[]) =>
        Boolean(session?.user && roles.includes(session.user.role)),
    }),
    [
      session,
      isInitializing,
      sessionExpired,
      login,
      register,
      logout,
      refreshSession,
      refreshUser,
      completeOAuthLogin,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
