/** Shared chart palette tokens (design-system driven, never hardcoded colours). */

export const CHART_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
];

export const SEVERITY_COLORS: Record<string, string> = {
  CRITICAL: "var(--critical)",
  HIGH: "var(--destructive)",
  MEDIUM: "var(--warning)",
  LOW: "var(--success)",
  INFO: "var(--info)",
};
