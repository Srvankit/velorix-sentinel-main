/**
 * SSR-safe browser storage helpers.
 * Every access is guarded so modules can be imported during server rendering.
 */

const isBrowser = (): boolean => typeof window !== "undefined";

export const storage = {
  get<T>(key: string): T | null {
    if (!isBrowser()) return null;
    try {
      const raw = window.localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  },

  set<T>(key: string, value: T): void {
    if (!isBrowser()) return;
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* quota or private-mode failures are non-fatal */
    }
  },

  remove(key: string): void {
    if (!isBrowser()) return;
    window.localStorage.removeItem(key);
  },
};

export const STORAGE_KEYS = {
  session: "sentinelai.session",
} as const;
