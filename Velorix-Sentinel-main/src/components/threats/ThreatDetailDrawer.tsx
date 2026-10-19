import { Fingerprint } from "lucide-react";

import { SEVERITY_SURFACE, textOrDash } from "./threat-tokens";
import { EmptyState, SeverityBadge } from "@/components/common";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import { formatDateTime, formatNumber, humaniseEnum } from "@/utils/format";
import type { Threat } from "@/types/threats";

interface DrawerProps {
  threat: Threat | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-1">
      <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="break-words text-sm">{value}</p>
    </div>
  );
}

function Block({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3 rounded-xl border border-border bg-card/60 p-4">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
      </h3>
      {children}
    </section>
  );
}

/** Right-side investigation drawer for a single finding. */
export function ThreatDetailDrawer({ threat, open, onOpenChange }: DrawerProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full overflow-y-auto sm:max-w-xl"
        aria-label="Threat details"
      >
        {threat ? (
          <>
            <SheetHeader className="space-y-3 text-left">
              <div className="flex flex-wrap items-center gap-2">
                <SeverityBadge severity={threat.severity ?? "LOW"} />
                <span className="text-xs text-muted-foreground">
                  {humaniseEnum(threat.threatType ?? "UNKNOWN")}
                </span>
              </div>
              <SheetTitle className="text-lg">{textOrDash(threat.title)}</SheetTitle>
              <SheetDescription>
                Detected {formatDateTime(threat.detectedAt)} · confidence{" "}
                {Number.isFinite(threat.confidencePercent) ? `${threat.confidencePercent}%` : "—"}
              </SheetDescription>
            </SheetHeader>

            <div className="space-y-4 px-4 pb-8">
              <div
                className={cn(
                  "rounded-xl border p-4 text-sm",
                  SEVERITY_SURFACE[threat.severity] ?? "border-border bg-muted",
                )}
              >
                {threat.description?.trim()
                  ? threat.description
                  : "No description was supplied by the detection rule."}
              </div>

              <Block title="Detection">
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="Detection rule" value={textOrDash(threat.detectionRule)} />
                  <Field label="Rule version" value={textOrDash(threat.ruleVersion)} />
                  <Field
                    label="Packet count"
                    value={
                      Number.isFinite(threat.packetCount) ? formatNumber(threat.packetCount) : "—"
                    }
                  />
                  <Field label="Status" value={humaniseEnum(threat.status ?? "OPEN")} />
                  <Field label="First seen" value={formatDateTime(threat.firstSeenAt)} />
                  <Field label="Last seen" value={formatDateTime(threat.lastSeenAt)} />
                </div>
              </Block>

              <Block title="Affected hosts">
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="Source" value={textOrDash(threat.sourceIp)} />
                  <Field label="Destination" value={textOrDash(threat.destinationIp)} />
                  <Field label="Protocol" value={textOrDash(threat.protocol)} />
                  <Field label="Analysis" value={textOrDash(threat.analysisId)} />
                </div>
              </Block>

              <Block title="MITRE ATT&CK">
                {threat.mitreTechnique ? (
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Field label="Technique" value={threat.mitreTechnique} />
                    <Field label="Name" value={textOrDash(threat.mitreTechniqueName)} />
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    No technique mapping supplied for this finding.
                  </p>
                )}
              </Block>

              <Block title="Indicators of compromise">
                <IocList threat={threat} />
              </Block>

              <Block title="Evidence">
                {threat.evidence?.trim() ? (
                  <pre className="max-h-64 overflow-auto rounded-lg bg-muted p-3 font-mono text-xs whitespace-pre-wrap">
                    {threat.evidence}
                  </pre>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    No evidence payload was captured for this finding.
                  </p>
                )}
                {threat.samplePacketNumbers && threat.samplePacketNumbers.length > 0 && (
                  <p className="text-xs text-muted-foreground">
                    Sample packets: {threat.samplePacketNumbers.join(", ")}
                  </p>
                )}
              </Block>

              <Block title="Recommended action">
                <p className="text-sm">
                  {threat.recommendation?.trim()
                    ? threat.recommendation
                    : "No recommendation was supplied by the detection rule."}
                </p>
              </Block>
            </div>
          </>
        ) : (
          <div className="p-6">
            <EmptyState
              title="No threat selected"
              description="Choose a finding from the table to inspect its evidence."
              icon={Fingerprint}
            />
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

/** IOCs are derived strictly from fields the backend returned for the finding. */
function IocList({ threat }: { threat: Threat }) {
  const indicators: Array<{ kind: string; value: string }> = [];
  if (threat.sourceIp) indicators.push({ kind: "IP", value: threat.sourceIp });
  if (threat.destinationIp) indicators.push({ kind: "IP", value: threat.destinationIp });
  if (threat.protocol) indicators.push({ kind: "Protocol", value: threat.protocol });
  if (threat.mitreTechnique) indicators.push({ kind: "Technique", value: threat.mitreTechnique });
  if (threat.detectionRule) indicators.push({ kind: "Rule", value: threat.detectionRule });

  if (indicators.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">No indicators available for this finding yet.</p>
    );
  }

  return (
    <ul className="flex flex-wrap gap-2">
      {indicators.map((indicator) => (
        <li
          key={`${indicator.kind}-${indicator.value}`}
          className="rounded-lg border border-border bg-muted/60 px-2.5 py-1 font-mono text-xs"
        >
          <span className="mr-1.5 text-[10px] uppercase text-muted-foreground">
            {indicator.kind}
          </span>
          {indicator.value}
        </li>
      ))}
    </ul>
  );
}
