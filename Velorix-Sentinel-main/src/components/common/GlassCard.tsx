import { motion } from "framer-motion";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface GlassCardProps {
  children: ReactNode;
  className?: string;
  /** Adds an entrance animation; disable inside virtualised lists. */
  animate?: boolean;
  delay?: number;
}

/** Frosted surface used for dashboard widgets, auth cards and modals. */
export function GlassCard({ children, className, animate = true, delay = 0 }: GlassCardProps) {
  if (!animate) {
    return <div className={cn("glass-panel p-6", className)}>{children}</div>;
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1], delay }}
      className={cn("glass-panel p-6", className)}
    >
      {children}
    </motion.div>
  );
}
