import { useEffect, useState } from "react";

import { INCIDENT_BACKEND_READY } from "@/services/incidentService";
import { toApiError } from "@/services/apiClient";

export type IncidentResourceState<T> =
  | { status: "awaiting"; data: null; error: null }
  | { status: "loading"; data: null; error: null }
  | { status: "ready"; data: T; error: null }
  | { status: "error"; data: null; error: string };

/**
 * Fetches an incident-response resource.
 *
 * While `INCIDENT_BACKEND_READY` is false the hook resolves to `awaiting`, so
 * screens render an honest empty state instead of fabricated incidents.
 */
export function useIncidentResource<T>(
  fetcher: (signal?: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): IncidentResourceState<T> {
  const [state, setState] = useState<IncidentResourceState<T>>(
    INCIDENT_BACKEND_READY
      ? { status: "loading", data: null, error: null }
      : { status: "awaiting", data: null, error: null },
  );

  useEffect(() => {
    if (!INCIDENT_BACKEND_READY) {
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
