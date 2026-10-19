import { useEffect, useState } from "react";

import { ADMIN_BACKEND_READY } from "@/services/adminService";
import { toApiError } from "@/services/apiClient";

export type AdminResourceState<T> =
  | { status: "awaiting"; data: null; error: null }
  | { status: "loading"; data: null; error: null }
  | { status: "ready"; data: T; error: null }
  | { status: "error"; data: null; error: string };

/**
 * Fetches an administration resource.
 *
 * While `ADMIN_BACKEND_READY` is false the hook resolves to `awaiting`, so every
 * admin surface renders "Awaiting Live Data" rather than invented tenants,
 * users, sessions or audit records.
 */
export function useAdminResource<T>(
  fetcher: (signal?: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): AdminResourceState<T> {
  const [state, setState] = useState<AdminResourceState<T>>(
    ADMIN_BACKEND_READY
      ? { status: "loading", data: null, error: null }
      : { status: "awaiting", data: null, error: null },
  );

  useEffect(() => {
    if (!ADMIN_BACKEND_READY) {
      setState({ status: "awaiting", data: null, error: null });
      return;
    }

    const controller = new AbortController();
    setState({ status: "loading", data: null, error: null });

    fetcher(controller.signal)
      .then((data) => setState({ status: "ready", data, error: null }))
      .catch((cause) => {
        if (controller.signal.aborted) return;
        setState({ status: "error", data: null, error: toApiError(cause).message });
      });

    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
