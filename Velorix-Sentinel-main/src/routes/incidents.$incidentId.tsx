import { createFileRoute } from "@tanstack/react-router";
import {
  BrainCircuit,
  Crosshair,
  FileSearch,
  Fingerprint,
  History,
  Lightbulb,
  ListTree,
  MessageSquare,
  Network,
  Server,
  ShieldAlert,
  Star,
} from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import {
  AwaitingBackend,
  CaseTimeline,
  CategoryChip,
  CollaborationPanel,
  EvidenceVault,
  IncidentCard,
  IncidentStatusChip,
  PriorityChip,
  SeverityChip,
} from "@/components/incidents";
import { PageHeader } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useAnalystWorkspace } from "@/hooks/useAnalystWorkspace";
import { useIncidentResource } from "@/hooks/useIncidentResource";
import { incidentService } from "@/services/incidentService";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/incidents/$incidentId")({
  head: () => ({
    meta: [
      { title: "Incident Investigation · Velorix Sentinel" },
      {
        name: "description",
        content: "Full investigation view with timeline, evidence, assets and MITRE mapping.",
      },
      { property: "og:title", content: "Incident Investigation · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Full investigation view with timeline, evidence, assets and MITRE mapping.",
      },
    ],
  }),
  component: IncidentDetailPage,
});

const UNAVAILABLE = "Available after backend integration.";

const SECTIONS = [
  { id: "overview", label: "Overview", icon: ShieldAlert },
  { id: "timeline", label: "Timeline", icon: History },
  { id: "evidence", label: "Evidence", icon: FileSearch },
  { id: "assets", label: "Affected Assets", icon: Server },
  { id: "indicators", label: "Indicators", icon: Fingerprint },
  { id: "alerts", label: "Related Alerts", icon: Network },
  { id: "mitre", label: "MITRE Mapping", icon: Crosshair },
  { id: "recommendations", label: "Recommendations", icon: Lightbulb },
  { id: "ai", label: "AI Summary", icon: BrainCircuit },
  { id: "collaboration", label: "Collaboration", icon: MessageSquare },
  { id: "activity", label: "Activity Log", icon: ListTree },
] as const;

type SectionId = (typeof SECTIONS)[number]["id"];

function IncidentDetailPage() {
  const { incidentId } = Route.useParams();
  const [section, setSection] = useState<SectionId>("overview");
  const { starredIncidentIds, toggleStar, pinnedIncidentIds, togglePin } = useAnalystWorkspace();

  const detail = useIncidentResource(
    (signal) => incidentService.getById(incidentId, signal),
    [incidentId],
  );
  const incident = detail.data;
  const starred = starredIncidentIds.includes(incidentId);
  const pinned = pinnedIncidentIds.includes(incidentId);

  const emptyDetail =
    "This case has no data yet. Investigation content loads from the response backend once it is connected.";

  return (
    <div className="space-y-6">
      <PageHeader
        title={incident?.title ?? "Investigation"}
        description={
          incident
            ? `${incident.reference} · opened ${new Date(incident.createdAt).toLocaleString()}`
            : `Case ${incidentId}`
        }
        actions={
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={() => toggleStar(incidentId)}
              aria-pressed={starred}
            >
              <Star className={cn("size-4", starred && "fill-amber-400 text-amber-400")} />
              {starred ? "Starred" : "Star"}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => togglePin(incidentId)}
              aria-pressed={pinned}
            >
              {pinned ? "Unpin" : "Pin to workspace"}
            </Button>
            <Button
              size="sm"
              disabled
              title={UNAVAILABLE}
              onClick={() => toast.info("Update case", { description: UNAVAILABLE })}
            >
              Update case
            </Button>
          </>
        }
      />

      {incident && (
        <div className="flex flex-wrap items-center gap-2">
          <SeverityChip severity={incident.severity} />
          <IncidentStatusChip status={incident.status} />
          <PriorityChip priority={incident.priority} />
          <CategoryChip category={incident.category} />
          <span className="text-xs text-muted-foreground">
            Risk {incident.riskScore} · {incident.assignee?.displayName ?? "Unassigned"}
          </span>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[220px_1fr]">
        <nav aria-label="Investigation sections" className="lg:sticky lg:top-4 lg:self-start">
          <ul className="flex gap-1 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
            {SECTIONS.map((item) => (
              <li key={item.id} className="shrink-0 lg:w-full">
                <button
                  type="button"
                  onClick={() => setSection(item.id)}
                  aria-current={section === item.id ? "true" : undefined}
                  className={cn(
                    "focus-ring flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-xs transition-colors",
                    section === item.id
                      ? "bg-primary/15 font-medium text-primary"
                      : "text-muted-foreground hover:bg-surface/60 hover:text-foreground",
                  )}
                >
                  <item.icon className="size-3.5 shrink-0" aria-hidden="true" />
                  <span className="whitespace-nowrap">{item.label}</span>
                </button>
              </li>
            ))}
          </ul>
        </nav>

        <div className="min-w-0 space-y-4">
          {section === "overview" && (
            <IncidentCard
              title="Case Overview"
              description="Summary and key facts"
              icon={ShieldAlert}
            >
              {incident ? (
                <div className="space-y-4">
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    {incident.description}
                  </p>
                  <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                    <Fact label="Source" value={incident.source.replace(/_/g, " ")} />
                    <Fact label="Detected" value={fmt(incident.detectedAt)} />
                    <Fact label="First response" value={fmt(incident.respondedAt)} />
                    <Fact label="Resolved" value={fmt(incident.resolvedAt)} />
                    <Fact label="Related alerts" value={String(incident.alertCount)} />
                    <Fact label="Affected assets" value={String(incident.affectedAssetCount)} />
                  </dl>
                  {incident.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {incident.tags.map((tag) => (
                        <span
                          key={tag}
                          className="rounded-full border border-border bg-muted/40 px-2 py-0.5 text-[11px] text-muted-foreground"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                <AwaitingBackend title="Case details unavailable." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}

          {section === "timeline" && (
            <IncidentCard
              title="Investigation Timeline"
              description="Case creation, assignment, status changes, evidence and resolution"
              icon={History}
            >
              {incident && incident.timeline.length > 0 ? (
                <CaseTimeline events={incident.timeline} />
              ) : (
                <AwaitingBackend title="No timeline events recorded." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}

          {section === "evidence" && (
            <IncidentCard
              title="Evidence"
              description="Collected artefacts with chain of custody"
              icon={FileSearch}
            >
              <EvidenceVault items={incident?.evidence ?? []} />
            </IncidentCard>
          )}

          {section === "assets" && (
            <IncidentCard
              title="Affected Assets"
              description="Hosts and endpoints in scope"
              icon={Server}
            >
              {incident && incident.assets.length > 0 ? (
                <div className="overflow-x-auto rounded-xl border border-border">
                  <table className="w-full text-sm">
                    <thead className="bg-surface/60 text-xs tracking-wider text-muted-foreground uppercase">
                      <tr>
                        <th className="px-4 py-3 text-left font-medium">Host</th>
                        <th className="px-4 py-3 text-left font-medium">IP</th>
                        <th className="px-4 py-3 text-left font-medium">MAC</th>
                        <th className="px-4 py-3 text-left font-medium">Type</th>
                        <th className="px-4 py-3 text-left font-medium">Criticality</th>
                        <th className="px-4 py-3 text-left font-medium">Last seen</th>
                      </tr>
                    </thead>
                    <tbody>
                      {incident.assets.map((asset) => (
                        <tr key={asset.id} className="border-t border-border/70">
                          <td className="px-4 py-3 font-medium">{asset.hostname ?? "—"}</td>
                          <td className="px-4 py-3 font-mono text-xs">{asset.ipAddress ?? "—"}</td>
                          <td className="px-4 py-3 font-mono text-xs">{asset.macAddress ?? "—"}</td>
                          <td className="px-4 py-3 text-muted-foreground">{asset.assetType}</td>
                          <td className="px-4 py-3">
                            <SeverityChip severity={asset.criticality} />
                          </td>
                          <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                            {fmt(asset.lastSeen)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <AwaitingBackend title="No affected assets identified." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}

          {section === "indicators" && (
            <IncidentCard
              title="Indicators"
              description="Observables extracted during the investigation"
              icon={Fingerprint}
            >
              {incident && incident.indicators.length > 0 ? (
                <ul className="space-y-2">
                  {incident.indicators.map((indicator) => (
                    <li
                      key={indicator.id}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/70 bg-card/40 px-3 py-2"
                    >
                      <span className="min-w-0">
                        <span className="block font-mono text-xs break-all">{indicator.value}</span>
                        <span className="block text-[11px] text-muted-foreground">
                          {indicator.kind} · first seen {fmt(indicator.firstSeen)}
                        </span>
                      </span>
                      <SeverityChip severity={indicator.severity} />
                    </li>
                  ))}
                </ul>
              ) : (
                <AwaitingBackend title="No indicators extracted." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}

          {section === "alerts" && (
            <IncidentCard
              title="Related Alerts"
              description="Detections correlated into this case"
              icon={Network}
            >
              {incident && incident.relatedAlerts.length > 0 ? (
                <ul className="space-y-2">
                  {incident.relatedAlerts.map((alert) => (
                    <li
                      key={alert.id}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/70 bg-card/40 px-3 py-2"
                    >
                      <span className="min-w-0">
                        <span className="block text-sm font-medium">{alert.title}</span>
                        <span className="block text-[11px] text-muted-foreground">
                          {alert.source} · {fmt(alert.raisedAt)}
                        </span>
                      </span>
                      <SeverityChip severity={alert.severity} />
                    </li>
                  ))}
                </ul>
              ) : (
                <AwaitingBackend title="No related alerts correlated." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}

          {section === "mitre" && (
            <IncidentCard
              title="MITRE ATT&CK Mapping"
              description="Tactics and techniques observed in this case"
              icon={Crosshair}
            >
              {incident && incident.mitre.length > 0 ? (
                <ul className="grid gap-2 sm:grid-cols-2">
                  {incident.mitre.map((mapping) => (
                    <li
                      key={`${mapping.tacticId}-${mapping.techniqueId}`}
                      className="rounded-xl border border-border/70 bg-card/40 p-3"
                    >
                      <p className="font-mono text-xs text-primary">{mapping.techniqueId}</p>
                      <p className="text-sm font-medium">{mapping.techniqueName}</p>
                      <p className="text-[11px] text-muted-foreground">
                        {mapping.tacticName} · confidence {mapping.confidence}%
                      </p>
                    </li>
                  ))}
                </ul>
              ) : (
                <AwaitingBackend title="No ATT&CK mapping recorded." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}

          {section === "recommendations" && (
            <IncidentCard
              title="Recommendations"
              description="Suggested response actions for this case"
              icon={Lightbulb}
            >
              {incident && incident.recommendations.length > 0 ? (
                <ol className="space-y-2">
                  {incident.recommendations.map((item, index) => (
                    <li
                      key={item}
                      className="flex gap-3 rounded-xl border border-border/70 bg-card/40 p-3"
                    >
                      <span className="grid size-6 shrink-0 place-items-center rounded-full bg-primary/10 text-xs font-semibold text-primary">
                        {index + 1}
                      </span>
                      <span className="text-sm text-muted-foreground">{item}</span>
                    </li>
                  ))}
                </ol>
              ) : (
                <AwaitingBackend
                  title="No recommendations generated."
                  detail="Response recommendations are produced by the analysis backend for each case."
                />
              )}
            </IncidentCard>
          )}

          {section === "ai" && (
            <IncidentCard
              title="AI Summary"
              description="Model-generated investigation narrative"
              icon={BrainCircuit}
              action={
                <Button
                  size="sm"
                  variant="outline"
                  disabled
                  title={UNAVAILABLE}
                  onClick={() => toast.info("Generate AI summary", { description: UNAVAILABLE })}
                >
                  Generate
                </Button>
              }
            >
              {incident?.aiSummary ? (
                <p className="text-sm leading-relaxed whitespace-pre-wrap text-muted-foreground">
                  {incident.aiSummary}
                </p>
              ) : (
                <AwaitingBackend
                  icon={BrainCircuit}
                  title="AI summary not generated."
                  detail="The Security Copilot will summarise this investigation once the AI backend is connected. No summary is simulated."
                />
              )}
            </IncidentCard>
          )}

          {section === "collaboration" && (
            <IncidentCard
              title="Collaboration"
              description="Internal notes, mentions, discussion and watchers"
              icon={MessageSquare}
            >
              <CollaborationPanel
                comments={incident?.comments ?? []}
                watchers={incident?.watchers ?? []}
              />
            </IncidentCard>
          )}

          {section === "activity" && (
            <IncidentCard
              title="Activity Log"
              description="Immutable audit trail of every action on this case"
              icon={ListTree}
            >
              {incident && incident.timeline.length > 0 ? (
                <ul className="divide-y divide-border/70 rounded-xl border border-border">
                  {incident.timeline.map((event) => (
                    <li
                      key={event.id}
                      className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm"
                    >
                      <span>
                        <span className="font-mono text-[11px] text-primary">
                          {event.kind.replace(/_/g, " ")}
                        </span>
                        <span className="ml-2 text-muted-foreground">{event.summary}</span>
                      </span>
                      <span className="font-mono text-[11px] text-muted-foreground">
                        {event.actor?.displayName ?? "system"} · {fmt(event.occurredAt)}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <AwaitingBackend title="No activity recorded." detail={emptyDetail} />
              )}
            </IncidentCard>
          )}
        </div>
      </div>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border/70 bg-card/40 p-3">
      <dt className="text-[11px] text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm font-medium">{value}</dd>
    </div>
  );
}

function fmt(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}
