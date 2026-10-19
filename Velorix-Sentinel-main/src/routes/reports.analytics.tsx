import { createFileRoute } from "@tanstack/react-router";
import { BarChart3, GitCompare, LineChart, PieChart } from "lucide-react";

import { AwaitingData } from "@/components/governance/AwaitingData";
import { GovernanceSection } from "@/components/governance/GovernanceSection";
import { useGovernanceResource } from "@/hooks/useGovernanceResource";
import { governanceService } from "@/services/governanceService";
import type { ExecutiveAnalyticsData } from "@/types/governance";

export const Route = createFileRoute("/reports/analytics")({
  head: () => ({
    meta: [
      { title: "Security Analytics · Velorix Sentinel" },
      {
        name: "description",
        content: "Trend analytics across detections, incidents and control effectiveness.",
      },
      { property: "og:title", content: "Security Analytics · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Trend analytics across detections, incidents and control effectiveness.",
      },
    ],
  }),
  component: SecurityAnalytics,
});

function SecurityAnalytics() {
  const analytics = useGovernanceResource<ExecutiveAnalyticsData>(
    (signal) => governanceService.getExecutiveAnalytics(signal),
    [],
  );

  const data = analytics.status === "ready" ? analytics.data : null;

  const panels = [
    {
      title: "Incident Trend Analysis",
      description: "Volume and severity over the reporting period",
      icon: LineChart,
      populated: (data?.incidentTrend.length ?? 0) > 0,
    },
    {
      title: "Risk Evolution",
      description: "Residual risk trajectory against the target profile",
      icon: LineChart,
      populated: (data?.riskEvolution.length ?? 0) > 0,
    },
    {
      title: "Compliance Progress",
      description: "Control coverage growth across frameworks",
      icon: BarChart3,
      populated: (data?.complianceProgress.length ?? 0) > 0,
    },
    {
      title: "Department Comparison",
      description: "Relative posture by business unit",
      icon: GitCompare,
      populated: (data?.departmentComparison.length ?? 0) > 0,
    },
    {
      title: "Asset Risk Distribution",
      description: "Exposure concentration across the estate",
      icon: PieChart,
      populated: (data?.assetRiskDistribution.length ?? 0) > 0,
    },
  ];

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {panels.map((panel) => (
        <GovernanceSection key={panel.title} title={panel.title} description={panel.description}>
          <AwaitingData
            icon={panel.icon}
            detail={
              panel.populated
                ? "Rendering live series."
                : "Charts render once the analytics service returns a real series. No synthetic trend line is drawn."
            }
          />
        </GovernanceSection>
      ))}
    </div>
  );
}
