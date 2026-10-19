import { useEffect, useState } from "react";

import { SOC_BACKEND_READY } from "@/services/socService";
import { toApiError } from "@/services/apiClient";

export type SocResourceState<T> =
  | { status: "awaiting"; data: null; error: null }
  | { status: "loading"; data: null; error: null }
  | { status: "ready"; data: T; error: null }
  | { status: "error"; data: null; error: string };

/**
 * Fetches a SOC workspace resource.
 *
 * While `SOC_BACKEND_READY` is false the hook resolves to `awaiting`, so every
 * panel renders an honest "no telemetry connected" state rather than sample
 * alerts.
 */
export function useSocResource<T>(
  fetcher: (signal?: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): SocResourceState<T> {
  const [state, setState] = useState<SocResourceState<T>>(
    SOC_BACKEND_READY
      ? { status: "loading", data: null, error: null }
      : { status: "awaiting", data: null, error: null },
  );

  useEffect(() => {
    if (!SOC_BACKEND_READY) {
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
