import { ArrowUpDown, ListFilter, Search, ShieldQuestion } from "lucide-react";
import { useMemo, useState } from "react";
import type { ReactNode } from "react";

import {
  AlertCategoryChip,
  AlertPriorityChip,
  AlertSeverityChip,
  AlertStatusChip,
  RiskScore,
} from "./AlertChips";
import { AwaitingTelemetry } from "./AwaitingTelemetry";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { SkeletonTable } from "@/components/common";
import { cn } from "@/lib/utils";
import type { AlertSeverity, AlertStatus, SocAlert } from "@/types/soc";

const SEVERITIES: AlertSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFORMATIONAL"];
const STATUSES: AlertStatus[] = [
  "NEW",
  "TRIAGED",
  "IN_PROGRESS",
  "ESCALATED",
  "SUPPRESSED",
  "CLOSED",
  "FALSE_POSITIVE",
];

const SEVERITY_RANK: Record<AlertSeverity, number> = {
  CRITICAL: 5,
  HIGH: 4,
  MEDIUM: 3,
  LOW: 2,
  INFORMATIONAL: 1,
};

type SortKey = "createdAt" | "riskScore" | "severity" | "id";

interface AlertTriageQueueProps {
  alerts: SocAlert[];
  isLoading?: boolean;
  awaiting?: boolean;
  pageSize?: number;
  selectedAlertId?: string | null;
  onOpen?: (alert: SocAlert) => void;
  bulkActions?: (selectedIds: string[], clear: () => void) => ReactNode;
  className?: string;
}

/**
 * Alert triage queue: search, severity/status filters, sorting, bulk selection
 * and pagination. Presentation only — rows always come from the caller so the
 * table can be wired to `/v1/soc/alerts` without changes here.
 */
export function AlertTriageQueue({
  alerts,
  isLoading = false,
  awaiting = false,
  pageSize = 10,
  selectedAlertId,
  onOpen,
  bulkActions,
  className,
}: AlertTriageQueueProps) {
  const [search, setSearch] = useState("");
  const [severity, setSeverity] = useState<AlertSeverity | "ALL">("ALL");
  const [status, setStatus] = useState<AlertStatus | "ALL">("ALL");
  const [sortKey, setSortKey] = useState<SortKey>("createdAt");
  const [ascending, setAscending] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string[]>([]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    const rows = alerts.filter((alert) => {
      if (severity !== "ALL" && alert.severity !== severity) return false;
      if (status !== "ALL" && alert.status !== status) return false;
      if (!term) return true;
      return (
        alert.name.toLowerCase().includes(term) ||
        alert.reference.toLowerCase().includes(term) ||
        (alert.sourceAddress ?? "").includes(term) ||
        (alert.destinationAddress ?? "").includes(term)
      );
    });

    const sorted = [...rows].sort((a, b) => {
      let delta = 0;
      if (sortKey === "severity") delta = SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity];
      else if (sortKey === "riskScore") delta = a.riskScore - b.riskScore;
      else if (sortKey === "id") delta = a.reference.localeCompare(b.reference);
      else delta = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      return ascending ? delta : -delta;
    });

    return sorted;
  }, [alerts, search, severity, status, sortKey, ascending]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const safePage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(safePage * pageSize, safePage * pageSize + pageSize);
  const allOnPageSelected = rows.length > 0 && rows.every((row) => selected.includes(row.id));

  const toggleSort = (key: SortKey) => {
    if (key === sortKey) setAscending((value) => !value);
    else {
      setSortKey(key);
      setAscending(false);
    }
  };

  const clearSelection = () => setSelected([]);

  return (
    <div className={cn("space-y-3", className)}>
      <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
        <div className="relative min-w-0">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder="Search alert name, reference or address…"
            aria-label="Search alerts"
            className="ps-9"
          />
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <SelectPill
            label="Severity"
            value={severity}
            options={["ALL", ...SEVERITIES]}
            onChange={(value) => {
              setSeverity(value as AlertSeverity | "ALL");
              setPage(0);
            }}
          />
          <SelectPill
            label="Status"
            value={status}
            options={["ALL", ...STATUSES]}
            onChange={(value) => {
              setStatus(value as AlertStatus | "ALL");
              setPage(0);
            }}
          />
        </div>
      </div>

      {selected.length > 0 && bulkActions && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-primary/30 bg-primary/5 px-3 py-2">
          <span className="text-xs text-muted-foreground">{selected.length} selected</span>
          {bulkActions(selected, clearSelection)}
        </div>
      )}

      {isLoading ? (
        <SkeletonTable rows={5} />
      ) : rows.length === 0 ? (
        <AwaitingTelemetry
          icon={ShieldQuestion}
          title={awaiting ? "No telemetry connected." : "No alerts match these filters."}
          detail={
            awaiting
              ? "The triage queue loads from /v1/soc/alerts once the SOC backend ships. No sample alerts are shown."
              : "Adjust the search term, severity or status filter to widen the queue."
          }
        />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface/60 text-[11px] tracking-wider text-muted-foreground uppercase">
              <tr>
                <th className="w-10 px-3 py-2.5">
                  <Checkbox
                    checked={allOnPageSelected}
                    aria-label="Select all alerts on this page"
                    onCheckedChange={(checked) =>
                      setSelected((prev) =>
                        checked
                          ? Array.from(new Set([...prev, ...rows.map((row) => row.id)]))
                          : prev.filter((id) => !rows.some((row) => row.id === id)),
                      )
                    }
                  />
                </th>
                <SortHeader
                  label="Alert ID"
                  active={sortKey === "id"}
                  ascending={ascending}
                  onClick={() => toggleSort("id")}
                />
                <SortHeader
                  label="Severity"
                  active={sortKey === "severity"}
                  ascending={ascending}
                  onClick={() => toggleSort("severity")}
                />
                <th className="px-3 py-2.5 text-left font-medium">Priority</th>
                <th className="px-3 py-2.5 text-left font-medium">Category</th>
                <th className="px-3 py-2.5 text-left font-medium">Assigned</th>
                <th className="px-3 py-2.5 text-left font-medium">Status</th>
                <SortHeader
                  label="Created"
                  active={sortKey === "createdAt"}
                  ascending={ascending}
                  onClick={() => toggleSort("createdAt")}
                />
                <SortHeader
                  label="Risk"
                  active={sortKey === "riskScore"}
                  ascending={ascending}
                  onClick={() => toggleSort("riskScore")}
                />
              </tr>
            </thead>
            <tbody>
              {rows.map((alert) => (
                <tr
                  key={alert.id}
                  onClick={() => onOpen?.(alert)}
                  className={cn(
                    "cursor-pointer border-t border-border/70 transition-colors hover:bg-surface/40",
                    selectedAlertId === alert.id && "bg-primary/5",
                  )}
                >
                  <td className="px-3 py-2.5" onClick={(event) => event.stopPropagation()}>
                    <Checkbox
                      checked={selected.includes(alert.id)}
                      aria-label={`Select ${alert.reference}`}
                      onCheckedChange={(checked) =>
                        setSelected((prev) =>
                          checked ? [...prev, alert.id] : prev.filter((id) => id !== alert.id),
                        )
                      }
                    />
                  </td>
                  <td className="px-3 py-2.5">
                    <span className="block font-mono text-xs text-primary">{alert.reference}</span>
                    <span className="block max-w-56 truncate text-xs text-muted-foreground">
                      {alert.name}
                    </span>
                  </td>
                  <td className="px-3 py-2.5">
                    <AlertSeverityChip severity={alert.severity} />
                  </td>
                  <td className="px-3 py-2.5">
                    <AlertPriorityChip priority={alert.priority} />
                  </td>
                  <td className="px-3 py-2.5">
                    <AlertCategoryChip category={alert.category} />
                  </td>
                  <td className="px-3 py-2.5 text-xs text-muted-foreground">
                    {alert.assignee?.displayName ?? "Unassigned"}
                  </td>
                  <td className="px-3 py-2.5">
                    <AlertStatusChip status={alert.status} />
                  </td>
                  <td className="px-3 py-2.5 font-mono text-[11px] text-muted-foreground">
                    {new Date(alert.createdAt).toLocaleString()}
                  </td>
                  <td className="px-3 py-2.5">
                    <RiskScore score={alert.riskScore} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {filtered.length > pageSize && (
        <div className="flex items-center justify-between gap-3">
          <p className="text-[11px] text-muted-foreground">
            Page {safePage + 1} of {pageCount} · {filtered.length} alerts
          </p>
          <div className="flex gap-1.5">
            <Button
              variant="outline"
              size="sm"
              disabled={safePage === 0}
              onClick={() => setPage(safePage - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={safePage >= pageCount - 1}
              onClick={() => setPage(safePage + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function SortHeader({
  label,
  active,
  ascending,
  onClick,
}: {
  label: string;
  active: boolean;
  ascending: boolean;
  onClick: () => void;
}) {
  return (
    <th className="px-3 py-2.5 text-left font-medium">
      <button
        type="button"
        onClick={onClick}
        className={cn(
          "focus-ring inline-flex items-center gap-1 rounded transition-colors hover:text-foreground",
          active && "text-primary",
        )}
        aria-label={`Sort by ${label}`}
      >
        {label}
        <ArrowUpDown
          className={cn("size-3", active && ascending && "rotate-180")}
          aria-hidden="true"
        />
      </button>
    </th>
  );
}

function SelectPill({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface/60 px-2.5 py-1 text-[11px] text-muted-foreground">
      <ListFilter className="size-3" aria-hidden="true" />
      <span className="sr-only">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
        className="focus-ring bg-transparent text-[11px] outline-none"
      >
        {options.map((option) => (
          <option key={option} value={option} className="bg-card">
            {option === "ALL" ? `All ${label.toLowerCase()}` : option.replace(/_/g, " ")}
          </option>
        ))}
      </select>
    </label>
  );
}
