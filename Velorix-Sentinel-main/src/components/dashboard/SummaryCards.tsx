import { Activity, FileStack, Flame, Layers, ShieldAlert, ShieldCheck } from "lucide-react";

import { DashboardCard } from "./DashboardCard";
import { ErrorState } from "@/components/common/ErrorState";
import { useDashboardSummary } from "@/hooks/useDashboard";
import { formatNumber } from "@/utils/format";

/** KPI row. Every value is served by `/dashboard/summary`; zeros are real zeros. */
export function SummaryCards() {
  const { data, isLoading, isError, error, refetch } = useDashboardSummary();

  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} />;
  }

  const value = (input: number | undefined) => (input === undefined ? "—" : formatNumber(input));

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <DashboardCard
        label="Security score"
        value={data ? `${data.securityScore}` : "—"}
        hint="100 = no residual risk across completed inspections"
        icon={ShieldCheck}
        tone={data && data.securityScore < 60 ? "danger" : "success"}
        isLoading={isLoading}
      />
      <DashboardCard
        label="Total analyses"
        value={value(data?.totalAnalyses)}
        hint={data ? `${formatNumber(data.completedAnalyses)} completed` : undefined}
        icon={Layers}
        isLoading={isLoading}
      />
      <DashboardCard
        label="Threats detected"
        value={value(data?.threatsDetected)}
        hint={data ? `${formatNumber(data.highThreats)} high severity` : undefined}
        icon={ShieldAlert}
        tone="warning"
        isLoading={isLoading}
      />
      <DashboardCard
        label="Critical threats"
        value={value(data?.criticalThreats)}
        hint="Findings requiring immediate triage"
        icon={Flame}
        tone="danger"
        isLoading={isLoading}
      />
      <DashboardCard
        label="Files uploaded"
        value={value(data?.filesUploaded)}
        hint={data ? `${formatNumber(data.packetsInspected)} packets inspected` : undefined}
        icon={FileStack}
        isLoading={isLoading}
      />
      <DashboardCard
        label="Active investigations"
        value={value(data?.activeInvestigations)}
        hint={data ? `${formatNumber(data.runningAnalyses)} inspections running` : undefined}
        icon={Activity}
        tone="warning"
        isLoading={isLoading}
      />
    </div>
  );
}
