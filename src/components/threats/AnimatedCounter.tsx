import { animate, useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState } from "react";

import { formatNumber } from "@/utils/format";

interface AnimatedCounterProps {
  value: number;
  /** Rendered instead of the number when the backend has no value yet. */
  fallback?: string;
  suffix?: string;
  className?: string;
}

/** Counts up to a numeric metric, honouring reduced-motion preferences. */
export function AnimatedCounter({ value, fallback, suffix, className }: AnimatedCounterProps) {
  const reduceMotion = useReducedMotion();
  const [display, setDisplay] = useState(reduceMotion ? value : 0);
  const previous = useRef(0);

  useEffect(() => {
    if (!Number.isFinite(value)) return;
    if (reduceMotion) {
      setDisplay(value);
      previous.current = value;
      return;
    }
    const controls = animate(previous.current, value, {
      duration: 0.9,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (latest) => setDisplay(Math.round(latest)),
    });
    previous.current = value;
    return () => controls.stop();
  }, [value, reduceMotion]);

  if (fallback) {
    return <span className={className}>{fallback}</span>;
  }

  return (
    <span className={className}>
      {formatNumber(Number.isFinite(display) ? display : 0)}
      {suffix}
    </span>
  );
}
