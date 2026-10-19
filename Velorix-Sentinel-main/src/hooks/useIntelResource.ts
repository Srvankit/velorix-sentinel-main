import { useEffect, useState } from "react";

import { INTEL_BACKEND_READY } from "@/services/threatIntelService";
import { toApiError } from "@/services/apiClient";

export type IntelState<T> =
  | { status: "awaiting"; data: null; error: null }
  | { status: "loading"; data: null; error: null }
  | { status: "ready"; data: T; error: null }
  | { status: "error"; data: null; error: string };

/**
 * Fetches a threat-intelligence resource.
 *
 * While `INTEL_BACKEND_READY` is false the hook short-circuits to an
 * `awaiting` state so screens can render an honest "Awaiting Live Threat
 * Intelligence" notice instead of placeholder data. When the backend ships,
 * flipping the flag turns every consumer live without UI changes.
 */
export function useIntelResource<T>(
  fetcher: (signal?: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): IntelState<T> {
  const [state, setState] = useState<IntelState<T>>(
    INTEL_BACKEND_READY
      ? { status: "loading", data: null, error: null }
      : { status: "awaiting", data: null, error: null },
  );

  useEffect(() => {
    if (!INTEL_BACKEND_READY) {
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
