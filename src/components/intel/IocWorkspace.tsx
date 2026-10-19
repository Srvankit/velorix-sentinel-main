import { Download, Upload } from "lucide-react";
import { useMemo, useState } from "react";

import { AwaitingFeed } from "./AwaitingFeed";
import { IntelSeverity } from "./IntelSeverity";
import { DataTable, type DataTableColumn } from "@/components/common";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { IOC_SEVERITIES, IOC_STATUSES, type IocCategory, type IocRecord } from "@/data/ioc-catalog";

interface IocWorkspaceProps {
  category: IocCategory;
  records: IocRecord[];
  isLoading: boolean;
  awaiting: boolean;
}

/** Premium indicator table for a single IOC class. */
export function IocWorkspace({ category, records, isLoading, awaiting }: IocWorkspaceProps) {
  const [severity, setSeverity] = useState<string>("all");
  const [status, setStatus] = useState<string>("all");

  const rows = useMemo(
    () =>
      records.filter(
        (row) =>
          (severity === "all" || row.severity === severity) &&
          (status === "all" || row.status === status),
      ),
    [records, severity, status],
  );

  const columns: Array<DataTableColumn<IocRecord>> = [
    {
      key: "value",
      header: "Indicator",
      cell: (row) => <span className="font-mono text-xs">{row.value}</span>,
      sortValue: (row) => row.value,
      sortable: true,
    },
    {
      key: "severity",
      header: "Severity",
      cell: (row) => <IntelSeverity severity={row.severity} />,
      sortValue: (row) => row.severity,
      sortable: true,
    },
    { key: "confidence", header: "Confidence", cell: (row) => row.confidence },
    { key: "status", header: "Status", cell: (row) => row.status },
    {
      key: "tags",
      header: "Tags",
      cell: (row) => (
        <div className="flex flex-wrap gap-1">
          {row.tags.map((tag) => (
            <span key={tag} className="rounded-full border border-border px-2 py-0.5 text-[11px]">
              {tag}
            </span>
          ))}
        </div>
      ),
    },
    { key: "source", header: "Source", cell: (row) => row.source },
    {
      key: "createdAt",
      header: "Created",
      cell: (row) => new Date(row.createdAt).toLocaleDateString(),
      sortValue: (row) => row.createdAt,
      sortable: true,
    },
    {
      key: "updatedAt",
      header: "Updated",
      cell: (row) => new Date(row.updatedAt).toLocaleDateString(),
      sortValue: (row) => row.updatedAt,
      sortable: true,
      align: "right",
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold">{category.plural}</h2>
          <p className="text-xs text-muted-foreground">{category.description}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled
            title="Available once the intelligence backend is connected"
          >
            <Upload className="size-3.5" aria-hidden="true" /> Import
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled
            title="Available once the intelligence backend is connected"
          >
            <Download className="size-3.5" aria-hidden="true" /> Export
          </Button>
        </div>
      </div>

      {awaiting && (
        <AwaitingFeed
          detail={`No ${category.plural.toLowerCase()} are stored yet. Expected format: ${category.example}`}
        />
      )}

      <DataTable
        rows={rows}
        columns={columns}
        rowKey={(row) => row.id}
        isLoading={isLoading}
        searchAccessor={(row) => `${row.value} ${row.source} ${row.tags.join(" ")}`}
        searchPlaceholder={`Search ${category.plural.toLowerCase()}…`}
        emptyTitle={`No ${category.plural.toLowerCase()} indexed`}
        emptyDescription="Awaiting live threat intelligence. Indicators will appear here once a source is connected."
        toolbar={
          <div className="flex flex-wrap items-center gap-2">
            <Select value={severity} onValueChange={setSeverity}>
              <SelectTrigger className="h-9 w-[150px]" aria-label="Filter by severity">
                <SelectValue placeholder="Severity" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All severities</SelectItem>
                {IOC_SEVERITIES.map((item) => (
                  <SelectItem key={item} value={item}>
                    {item}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={status} onValueChange={setStatus}>
              <SelectTrigger className="h-9 w-[150px]" aria-label="Filter by status">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All statuses</SelectItem>
                {IOC_STATUSES.map((item) => (
                  <SelectItem key={item} value={item}>
                    {item}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        }
      />
    </div>
  );
}
