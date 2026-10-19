import { useEffect, useState } from "react";

/** True once React has hydrated — use to gate browser-only rendering. */
export function useHydrated(): boolean {
  const [hydrated, setHydrated] = useState(false);
  useEffect(() => setHydrated(true), []);
  return hydrated;
}
