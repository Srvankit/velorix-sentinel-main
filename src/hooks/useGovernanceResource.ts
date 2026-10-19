import { useEffect, useState } from "react";

import { GOVERNANCE_BACKEND_READY } from "@/services/governanceService";
import { toApiError } from "@/services/apiClient";

export type GovernanceResourceState<T> =
  | { status: "awaiting"; data: null; error: null }
  | { status: "loading"; data: null; error: null }
  | { status: "ready"; data: T; error: null }
  | { status: "error"; data: null; error: string };

/**
 * Fetches an executive/compliance resource.
 *
 * While `GOVERNANCE_BACKEND_READY` is false the hook resolves to `awaiting`, so
 * every KPI, chart and framework panel shows "Awaiting Live Security Data"
 * instead of an invented score.
 */
export function useGovernanceResource<T>(
  fetcher: (signal?: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): GovernanceResourceState<T> {
  const [state, setState] = useState<GovernanceResourceState<T>>(
    GOVERNANCE_BACKEND_READY
      ? { status: "loading", data: null, error: null }
      : { status: "awaiting", data: null, error: null },
  );

  useEffect(() => {
    if (!GOVERNANCE_BACKEND_READY) {
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
