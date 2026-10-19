import { cn } from "@/lib/utils";

const TONE: Record<string, string> = {
  Critical: "border-primary/50 bg-primary/15 text-primary",
  High: "border-orange-500/40 bg-orange-500/10 text-orange-400",
  Medium: "border-amber-500/40 bg-amber-500/10 text-amber-400",
  Low: "border-sky-500/40 bg-sky-500/10 text-sky-400",
  Informational: "border-border bg-muted/40 text-muted-foreground",
};

interface IntelSeverityProps {
  severity: string;
  className?: string;
}

/** Compact severity chip used across IOC, CVE and feed surfaces. */
export function IntelSeverity({ severity, className }: IntelSeverityProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium",
        TONE[severity] ?? TONE.Informational,
        className,
      )}
    >
      {severity}
    </span>
  );
}
