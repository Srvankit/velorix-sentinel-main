import { useCallback, useEffect, useRef, useState } from "react";

import { SOC_STREAM_READY } from "@/services/socService";
import type { StreamState } from "@/types/soc";

interface UseSocStreamResult<T> {
  /** Events received since the stream opened. Empty until the gateway ships. */
  events: T[];
  state: StreamState;
  /** True while the realtime gateway is unavailable by configuration. */
  disabled: boolean;
  clear: () => void;
}

/**
 * Subscription placeholder for a SOC telemetry channel.
 *
 * The realtime gateway does not exist yet, so this hook intentionally emits no
 * events — it never fabricates traffic. When `SOC_STREAM_READY` flips to true,
 * replace the body of the effect with the WebSocket/STOMP subscription for
 * `topic`; every consumer already reads `events` and `state`.
 */
export function useSocStream<T>(topic: string, limit = 100): UseSocStreamResult<T> {
  const [events, setEvents] = useState<T[]>([]);
  const [state, setState] = useState<StreamState>(SOC_STREAM_READY ? "CONNECTING" : "DISABLED");
  const limitRef = useRef(limit);
  limitRef.current = limit;

  useEffect(() => {
    if (!SOC_STREAM_READY) {
      setState("DISABLED");
      setEvents([]);
      return;
    }

    // Integration point: open the WebSocket for `topic`, push payloads through
    // `push`, and mirror the socket lifecycle onto `setState`.
    setState("CONNECTING");
    return () => setState("CLOSED");
  }, [topic]);

  const clear = useCallback(() => setEvents([]), []);

  return { events, state, disabled: !SOC_STREAM_READY, clear };
}
