import { useMemo, useState } from "react";

import { STATUS_CLASS, textOrDash } from "./threat-tokens";
import { DataTable, SeverityBadge, type DataTableColumn } from "@/components/common";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { formatDateTime, humaniseEnum } from "@/utils/format";
import type { Threat, ThreatSeverity, ThreatStatus } from "@/types/threats";
import { THREAT_SEVERITIES, THREAT_STATUSES } from "@/types/threats";

interface ThreatsTableProps {
  threats: Threat[];
  isLoading: boolean;
  onSelect: (threat: Threat) => void;
}

/** Enterprise finding table with search, filters, sorting and pagination. */
export function ThreatsTable({ threats, isLoading, onSelect }: ThreatsTableProps) {
  const [severity, setSeverity] = useState<ThreatSeverity | "ALL">("ALL");
  const [status, setStatus] = useState<ThreatStatus | "ALL">("ALL");

  const rows = useMemo(
    () =>
      threats.filter(
        (threat) =>
          (severity === "ALL" || threat.severity === severity) &&
          (status === "ALL" || threat.status === status),
      ),
    [threats, severity, status],
  );

  const columns: Array<DataTableColumn<Threat>> = [
    {
      key: "title",
      header: "Threat",
      sortable: true,
      sortValue: (row) => row.title ?? "",
      cell: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{textOrDash(row.title)}</p>
          <p className="truncate text-xs text-muted-foreground">
            {humaniseEnum(row.threatType ?? "UNKNOWN")}
          </p>
        </div>
      ),
    },
    {
      key: "severity",
      header: "Severity",
      sortable: true,
      sortValue: (row) => THREAT_SEVERITIES.indexOf(row.severity),
      cell: (row) => <SeverityBadge severity={row.severity ?? "LOW"} />,
    },
    {
      key: "confidence",
      header: "Confidence",
      align: "right",
      sortable: true,
      sortValue: (row) => row.confidencePercent ?? 0,
      cell: (row) => (
        <span className="tabular-nums">
          {Number.isFinite(row.confidencePercent) ? `${row.confidencePercent}%` : "—"}
        </span>
      ),
    },
    {
      key: "sourceIp",
      header: "Source",
      sortable: true,
      sortValue: (row) => row.sourceIp ?? "",
      cell: (row) => <span className="font-mono text-xs">{textOrDash(row.sourceIp)}</span>,
    },
    {
      key: "destinationIp",
      header: "Destination",
      sortable: true,
      sortValue: (row) => row.destinationIp ?? "",
      cell: (row) => <span className="font-mono text-xs">{textOrDash(row.destinationIp)}</span>,
    },
    {
      key: "protocol",
      header: "Protocol",
      cell: (row) => <span className="text-xs">{textOrDash(row.protocol)}</span>,
    },
    {
      key: "mitre",
      header: "MITRE",
      cell: (row) => (
        <span className="font-mono text-xs text-muted-foreground">
          {textOrDash(row.mitreTechnique)}
        </span>
      ),
    },
    {
      key: "status",
      header: "Status",
      sortable: true,
      sortValue: (row) => row.status ?? "",
      cell: (row) => (
        <span
          className={cn(
            "inline-flex rounded-full border px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide",
            STATUS_CLASS[row.status] ?? "border-border text-muted-foreground",
          )}
        >
          {humaniseEnum(row.status ?? "OPEN")}
        </span>
      ),
    },
    {
      key: "detectedAt",
      header: "Detected",
      sortable: true,
      sortValue: (row) => (row.detectedAt ? Date.parse(row.detectedAt) : 0),
      cell: (row) => (
        <span className="whitespace-nowrap text-xs text-muted-foreground">
          {formatDateTime(row.detectedAt)}
        </span>
      ),
    },
    {
      key: "actions",
      header: "Actions",
      align: "right",
      cell: (row) => (
        <Button
          size="sm"
          variant="ghost"
          onClick={(event) => {
            event.stopPropagation();
            onSelect(row);
          }}
          aria-label={`Investigate ${row.title}`}
        >
          Investigate
        </Button>
      ),
    },
  ];

  const filterSelect = (
    label: string,
    value: string,
    options: string[],
    onChange: (next: string) => void,
  ) => (
    <label className="flex items-center gap-2 text-xs text-muted-foreground">
      <span className="sr-only sm:not-sr-only">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
        className="h-9 rounded-lg border border-border bg-background px-2 text-xs text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
      >
        <option value="ALL">All</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {humaniseEnum(option)}
          </option>
        ))}
      </select>
    </label>
  );

  return (
    <DataTable
      rows={rows}
      columns={columns}
      rowKey={(row) => row.id}
      isLoading={isLoading}
      onRowClick={onSelect}
      searchAccessor={(row) =>
        [row.title, row.description, row.sourceIp, row.destinationIp, row.detectionRule]
          .filter(Boolean)
          .join(" ")
      }
      searchPlaceholder="Search threats, hosts or rules…"
      emptyTitle="No threats detected yet"
      emptyDescription="Findings appear here once the detection engine runs over an inspected capture."
      toolbar={
        <div className="flex flex-wrap items-center gap-2">
          {filterSelect("Severity", severity, THREAT_SEVERITIES, (next) =>
            setSeverity(next as ThreatSeverity | "ALL"),
          )}
          {filterSelect("Status", status, THREAT_STATUSES, (next) =>
            setStatus(next as ThreatStatus | "ALL"),
          )}
        </div>
      }
    />
  );
}
