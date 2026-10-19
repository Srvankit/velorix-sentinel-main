/** Risk bands mirror the backend `OverallStatus` thresholds. */
export function riskBand(score: number): { label: string; className: string } {
  if (score >= 80)
    return { label: "Critical", className: "border-critical/40 bg-critical/15 text-critical" };
  if (score >= 60)
    return { label: "High", className: "border-destructive/40 bg-destructive/15 text-destructive" };
  if (score >= 40)
    return { label: "Elevated", className: "border-warning/40 bg-warning/15 text-warning" };
  if (score >= 20) return { label: "Low", className: "border-info/40 bg-info/15 text-info" };
  return { label: "Secure", className: "border-success/40 bg-success/15 text-success" };
}
