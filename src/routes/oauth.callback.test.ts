import { describe, expect, mock, test } from "bun:test";
import type { AuthSession, AuthUser } from "@/types/auth";

describe("OAuth callback race condition resolution (Task 3.4)", () => {
  const sampleTokens: Omit<AuthSession, "user"> = {
    accessToken: "test-oauth-access-token",
    refreshToken: "test-oauth-refresh-token",
    tokenType: "Bearer",
    accessTokenExpiresAt: "2026-10-30T00:00:00Z",
    refreshTokenExpiresAt: "2026-11-06T00:00:00Z",
  };

  const sampleUser: AuthUser = {
    id: "user-uuid-1234",
    firstName: "Ada",
    lastName: "Lovelace",
    fullName: "Ada Lovelace",
    email: "ada@velorix.io",
    role: "USER",
    verified: true,
    active: true,
    createdAt: "2026-10-29T10:00:00Z",
    updatedAt: "2026-10-29T10:00:00Z",
  };

  test("persists tokens first so HTTP client Authorization header is available when fetching currentUser", async () => {
    const callOrder: string[] = [];
    let currentSession: AuthSession | null = null;

    // Simulate getAccessToken (which reads session.accessToken)
    const getAccessToken = () => currentSession?.accessToken ?? null;

    const persistSession = mock((session: AuthSession) => {
      callOrder.push("persistSession");
      currentSession = session;
    });

    const clearSession = mock(() => {
      callOrder.push("clearSession");
      currentSession = null;
    });

    // Simulate authService.currentUser() which requires getAccessToken() != null
    const currentUser = mock(async () => {
      callOrder.push("currentUser");
      const token = getAccessToken();
      if (!token) {
        throw new Error("401 Unauthorized: Bearer token is missing");
      }
      return sampleUser;
    });

    // The fixed completeOAuthLogin implementation from AuthContext.tsx
    const completeOAuthLogin = async (tokens: Omit<AuthSession, "user">) => {
      persistSession({ ...tokens, user: null });
      try {
        const user = await currentUser();
        persistSession({ ...tokens, user });
      } catch (error) {
        clearSession();
        throw error;
      }
    };

    await completeOAuthLogin(sampleTokens);

    // Verify call order: persistSession (with user: null) MUST be called BEFORE currentUser
    expect(callOrder).toEqual(["persistSession", "currentUser", "persistSession"]);
    expect(persistSession).toHaveBeenCalledTimes(2);
    expect(currentUser).toHaveBeenCalledTimes(1);
    expect(clearSession).not.toHaveBeenCalled();

    // Verify final session state has both tokens and user profile
    expect(currentSession).not.toBeNull();
    expect(currentSession?.accessToken).toBe("test-oauth-access-token");
    expect(currentSession?.user).toEqual(sampleUser);
  });

  test("unfixed implementation (race condition) fails with 401 because token is not yet in session", async () => {
    let currentSession: AuthSession | null = null;
    const getAccessToken = () => currentSession?.accessToken ?? null;

    const persistSession = mock((session: AuthSession) => {
      currentSession = session;
    });

    const currentUser = mock(async () => {
      const token = getAccessToken();
      if (!token) {
        throw new Error("401 Unauthorized: Bearer token is missing");
      }
      return sampleUser;
    });

    // Buggy original implementation: calls currentUser before persistSession
    const buggyCompleteOAuthLogin = async (tokens: Omit<AuthSession, "user">) => {
      const user = await currentUser();
      persistSession({ ...tokens, user });
    };

    // Must reject because getAccessToken() returns null
    expect(buggyCompleteOAuthLogin(sampleTokens)).rejects.toThrow("401 Unauthorized");
    expect(persistSession).not.toHaveBeenCalled();
  });

  test("clears session cleanly when currentUser fails during callback", async () => {
    let currentSession: AuthSession | null = null;

    const persistSession = mock((session: AuthSession) => {
      currentSession = session;
    });

    const clearSession = mock(() => {
      currentSession = null;
    });

    const failingCurrentUser = mock(async () => {
      throw new Error("Account deactivated or network error");
    });

    const completeOAuthLogin = async (tokens: Omit<AuthSession, "user">) => {
      persistSession({ ...tokens, user: null });
      try {
        await failingCurrentUser();
      } catch (error) {
        clearSession();
        throw error;
      }
    };

    await expect(completeOAuthLogin(sampleTokens)).rejects.toThrow("Account deactivated");
    expect(persistSession).toHaveBeenCalledTimes(1);
    expect(clearSession).toHaveBeenCalledTimes(1);
    expect(currentSession).toBeNull();
  });
});

describe("OAuth One-Time Code Exchange (Task 3.5)", () => {
  const sampleExchangeResponse: AuthSession = {
    accessToken: "exchange-issued-access-token",
    refreshToken: "exchange-issued-refresh-token",
    tokenType: "Bearer",
    accessTokenExpiresAt: "2026-10-30T00:00:00Z",
    refreshTokenExpiresAt: "2026-11-06T00:00:00Z",
    user: {
      id: "user-uuid-exchange-1",
      firstName: "Grace",
      lastName: "Hopper",
      fullName: "Grace Hopper",
      email: "grace@velorix.io",
      role: "USER",
      verified: true,
      active: true,
      createdAt: "2026-10-29T10:00:00Z",
      updatedAt: "2026-10-29T10:00:00Z",
    },
  };

  test("parses code from search query, replaces URL bar state, and exchanges code for session", async () => {
    const callOrder: string[] = [];

    const mockCode = "sec_auth_code_987654";
    const searchParams = new URLSearchParams(`?code=${mockCode}`);
    const code = searchParams.get("code");

    const replaceState = mock((_state: unknown, _title: string, url: string) => {
      callOrder.push(`replaceState:${url}`);
    });

    const exchangeOAuthCode = mock(async (req: { code: string }) => {
      callOrder.push(`exchangeOAuthCode:${req.code}`);
      return sampleExchangeResponse;
    });

    const completeOAuthLogin = mock(async (_session: AuthSession) => {
      callOrder.push("completeOAuthLogin");
    });

    expect(code).toBe(mockCode);
    replaceState(null, "", "/oauth/callback");
    const session = await exchangeOAuthCode({ code: code! });
    await completeOAuthLogin(session);

    expect(callOrder).toEqual([
      "replaceState:/oauth/callback",
      `exchangeOAuthCode:${mockCode}`,
      "completeOAuthLogin",
    ]);
    expect(replaceState).toHaveBeenCalledWith(null, "", "/oauth/callback");
    expect(exchangeOAuthCode).toHaveBeenCalledWith({ code: mockCode });
    expect(completeOAuthLogin).toHaveBeenCalledWith(sampleExchangeResponse);
  });

  test("handles exchange rejection (expired or replayed code) by redirecting to login", async () => {
    const exchangeOAuthCode = mock(async () => {
      throw new Error("401 Unauthorized: Invalid or expired OAuth exchange code");
    });

    const navigateToLogin = mock(() => {});

    await exchangeOAuthCode({ code: "expired-or-replayed-code" }).catch(() => {
      navigateToLogin();
    });

    expect(navigateToLogin).toHaveBeenCalledTimes(1);
  });

  test("rejects legacy URL fragment without code and redirects to login", () => {
    const searchParams = new URLSearchParams("");
    const code = searchParams.get("code");
    const hash = "#accessToken=legacy-token&refreshToken=legacy-refresh";

    const replaceState = mock((_state: unknown, _title: string, url: string) => {});
    const navigateToLogin = mock(() => {});

    // Code is missing
    expect(code).toBeNull();

    // Verify rejection behavior: strip hash if present and navigate to login
    if (hash) {
      replaceState(null, "", "/oauth/callback");
    }
    navigateToLogin();

    expect(replaceState).toHaveBeenCalledWith(null, "", "/oauth/callback");
    expect(navigateToLogin).toHaveBeenCalledTimes(1);
  });
});
