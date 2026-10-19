import { Link } from "@tanstack/react-router";
import { Star } from "lucide-react";
import { useMemo, useState } from "react";
import type { ReactNode } from "react";

import { AwaitingBackend } from "./AwaitingBackend";
import { CategoryChip, IncidentStatusChip, PriorityChip, SeverityChip } from "./IncidentChips";
import { Button } from "@/components/ui/button";
import { SkeletonTable } from "@/components/common";
import { cn } from "@/lib/utils";
import type { Incident, IncidentSeverity, IncidentStatus } from "@/types/incident";

const SEVERITIES: IncidentSeverity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFORMATIONAL"];
const STATUSES: IncidentStatus[] = [
  "NEW",
  "TRIAGED",
  "INVESTIGATING",
  "CONTAINED",
  "ERADICATED",
  "RECOVERED",
  "RESOLVED",
  "CLOSED",
  "FALSE_POSITIVE",
];

type SortKey = "createdAt" | "updatedAt" | "riskScore" | "severity" | "title";

const SEVERITY_RANK: Record<IncidentSeverity, number> = {
  CRITICAL: 5,
  HIGH: 4,
  MEDIUM: 3,
  LOW: 2,
  INFORMATIONAL: 1,
};

interface IncidentTableProps {
  incidents: Incident[];
  isLoading?: boolean;
  awaiting?: boolean;
  pageSize?: number;
  starredIds?: string[];
  onToggleStar?: (id: string) => void;
  /** Rendered above the table when at least one row is selected. */
  bulkActions?: (selectedIds: string[], clear: () => void) => ReactNode;
  emptyTitle?: string;
  emptyDetail?: string;
  className?: string;
}

function formatDate(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}

/**
 * Enterprise incident table: search, severity/status filters, sorting,
 * pagination, row selection and bulk actions. Presentation only — data always
 * comes from the caller so it can be wired to the API without changes here.
 */
export function IncidentTable({
  incidents,
  isLoading = false,
  awaiting = false,
  pageSize = 15,
  starredIds = [],
  onToggleStar,
  bulkActions,
  emptyTitle,
  emptyDetail,
  className,
}: IncidentTableProps) {
  const [query, setQuery] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity | "ALL">("ALL");
  const [status, setStatus] = useState<IncidentStatus | "ALL">("ALL");
  const [sort, setSort] = useState<{ key: SortKey; direction: "asc" | "desc" }>({
    key: "createdAt",
    direction: "desc",
  });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string[]>([]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return incidents.filter((incident) => {
      if (severity !== "ALL" && incident.severity !== severity) return false;
      if (status !== "ALL" && incident.status !== status) return false;
      if (!needle) return true;
      return [
        incident.reference,
        incident.title,
        incident.category,
        incident.source,
        incident.assignee?.displayName ?? "",
        incident.tags.join(" "),
      ]
        .join(" ")
        .toLowerCase()
        .includes(needle);
    });
  }, [incidents, query, severity, status]);

  const sorted = useMemo(() => {
    const factor = sort.direction === "asc" ? 1 : -1;
    return [...filtered].sort((a, b) => {
      switch (sort.key) {
        case "riskScore":
          return (a.riskScore - b.riskScore) * factor;
        case "severity":
          return (SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity]) * factor;
        case "title":
          return a.title.localeCompare(b.title) * factor;
        default:
          return (new Date(a[sort.key]).getTime() - new Date(b[sort.key]).getTime()) * factor;
      }
    });
  }, [filtered, sort]);

  const pageCount = Math.max(1, Math.ceil(sorted.length / pageSize));
  const current = Math.min(page, pageCount - 1);
  const visible = sorted.slice(current * pageSize, current * pageSize + pageSize);
  const allVisibleSelected = visible.length > 0 && visible.every((i) => selected.includes(i.id));

  function toggleSort(key: SortKey) {
    setPage(0);
    setSort((prev) =>
      prev.key === key
        ? { key, direction: prev.direction === "asc" ? "desc" : "asc" }
        : { key, direction: "desc" },
    );
  }

  const selectClass =
    "focus-ring h-9 rounded-lg border border-input bg-surface px-3 text-sm text-foreground";

  const header = (
    <div className="flex flex-wrap items-center gap-2">
      <input
        type="search"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value);
          setPage(0);
        }}
        placeholder="Search incident ID, title, analyst or tag…"
        aria-label="Search incidents"
        className="focus-ring h-9 min-w-56 flex-1 rounded-lg border border-input bg-surface px-3 text-sm placeholder:text-muted-foreground"
      />
      <select
        value={severity}
        onChange={(event) => {
          setSeverity(event.target.value as IncidentSeverity | "ALL");
          setPage(0);
        }}
        aria-label="Filter by severity"
        className={selectClass}
      >
        <option value="ALL">All severities</option>
        {SEVERITIES.map((value) => (
          <option key={value} value={value}>
            {value}
          </option>
        ))}
      </select>
      <select
        value={status}
        onChange={(event) => {
          setStatus(event.target.value as IncidentStatus | "ALL");
          setPage(0);
        }}
        aria-label="Filter by status"
        className={selectClass}
      >
        <option value="ALL">All statuses</option>
        {STATUSES.map((value) => (
          <option key={value} value={value}>
            {value.replace(/_/g, " ")}
          </option>
        ))}
      </select>
    </div>
  );

  if (isLoading) {
    return (
      <div className={cn("space-y-4", className)}>
        {header}
        <SkeletonTable rows={6} />
      </div>
    );
  }

  return (
    <div className={cn("space-y-4", className)}>
      {header}

      {selected.length > 0 && bulkActions && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-primary/30 bg-primary/5 px-3 py-2">
          <span className="text-xs font-medium text-primary">{selected.length} selected</span>
          {bulkActions(selected, () => setSelected([]))}
        </div>
      )}

      {visible.length === 0 ? (
        <AwaitingBackend
          title={emptyTitle ?? (awaiting ? "No incidents have been detected." : "No matches")}
          detail={
            emptyDetail ??
            (awaiting
              ? "Incidents raised by the detection engine will be listed here once the response backend is connected."
              : "No incident matches the current search and filters.")
          }
        />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[1100px] text-sm">
            <thead className="bg-surface/60">
              <tr className="text-xs tracking-wider text-muted-foreground uppercase">
                <th scope="col" className="w-10 px-3 py-3">
                  <input
                    type="checkbox"
                    aria-label="Select all visible incidents"
                    checked={allVisibleSelected}
                    onChange={() =>
                      setSelected(allVisibleSelected ? [] : visible.map((item) => item.id))
                    }
                    className="size-4 accent-[var(--primary)]"
                  />
                </th>
                {onToggleStar && <th scope="col" className="w-10 px-2 py-3" />}
                <SortableHead label="Incident" onClick={() => toggleSort("title")} />
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Severity
                </th>
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Status
                </th>
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Priority
                </th>
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Analyst
                </th>
                <SortableHead label="Created" onClick={() => toggleSort("createdAt")} />
                <SortableHead label="Updated" onClick={() => toggleSort("updatedAt")} />
                <SortableHead label="Risk" onClick={() => toggleSort("riskScore")} align="right" />
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Source
                </th>
                <th scope="col" className="px-4 py-3 text-left font-medium">
                  Category
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.map((incident) => (
                <tr
                  key={incident.id}
                  className="border-t border-border/70 transition-colors hover:bg-surface/60"
                >
                  <td className="px-3 py-3">
                    <input
                      type="checkbox"
                      aria-label={`Select ${incident.reference}`}
                      checked={selected.includes(incident.id)}
                      onChange={() =>
                        setSelected((prev) =>
                          prev.includes(incident.id)
                            ? prev.filter((id) => id !== incident.id)
                            : [...prev, incident.id],
                        )
                      }
                      className="size-4 accent-[var(--primary)]"
                    />
                  </td>
                  {onToggleStar && (
                    <td className="px-2 py-3">
                      <button
                        type="button"
                        onClick={() => onToggleStar(incident.id)}
                        aria-label={`Star ${incident.reference}`}
                        className="focus-ring rounded p-1 text-muted-foreground transition-colors hover:text-amber-400"
                      >
                        <Star
                          className={cn(
                            "size-4",
                            starredIds.includes(incident.id) && "fill-amber-400 text-amber-400",
                          )}
                        />
                      </button>
                    </td>
                  )}
                  <td className="px-4 py-3">
                    <Link
                      to="/incidents/$incidentId"
                      params={{ incidentId: incident.id }}
                      className="focus-ring block rounded"
                    >
                      <span className="font-mono text-xs text-muted-foreground">
                        {incident.reference}
                      </span>
                      <span className="block font-medium text-foreground hover:text-primary">
                        {incident.title}
                      </span>
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <SeverityChip severity={incident.severity} />
                  </td>
                  <td className="px-4 py-3">
                    <IncidentStatusChip status={incident.status} />
                  </td>
                  <td className="px-4 py-3">
                    <PriorityChip priority={incident.priority} />
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {incident.assignee?.displayName ?? "Unassigned"}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                    {formatDate(incident.createdAt)}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                    {formatDate(incident.updatedAt)}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold">{incident.riskScore}</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">
                    {incident.source.replace(/_/g, " ")}
                  </td>
                  <td className="px-4 py-3">
                    <CategoryChip category={incident.category} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {sorted.length > pageSize && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            {current * pageSize + 1}–{Math.min(sorted.length, (current + 1) * pageSize)} of{" "}
            {sorted.length}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={current === 0}
              onClick={() => setPage(current - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={current >= pageCount - 1}
              onClick={() => setPage(current + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function SortableHead({
  label,
  onClick,
  align = "left",
}: {
  label: string;
  onClick: () => void;
  align?: "left" | "right";
}) {
  return (
    <th
      scope="col"
      className={cn("px-4 py-3 font-medium", align === "right" ? "text-right" : "text-left")}
    >
      <button
        type="button"
        onClick={onClick}
        className="focus-ring rounded transition-colors hover:text-foreground"
      >
        {label}
      </button>
    </th>
  );
}
