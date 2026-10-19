import { ChevronLeft, ChevronRight, Search, ShieldAlert } from "lucide-react";

import { EmptyState, ErrorState, SkeletonTable } from "@/components/common";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import type { NetworkProtocol, Packet, PacketQuery, Protocol } from "@/types/analysis";
import { NETWORK_PROTOCOLS, PROTOCOLS } from "@/types/analysis";
import { formatEndpoint } from "@/utils/analysisFormat";
import { formatBytes, formatNumber } from "@/utils/format";

interface PacketTableProps {
  packets: Packet[];
  totalElements: number;
  totalPages: number;
  query: PacketQuery;
  onQueryChange: (patch: Partial<PacketQuery>) => void;
  isLoading: boolean;
  isFetching: boolean;
  error: unknown;
  onRetry: () => void;
  selectedId: string | null;
  onSelect: (packet: Packet) => void;
}

const SUSPICIOUS_OPTIONS = [
  { value: "ALL", label: "All frames" },
  { value: "true", label: "Suspicious only" },
  { value: "false", label: "Clean only" },
] as const;

function packetTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return `${date.toLocaleTimeString("en-US", { hour12: false })}.${String(date.getMilliseconds()).padStart(3, "0")}`;
}

/**
 * Enterprise frame listing: server-side pagination, protocol/verdict filtering,
 * free-text search and row selection driving the detail drawer.
 */
export function PacketTable({
  packets,
  totalElements,
  totalPages,
  query,
  onQueryChange,
  isLoading,
  isFetching,
  error,
  onRetry,
  selectedId,
  onSelect,
}: PacketTableProps) {
  const page = query.page ?? 0;

  if (error) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-56 flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <input
            type="search"
            value={query.search ?? ""}
            onChange={(event) => onQueryChange({ search: event.target.value, page: 0 })}
            placeholder="Search address or description…"
            aria-label="Search packets"
            className="focus-ring h-9 w-full rounded-lg border border-input bg-surface pl-9 text-sm placeholder:text-muted-foreground"
          />
        </div>

        <Select
          value={query.protocol ?? "ALL"}
          onValueChange={(value) =>
            onQueryChange({ protocol: value === "ALL" ? undefined : (value as Protocol), page: 0 })
          }
        >
          <SelectTrigger className="h-9 w-40" aria-label="Filter by protocol">
            <SelectValue placeholder="Protocol" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All protocols</SelectItem>
            {PROTOCOLS.map((protocol) => (
              <SelectItem key={protocol} value={protocol}>
                {protocol}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={query.networkProtocol ?? "ALL"}
          onValueChange={(value) =>
            onQueryChange({
              networkProtocol: value === "ALL" ? undefined : (value as NetworkProtocol),
              page: 0,
            })
          }
        >
          <SelectTrigger className="h-9 w-36" aria-label="Filter by network layer">
            <SelectValue placeholder="Layer 3" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All layers</SelectItem>
            {NETWORK_PROTOCOLS.map((item) => (
              <SelectItem key={item} value={item}>
                {item}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={query.suspicious === undefined ? "ALL" : String(query.suspicious)}
          onValueChange={(value) =>
            onQueryChange({ suspicious: value === "ALL" ? undefined : value === "true", page: 0 })
          }
        >
          <SelectTrigger className="h-9 w-40" aria-label="Filter by verdict">
            <SelectValue placeholder="Verdict" />
          </SelectTrigger>
          <SelectContent>
            {SUSPICIOUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <SkeletonTable rows={10} />
      ) : packets.length === 0 ? (
        <EmptyState
          title="No frames match these filters"
          description="Relax the protocol or verdict filter, or clear the search term."
        />
      ) : (
        <div
          className={cn(
            "max-h-[32rem] overflow-auto rounded-2xl border border-border transition-opacity",
            isFetching && "opacity-60",
          )}
        >
          <Table>
            <TableHeader className="sticky top-0 z-10 bg-surface">
              <TableRow className="hover:bg-transparent">
                <TableHead className="w-20 text-xs uppercase">#</TableHead>
                <TableHead className="text-xs uppercase">Time</TableHead>
                <TableHead className="text-xs uppercase">Source</TableHead>
                <TableHead className="text-xs uppercase">Destination</TableHead>
                <TableHead className="text-xs uppercase">Protocol</TableHead>
                <TableHead className="text-xs uppercase">Length</TableHead>
                <TableHead className="text-xs uppercase">Info</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {packets.map((packet) => (
                <TableRow
                  key={packet.id}
                  onClick={() => onSelect(packet)}
                  aria-selected={selectedId === packet.id}
                  className={cn(
                    "cursor-pointer",
                    selectedId === packet.id && "bg-primary/10",
                    packet.suspicious && "text-destructive",
                  )}
                >
                  <TableCell className="font-mono text-xs tabular-nums">
                    {formatNumber(packet.packetNumber)}
                  </TableCell>
                  <TableCell className="font-mono text-xs whitespace-nowrap">
                    {packetTime(packet.timestamp)}
                  </TableCell>
                  <TableCell className="font-mono text-xs whitespace-nowrap">
                    {formatEndpoint(packet.sourceIp, packet.sourcePort)}
                  </TableCell>
                  <TableCell className="font-mono text-xs whitespace-nowrap">
                    {formatEndpoint(packet.destinationIp, packet.destinationPort)}
                  </TableCell>
                  <TableCell className="text-xs font-medium">{packet.protocol}</TableCell>
                  <TableCell className="text-xs tabular-nums">
                    {formatBytes(packet.packetLength, 0)}
                  </TableCell>
                  <TableCell className="max-w-[24rem] truncate text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-1.5">
                      {packet.suspicious && (
                        <ShieldAlert className="size-3.5 text-destructive" aria-hidden="true" />
                      )}
                      {packet.info ?? `${packet.protocol} frame`}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground">
        <span>
          {formatNumber(totalElements)} frames · page {page + 1} of {Math.max(1, totalPages)}
        </span>
        <div className="flex items-center gap-2">
          <Select
            value={String(query.size ?? 50)}
            onValueChange={(value) => onQueryChange({ size: Number(value), page: 0 })}
          >
            <SelectTrigger className="h-8 w-28" aria-label="Rows per page">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[25, 50, 100, 200].map((size) => (
                <SelectItem key={size} value={String(size)}>
                  {size} rows
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => onQueryChange({ page: page - 1 })}
          >
            <ChevronLeft className="size-4" aria-hidden="true" />
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => onQueryChange({ page: page + 1 })}
          >
            Next
            <ChevronRight className="size-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </div>
  );
}
