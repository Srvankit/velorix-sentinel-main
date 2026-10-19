import { motion } from "framer-motion";

import { cn } from "@/lib/utils";
import type { PostureBand } from "@/types/governance";

interface PostureGaugeProps {
  score?: number | null;
  band?: PostureBand;
  label?: string;
  size?: number;
  className?: string;
}

const BAND_STROKE: Record<PostureBand, string> = {
  STRONG: "stroke-emerald-400",
  MODERATE: "stroke-amber-400",
  WEAK: "stroke-primary",
  UNKNOWN: "stroke-muted-foreground/40",
};

/**
 * Radial security posture gauge. With no score the arc stays empty and the
 * centre reads "Awaiting Data" rather than defaulting to zero.
 */
export function PostureGauge({
  score = null,
  band = "UNKNOWN",
  label = "Security Posture",
  size = 168,
  className,
}: PostureGaugeProps) {
  const stroke = 10;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const arc = circumference * 0.75;
  const hasScore = score !== null && score !== undefined;
  const progress = hasScore ? Math.min(Math.max(score, 0), 100) / 100 : 0;

  return (
    <div className={cn("flex flex-col items-center", className)}>
      <div className="relative" style={{ width: size, height: size }}>
        <svg
          width={size}
          height={size}
          viewBox={`0 0 ${size} ${size}`}
          className="-rotate-[225deg]"
          role="img"
          aria-label={hasScore ? `${label}: ${score} out of 100` : `${label}: awaiting live data`}
        >
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${arc} ${circumference}`}
            className="stroke-muted/40"
          />
          <motion.circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${arc} ${circumference}`}
            initial={{ strokeDashoffset: arc }}
            animate={{ strokeDashoffset: arc - arc * progress }}
            transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] }}
            className={BAND_STROKE[band]}
          />
        </svg>
        <div className="absolute inset-0 grid place-items-center text-center">
          {hasScore ? (
            <>
              <span className="font-mono text-4xl leading-none font-semibold">{score}</span>
              <span className="mt-9 absolute text-[10px] tracking-widest text-muted-foreground uppercase">
                / 100
              </span>
            </>
          ) : (
            <span className="max-w-[7rem] text-[11px] leading-snug font-medium text-muted-foreground">
              Awaiting Live Data
            </span>
          )}
        </div>
      </div>
      <p className="mt-1 text-xs tracking-wide text-muted-foreground uppercase">{label}</p>
    </div>
  );
}
