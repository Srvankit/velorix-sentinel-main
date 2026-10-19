import { motion, useReducedMotion } from "framer-motion";
import { useMemo } from "react";

import { cn } from "@/lib/utils";

interface ParticleFieldProps {
  count?: number;
  className?: string;
}

/** Decorative floating particles used behind hero and CTA bands. */
export function ParticleField({ count = 24, className }: ParticleFieldProps) {
  const reduceMotion = useReducedMotion();
  const particles = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        id: i,
        left: (i * 37) % 100,
        top: (i * 61) % 100,
        size: 2 + (i % 3),
        duration: 8 + (i % 7) * 1.5,
        delay: (i % 5) * 0.8,
      })),
    [count],
  );

  return (
    <div
      aria-hidden="true"
      className={cn("pointer-events-none absolute inset-0 overflow-hidden", className)}
    >
      {particles.map((p) => (
        <motion.span
          key={p.id}
          className="absolute rounded-full bg-primary/50"
          style={{
            left: `${p.left}%`,
            top: `${p.top}%`,
            width: p.size,
            height: p.size,
          }}
          animate={
            reduceMotion ? { opacity: 0.35 } : { y: [0, -28, 0], opacity: [0.15, 0.7, 0.15] }
          }
          transition={{
            duration: p.duration,
            delay: p.delay,
            repeat: reduceMotion ? 0 : Infinity,
            ease: "easeInOut",
          }}
        />
      ))}
    </div>
  );
}
