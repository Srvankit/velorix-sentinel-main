import { createFileRoute } from "@tanstack/react-router";
import { Archive, DatabaseBackup, LifeBuoy, RotateCcw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { AwaitingMonitoring, OpsKpi, OpsSection, StatRow } from "@/components/observability";
import { useObservabilityResource } from "@/hooks/useObservabilityResource";
import { observabilityService, OBSERVABILITY_WRITE_READY } from "@/services/observabilityService";

export const Route = createFileRoute("/ops/backups")({
  head: () => ({
    meta: [
      { title: "Backup & Recovery · Velorix Sentinel" },
      {
        name: "description",
        content: "Backup schedule, retention, recovery objectives and restore controls.",
      },
      { property: "og:title", content: "Backup & Recovery · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Backup schedule, retention, recovery objectives and restore controls.",
      },
    ],
  }),
  component: BackupCenterPage,
});

function BackupCenterPage() {
  const backups = useObservabilityResource(observabilityService.getBackupStatus);
  const data = backups.data;

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <OpsKpi
          label="Latest Backup"
          icon={DatabaseBackup}
          value={data?.lastBackupAt ? new Date(data.lastBackupAt).toLocaleString() : null}
          state={data?.state ?? "UNKNOWN"}
          hint="Most recent successful protected recovery point"
        />
        <OpsKpi
          label="Backup Schedule"
          icon={Archive}
          value={data?.schedule ?? null}
          hint="Cadence configured for automated snapshots"
        />
        <OpsKpi
          label="Retention"
          icon={Archive}
          value={data?.retentionDays ?? null}
          suffix=" days"
          hint="How long recovery points are preserved"
        />
        <OpsKpi
          label="Restore Rehearsal"
          icon={LifeBuoy}
          value={data?.restoreTestedAt ? new Date(data.restoreTestedAt).toLocaleString() : null}
          hint="Last verified restore drill"
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <OpsSection
          title="Disaster Recovery Objectives"
          description="Targets governing acceptable data loss and downtime."
        >
          <div className="space-y-2">
            <StatRow
              label="Recovery Point Objective (RPO)"
              value={data?.recoveryPointObjective ?? undefined}
              hint="Maximum tolerable data loss"
            />
            <StatRow
              label="Recovery Time Objective (RTO)"
              value={data?.recoveryTimeObjective ?? undefined}
              hint="Maximum tolerable time to restore service"
            />
            <StatRow label="Failover Region" hint="Secondary region for regional failure" />
            <StatRow label="Runbook Owner" hint="Team accountable for recovery execution" />
          </div>
        </OpsSection>

        <OpsSection
          title="Restore"
          description="Recover the platform database to a selected recovery point."
        >
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground">
              Restores are destructive and run through the operations control plane with explicit
              approval. Capture evidence in object storage is restored separately.
            </p>
            <Button variant="outline" size="sm" disabled={!OBSERVABILITY_WRITE_READY}>
              <RotateCcw className="size-3.5" aria-hidden="true" />
              Start restore
            </Button>
            {!OBSERVABILITY_WRITE_READY && (
              <p className="text-[11px] text-muted-foreground">
                Enabled once the backup control API is available.
              </p>
            )}
          </div>
        </OpsSection>
      </div>

      <OpsSection
        title="Backup History"
        description="Snapshot inventory with size, duration and verification outcome."
      >
        <AwaitingMonitoring detail="Backup records are published by the managed database provider. No snapshots, sizes or timestamps are simulated." />
      </OpsSection>
    </div>
  );
}
