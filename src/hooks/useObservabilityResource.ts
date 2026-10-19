import { useEffect, useState } from "react";

import { OBSERVABILITY_BACKEND_READY } from "@/services/observabilityService";
import { toApiError } from "@/services/apiClient";

export type ObservabilityResourceState<T> =
  | { status: "awaiting"; data: null; error: null }
  | { status: "loading"; data: null; error: null }
  | { status: "ready"; data: T; error: null }
  | { status: "error"; data: null; error: string };

/**
 * Fetches an observability resource.
 *
 * While `OBSERVABILITY_BACKEND_READY` is false the hook resolves to `awaiting`,
 * so every monitoring surface renders "Awaiting Live Platform Monitoring"
 * rather than invented telemetry.
 */
export function useObservabilityResource<T>(
  fetcher: (signal?: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): ObservabilityResourceState<T> {
  const [state, setState] = useState<ObservabilityResourceState<T>>(
    OBSERVABILITY_BACKEND_READY
      ? { status: "loading", data: null, error: null }
      : { status: "awaiting", data: null, error: null },
  );

  useEffect(() => {
    if (!OBSERVABILITY_BACKEND_READY) {
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
