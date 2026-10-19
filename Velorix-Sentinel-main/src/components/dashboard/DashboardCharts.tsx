import { ArrowDownRight, ArrowUpRight, Gauge, Network, PieChart, TrendingUp } from "lucide-react";

import {
  CategoryBarChart,
  ChartCard,
  DistributionDonutChart,
  TimeSeriesAreaChart,
} from "@/components/charts";
import { useDashboardCharts } from "@/hooks/useDashboard";

/** Every dashboard visualisation, driven by a single `/dashboard/charts` read. */
export function DashboardCharts() {
  const { data, isLoading, isError, error, refetch } = useDashboardCharts();
  const failure = isError ? { message: error?.message ?? "Unable to load chart data." } : null;
  const retry = () => void refetch();

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <ChartCard
        title="Threat trend"
        description="Findings per day by severity"
        icon={TrendingUp}
        isLoading={isLoading}
        error={failure}
        onRetry={retry}
        isEmpty={!data?.threatTrend.length}
        className="xl:col-span-2"
      >
        <TimeSeriesAreaChart
          data={data?.threatTrend ?? []}
          xKey="bucketStart"
          series={[
            { key: "critical", label: "Critical", color: "var(--critical)" },
            { key: "high", label: "High", color: "var(--destructive)" },
            { key: "medium", label: "Medium", color: "var(--warning)" },
            { key: "low", label: "Low", color: "var(--success)" },
          ]}
        />
      </ChartCard>

      <ChartCard
        title="Protocol distribution"
        description="Share of inspected packets per protocol"
        icon={Network}
        isLoading={isLoading}
        error={failure}
        onRetry={retry}
        isEmpty={!data?.protocolDistribution.length}
      >
        <CategoryBarChart data={data?.protocolDistribution ?? []} />
      </ChartCard>

      <ChartCard
        title="Threat severity breakdown"
        description="Open findings grouped by severity"
        icon={PieChart}
        isLoading={isLoading}
        error={failure}
        onRetry={retry}
        isEmpty={!data?.severityBreakdown.length}
      >
        <DistributionDonutChart data={data?.severityBreakdown ?? []} />
      </ChartCard>

      <ChartCard
        title="Packet volume timeline"
        description="Packets inspected per day"
        icon={Gauge}
        isLoading={isLoading}
        error={failure}
        onRetry={retry}
        isEmpty={!data?.packetVolume.length}
        className="xl:col-span-2"
      >
        <TimeSeriesAreaChart
          data={data?.packetVolume ?? []}
          xKey="bucketStart"
          series={[{ key: "packets", label: "Packets", color: "var(--chart-1)" }]}
        />
      </ChartCard>

      <ChartCard
        title="Top source IPs"
        description="Hosts generating the most findings"
        icon={ArrowUpRight}
        isLoading={isLoading}
        error={failure}
        onRetry={retry}
        isEmpty={!data?.topSourceIps.length}
      >
        <CategoryBarChart data={data?.topSourceIps ?? []} />
      </ChartCard>

      <ChartCard
        title="Top destination IPs"
        description="Hosts most frequently targeted"
        icon={ArrowDownRight}
        isLoading={isLoading}
        error={failure}
        onRetry={retry}
        isEmpty={!data?.topDestinationIps.length}
      >
        <CategoryBarChart data={data?.topDestinationIps ?? []} />
      </ChartCard>
    </div>
  );
}
