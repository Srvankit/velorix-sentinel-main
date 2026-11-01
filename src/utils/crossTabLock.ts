/**
 * Cross-tab lock coordinator using the browser Web Locks API.
 *
 * Guarantees that sensitive single-flight operations (such as OAuth/JWT refresh token rotation)
 * are coordinated across all browser tabs without race conditions or accidental token reuse.
 *
 * If the Web Locks API is unavailable, fails closed by throwing CrossTabLockUnavailableError
 * to prevent uncoordinated concurrent requests from triggering backend token family revocation.
 */

export interface CrossTabLockOptions {
  timeoutMs?: number;
}

export class CrossTabLockError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "CrossTabLockError";
  }
}

export class CrossTabLockUnavailableError extends CrossTabLockError {
  constructor(
    message = "Cross-tab lock unavailable: Web Locks API is required to guarantee safe token rotation."
  ) {
    super(message);
    this.name = "CrossTabLockUnavailableError";
  }
}

export class CrossTabLockTimeoutError extends CrossTabLockError {
  constructor(message: string) {
    super(message);
    this.name = "CrossTabLockTimeoutError";
  }
}

const DEFAULT_TIMEOUT_MS = 10_000;

export const isWebLocksSupported = (): boolean =>
  typeof navigator !== "undefined" &&
  Boolean(navigator.locks) &&
  typeof navigator.locks.request === "function";

/**
 * Executes an async task while holding an exclusive cross-tab Web Lock.
 *
 * - Native Web Locks API: kernel-managed mutual exclusion across tabs, held across
 *   asynchronous network boundaries without time-based lease preemption.
 * - Auto-cleanup: cleanly released if the tab or process crashes or unloads.
 * - Fail-closed: throws CrossTabLockUnavailableError if Web Locks API is unsupported.
 */
export async function withCrossTabLock<T>(
  lockName: string,
  task: () => Promise<T>,
  options: CrossTabLockOptions = {}
): Promise<T> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  if (!isWebLocksSupported()) {
    throw new CrossTabLockUnavailableError();
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await navigator.locks.request(
      lockName,
      { signal: controller.signal },
      async () => {
        return await task();
      }
    );
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new CrossTabLockTimeoutError(
        `Failed to acquire cross-tab lock '${lockName}' within ${timeoutMs}ms.`
      );
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
