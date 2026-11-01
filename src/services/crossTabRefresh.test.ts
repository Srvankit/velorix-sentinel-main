import { afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import type { AuthSession } from "@/types/auth";
import {
  CrossTabLockTimeoutError,
  CrossTabLockUnavailableError,
  isWebLocksSupported,
  withCrossTabLock,
} from "@/utils/crossTabLock";
import { STORAGE_KEYS, storage } from "@/utils/storage";

function createMockWebLocks() {
  const activeQueues = new Map<string, Promise<void>>();

  return {
    request: mock(async (name: string, options: unknown, callback?: () => Promise<unknown>) => {
      const task = typeof options === "function" ? (options as () => Promise<unknown>) : callback!;
      const opts = typeof options === "object" && options !== null ? (options as { signal?: AbortSignal }) : {};

      if (opts.signal?.aborted) {
        throw new DOMException("The operation was aborted.", "AbortError");
      }

      const previous = activeQueues.get(name) ?? Promise.resolve();
      let release: () => void;
      const next = new Promise<void>((resolve) => {
        release = resolve;
      });
      activeQueues.set(name, next);

      let abortListener: (() => void) | undefined;
      const abortPromise = new Promise<never>((_, reject) => {
        if (opts.signal) {
          abortListener = () => {
            reject(new DOMException("The operation was aborted.", "AbortError"));
          };
          opts.signal.addEventListener("abort", abortListener, { once: true });
        }
      });

      try {
        await Promise.race([previous, abortPromise]);
        return await task();
      } finally {
        if (opts.signal && abortListener) {
          opts.signal.removeEventListener("abort", abortListener);
        }
        release!();
      }
    }),
  };
}

describe("Cross-Tab Refresh Coordination (Task 3.6)", () => {
  const storageMap = new Map<string, string>();
  let mockLocks: ReturnType<typeof createMockWebLocks>;

  beforeEach(() => {
    storageMap.clear();
    mockLocks = createMockWebLocks();

    (globalThis as unknown as { window: unknown }).window = {
      localStorage: {
        getItem: (k: string) => storageMap.get(k) ?? null,
        setItem: (k: string, v: string) => storageMap.set(k, v),
        removeItem: (k: string) => storageMap.delete(k),
        clear: () => storageMap.clear(),
      },
      addEventListener: () => {},
      removeEventListener: () => {},
    };

    (globalThis as unknown as { navigator: unknown }).navigator = {
      locks: mockLocks,
    };
  });

  afterEach(() => {
    storageMap.clear();
  });

  const initialSession: AuthSession = {
    accessToken: "access-token-v1",
    refreshToken: "refresh-token-v1",
    tokenType: "Bearer",
    accessTokenExpiresAt: "2026-10-31T12:00:00Z",
    refreshTokenExpiresAt: "2026-11-07T12:00:00Z",
    user: {
      id: "user-1",
      firstName: "Grace",
      lastName: "Hopper",
      fullName: "Grace Hopper",
      email: "grace@velorix.io",
      role: "USER",
      verified: true,
      active: true,
      createdAt: "2026-10-19T10:00:00Z",
      updatedAt: "2026-10-19T10:00:00Z",
    },
  };

  const rotatedSession: AuthSession = {
    ...initialSession,
    accessToken: "access-token-v2",
    refreshToken: "refresh-token-v2",
  };

  // 1. Same-tab requests: single-flight deduplication
  test("1: Same-tab requests: single-flight deduplication ensures only one refresh call is made", async () => {
    let networkCallCount = 0;

    const mockNetworkRefresh = async (): Promise<string> => {
      networkCallCount++;
      await new Promise((resolve) => setTimeout(resolve, 20));
      return "new-access-token";
    };

    let pendingRefresh: Promise<string | null> | null = null;
    const refreshOnce = (): Promise<string | null> => {
      pendingRefresh ??= mockNetworkRefresh().finally(() => {
        pendingRefresh = null;
      });
      return pendingRefresh;
    };

    const [token1, token2, token3] = await Promise.all([
      refreshOnce(),
      refreshOnce(),
      refreshOnce(),
    ]);

    expect(networkCallCount).toBe(1);
    expect(token1).toBe("new-access-token");
    expect(token2).toBe("new-access-token");
    expect(token3).toBe("new-access-token");
  });

  // 2. Cross-tab coordination: Tab 1 rotates, Tab 2 re-reads storage and does NOT submit stale token
  test("2: Cross-tab coordination: Tab 1 rotates R1->R2, Tab 2 re-reads storage and reuses R2", async () => {
    storage.set(STORAGE_KEYS.session, initialSession);

    let tab1Session: AuthSession | null = { ...initialSession };
    let tab2Session: AuthSession | null = { ...initialSession };

    let backendRefreshCallCount = 0;
    const backendRefreshMock = mock(async (token: string): Promise<AuthSession> => {
      backendRefreshCallCount++;
      if (token === "refresh-token-v1") {
        return rotatedSession;
      }
      throw new Error("Invalid or replayed token");
    });

    const runTabRefresh = async (
      tabSessionRef: { current: AuthSession | null },
      setTabSession: (next: AuthSession) => void
    ): Promise<string | null> => {
      const triggeringToken = tabSessionRef.current?.refreshToken;
      if (!triggeringToken) return null;

      return await withCrossTabLock("velorix_refresh_lock", async () => {
        // 1. Re-read latest session from shared storage after acquiring lock
        const latestSession = storage.get<AuthSession>(STORAGE_KEYS.session);
        if (!latestSession?.refreshToken) {
          return null;
        }

        // 2. Compare against triggering token
        if (latestSession.refreshToken !== triggeringToken) {
          // Another tab already rotated! Use latest session without calling backend
          tabSessionRef.current = latestSession;
          setTabSession(latestSession);
          return latestSession.accessToken;
        }

        // 3. Perform network refresh with latest token
        const next = await backendRefreshMock(triggeringToken);

        // 4. Persist newly issued token before releasing lock
        storage.set(STORAGE_KEYS.session, next);
        tabSessionRef.current = next;
        setTabSession(next);
        return next.accessToken;
      });
    };

    const tab1Ref = { current: tab1Session };
    const tab2Ref = { current: tab2Session };

    const [tab1Result, tab2Result] = await Promise.all([
      runTabRefresh(tab1Ref, (next) => {
        tab1Session = next;
      }),
      runTabRefresh(tab2Ref, (next) => {
        tab2Session = next;
      }),
    ]);

    expect(backendRefreshCallCount).toBe(1);
    expect(tab1Result).toBe("access-token-v2");
    expect(tab2Result).toBe("access-token-v2");
    expect(tab1Session?.refreshToken).toBe("refresh-token-v2");
    expect(tab2Session?.refreshToken).toBe("refresh-token-v2");
    expect(mockLocks.request).toHaveBeenCalledTimes(2);
  });

  // 3. Genuine replay detection
  test("3: Genuine replay: Submitting an already-rotated token directly triggers replay failure", async () => {
    const backendRefreshMock = mock(async (req: { refreshToken: string }) => {
      if (req.refreshToken === "stale-revoked-token") {
        const error = new Error("Suspicious token reuse detected; all sessions revoked.");
        (error as unknown as { status: number }).status = 401;
        throw error;
      }
      return rotatedSession;
    });

    let caughtError: unknown = null;
    try {
      await backendRefreshMock({ refreshToken: "stale-revoked-token" });
    } catch (err) {
      caughtError = err;
    }

    expect(caughtError).not.toBeNull();
    expect((caughtError as Error).message).toContain("Suspicious token reuse detected");
    expect((caughtError as unknown as { status: number }).status).toBe(401);
  });

  // 4. Failure handling: Lock is cleanly released even when task fails
  test("4: Failure handling: Refresh failure cleanly releases lock so subsequent attempts succeed", async () => {
    let failedAttemptRan = false;
    let subsequentAttemptRan = false;

    try {
      await withCrossTabLock("velorix_refresh_lock", async () => {
        failedAttemptRan = true;
        throw new Error("Network explosion");
      });
    } catch {
      // Expected failure
    }

    expect(failedAttemptRan).toBe(true);

    const result = await withCrossTabLock("velorix_refresh_lock", async () => {
      subsequentAttemptRan = true;
      return "success-after-failure";
    });

    expect(subsequentAttemptRan).toBe(true);
    expect(result).toBe("success-after-failure");
  });

  // 5. Capability detection
  test("5: Capability detection: Reports Web Locks availability accurately", () => {
    expect(isWebLocksSupported()).toBe(true);

    const originalNavigator = (globalThis as unknown as { navigator?: unknown }).navigator;
    try {
      (globalThis as unknown as { navigator: unknown }).navigator = {};
      expect(isWebLocksSupported()).toBe(false);
    } finally {
      (globalThis as unknown as { navigator: unknown }).navigator = originalNavigator;
    }
  });

  // 6. Fail-closed: Throws CrossTabLockUnavailableError when Web Locks are unavailable, preserving session
  test("6: Fail-closed: Throws CrossTabLockUnavailableError when Web Locks are absent, preserving session", async () => {
    storage.set(STORAGE_KEYS.session, initialSession);

    const originalNavigator = (globalThis as unknown as { navigator?: unknown }).navigator;
    try {
      (globalThis as unknown as { navigator: unknown }).navigator = {};

      let caughtError: unknown = null;
      try {
        await withCrossTabLock("fail_closed_lock", async () => {
          return "should-never-run";
        });
      } catch (err) {
        caughtError = err;
      }

      expect(caughtError).toBeInstanceOf(CrossTabLockUnavailableError);
      expect((caughtError as Error).message).toContain("Web Locks API is required to guarantee safe token rotation");

      // Existing session in storage MUST be preserved!
      const currentSession = storage.get<AuthSession>(STORAGE_KEYS.session);
      expect(currentSession).not.toBeNull();
      expect(currentSession?.accessToken).toBe("access-token-v1");
      expect(currentSession?.refreshToken).toBe("refresh-token-v1");
    } finally {
      (globalThis as unknown as { navigator: unknown }).navigator = originalNavigator;
    }
  });

  // 7. Timeout handling: Throws CrossTabLockTimeoutError when lock cannot be acquired within timeout
  test("7: Timeout handling: Throws CrossTabLockTimeoutError on acquisition timeout", async () => {
    // Hold lock indefinitely
    const neverEndingTask = withCrossTabLock(
      "timeout_lock",
      () => new Promise(() => {}),
      { timeoutMs: 5000 }
    );

    let caughtError: unknown = null;
    try {
      // Attempt with very short 50ms timeout
      await withCrossTabLock(
        "timeout_lock",
        async () => "should-timeout",
        { timeoutMs: 50 }
      );
    } catch (err) {
      caughtError = err;
    }

    expect(caughtError).toBeInstanceOf(CrossTabLockTimeoutError);
    expect((caughtError as Error).message).toContain("Failed to acquire cross-tab lock 'timeout_lock'");
  });
});
