import { createFileRoute } from "@tanstack/react-router";
import {
  BellRing,
  Database,
  Mail,
  MessageSquare,
  Server,
  ShieldAlert,
  Waypoints,
  Webhook,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { ComingSoonTag, OpsSection } from "@/components/observability";

export const Route = createFileRoute("/ops/alerts")({
  head: () => ({
    meta: [
      { title: "Alerting Center · Velorix Sentinel" },
      {
        name: "description",
        content: "Platform alert rules, notification channels and escalation policy.",
      },
      { property: "og:title", content: "Alerting Center · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Platform alert rules, notification channels and escalation policy.",
      },
    ],
  }),
  component: AlertingCenterPage,
});

const ALERT_CATEGORIES: {
  icon: LucideIcon;
  title: string;
  detail: string;
  conditions: string[];
}[] = [
  {
    icon: Server,
    title: "Platform Alerts",
    detail: "Availability, saturation and resource exhaustion across platform services.",
    conditions: [
      "Service unavailable for longer than the configured grace period",
      "CPU or memory sustained above the warning threshold",
      "Disk or capture storage approaching quota",
      "Inspection worker queue backlog growing",
    ],
  },
  {
    icon: ShieldAlert,
    title: "Security Alerts",
    detail: "Detection engine output and identity anomalies requiring analyst attention.",
    conditions: [
      "Critical severity threat detected in a capture",
      "Repeated failed authentication from a single source",
      "Privileged role granted or security policy disabled",
      "Session revoked following suspicious activity",
    ],
  },
  {
    icon: Waypoints,
    title: "API Alerts",
    detail: "Reliability and latency regressions on the public API surface.",
    conditions: [
      "Error rate above the configured percentage",
      "p95 latency above the configured budget",
      "Rate-limit rejections spiking on a single key",
      "Endpoint returning sustained 5xx responses",
    ],
  },
  {
    icon: Database,
    title: "Database Alerts",
    detail: "Connection pressure, slow queries and replication health.",
    conditions: [
      "Connection pool near exhaustion",
      "Average query time above threshold",
      "Replication lag exceeding the recovery point objective",
      "Backup missed or verification failed",
    ],
  },
];

const CHANNELS: { icon: LucideIcon; title: string; detail: string }[] = [
  {
    icon: Mail,
    title: "Email Notifications",
    detail: "Digest and immediate delivery to on-call distribution lists.",
  },
  {
    icon: MessageSquare,
    title: "Slack Notifications",
    detail: "Channel routing per severity with threaded incident updates.",
  },
  {
    icon: Webhook,
    title: "Webhook Notifications",
    detail: "Signed JSON payloads for SIEM, SOAR and paging providers.",
  },
];

function AlertingCenterPage() {
  return (
    <div className="space-y-6">
      <OpsSection
        title="Alert Rules"
        description="Conditions the alerting engine will evaluate against live telemetry."
        actions={<ComingSoonTag />}
      >
        <div className="grid gap-3 md:grid-cols-2">
          {ALERT_CATEGORIES.map((category) => (
            <article
              key={category.title}
              className="rounded-2xl border border-border/70 bg-muted/10 p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-start gap-3">
                  <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
                    <category.icon className="size-4" aria-hidden="true" />
                  </span>
                  <div className="min-w-0">
                    <h3 className="text-sm font-semibold">{category.title}</h3>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{category.detail}</p>
                  </div>
                </div>
                <ComingSoonTag />
              </div>
              <ul className="mt-3 space-y-1.5 border-t border-border/60 pt-3">
                {category.conditions.map((condition) => (
                  <li key={condition} className="flex gap-2 text-[11px] text-muted-foreground">
                    <span className="mt-1.5 size-1 shrink-0 rounded-full bg-primary/60" />
                    {condition}
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </OpsSection>

      <OpsSection
        title="Notification Channels"
        description="Delivery targets for triggered alerts."
        actions={<ComingSoonTag />}
      >
        <div className="grid gap-3 md:grid-cols-3">
          {CHANNELS.map((channel) => (
            <article
              key={channel.title}
              className="rounded-2xl border border-border/70 bg-muted/10 p-4"
            >
              <span className="grid size-9 place-items-center rounded-xl bg-primary/10 text-primary">
                <channel.icon className="size-4" aria-hidden="true" />
              </span>
              <h3 className="mt-3 text-sm font-semibold">{channel.title}</h3>
              <p className="mt-0.5 text-[11px] text-muted-foreground">{channel.detail}</p>
              <div className="mt-3">
                <ComingSoonTag />
              </div>
            </article>
          ))}
        </div>
      </OpsSection>

      <OpsSection
        title="Escalation Policy"
        description="Routing, acknowledgement windows and on-call rotation."
        actions={<ComingSoonTag />}
      >
        <div className="flex items-start gap-3 rounded-xl border border-dashed border-border/80 bg-muted/15 px-3 py-2.5">
          <BellRing className="mt-0.5 size-4 shrink-0 text-primary/80" aria-hidden="true" />
          <p className="text-[11px] text-muted-foreground">
            Escalation tiers, acknowledgement timers and paging integrations are configured once the
            alerting engine is enabled. No alert rules are active in this deployment.
          </p>
        </div>
      </OpsSection>
    </div>
  );
}
