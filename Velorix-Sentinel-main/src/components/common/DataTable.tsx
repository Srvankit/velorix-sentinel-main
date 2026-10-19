import { ChevronDown, ChevronUp, ChevronsUpDown, Search } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";

import { EmptyState } from "./EmptyState";
import { SkeletonTable } from "./Skeletons";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export interface DataTableColumn<T> {
  /** Stable key, also used as the sort key when `sortable` is set. */
  key: string;
  header: string;
  /** Cell renderer. */
  cell: (row: T) => ReactNode;
  /** Comparable value used for sorting; required for sortable columns. */
  sortValue?: (row: T) => string | number;
  align?: "left" | "right";
  className?: string;
  sortable?: boolean;
}

interface DataTableProps<T> {
  rows: T[];
  columns: Array<DataTableColumn<T>>;
  rowKey: (row: T) => string;
  isLoading?: boolean;
  /** Free-text search: return the searchable haystack for a row. */
  searchAccessor?: (row: T) => string;
  searchPlaceholder?: string;
  /** Optional filter controls rendered next to the search box. */
  toolbar?: ReactNode;
  pageSize?: number;
  emptyTitle?: string;
  emptyDescription?: string;
  onRowClick?: (row: T) => void;
  className?: string;
}

type SortState = { key: string; direction: "asc" | "desc" } | null;

/**
 * Enterprise table primitive: client-side search, sorting and pagination over a
 * page of rows already fetched from the API. Presentation only — no data fetching,
 * so every module reuses it with its own service.
 */
export function DataTable<T>({
  rows,
  columns,
  rowKey,
  isLoading = false,
  searchAccessor,
  searchPlaceholder = "Search…",
  toolbar,
  pageSize = 10,
  emptyTitle = "Nothing to show yet",
  emptyDescription,
  onRowClick,
  className,
}: DataTableProps<T>) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState>(null);
  const [page, setPage] = useState(0);

  const filtered = useMemo(() => {
    if (!searchAccessor || !query.trim()) return rows;
    const needle = query.trim().toLowerCase();
    return rows.filter((row) => searchAccessor(row).toLowerCase().includes(needle));
  }, [rows, query, searchAccessor]);

  const sorted = useMemo(() => {
    if (!sort) return filtered;
    const column = columns.find((item) => item.key === sort.key);
    if (!column?.sortValue) return filtered;
    const factor = sort.direction === "asc" ? 1 : -1;
    return [...filtered].sort((a, b) => {
      const left = column.sortValue!(a);
      const right = column.sortValue!(b);
      if (typeof left === "number" && typeof right === "number") return (left - right) * factor;
      return String(left).localeCompare(String(right)) * factor;
    });
  }, [filtered, sort, columns]);

  const pageCount = Math.max(1, Math.ceil(sorted.length / pageSize));
  const current = Math.min(page, pageCount - 1);
  const visible = sorted.slice(current * pageSize, current * pageSize + pageSize);

  function toggleSort(key: string) {
    setPage(0);
    setSort((previous) =>
      previous?.key === key
        ? { key, direction: previous.direction === "asc" ? "desc" : "asc" }
        : { key, direction: "desc" },
    );
  }

  if (isLoading) {
    return <SkeletonTable rows={pageSize} />;
  }

  return (
    <div className={cn("space-y-4", className)}>
      {(searchAccessor || toolbar) && (
        <div className="flex flex-wrap items-center gap-2">
          {searchAccessor && (
            <div className="relative min-w-52 flex-1">
              <Search
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
                aria-hidden="true"
              />
              <input
                type="search"
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setPage(0);
                }}
                placeholder={searchPlaceholder}
                aria-label={searchPlaceholder}
                className="focus-ring h-9 w-full rounded-lg border border-input bg-surface pl-9 text-sm placeholder:text-muted-foreground"
              />
            </div>
          )}
          {toolbar}
        </div>
      )}

      {visible.length === 0 ? (
        <EmptyState title={emptyTitle} description={emptyDescription} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface/60">
              <tr>
                {columns.map((column) => {
                  const active = sort?.key === column.key;
                  const Icon = !active
                    ? ChevronsUpDown
                    : sort.direction === "asc"
                      ? ChevronUp
                      : ChevronDown;
                  return (
                    <th
                      key={column.key}
                      scope="col"
                      className={cn(
                        "px-4 py-3 text-xs font-medium tracking-wider text-muted-foreground uppercase",
                        column.align === "right" ? "text-right" : "text-left",
                        column.className,
                      )}
                    >
                      {column.sortable && column.sortValue ? (
                        <button
                          type="button"
                          onClick={() => toggleSort(column.key)}
                          className="focus-ring inline-flex items-center gap-1 rounded transition-colors hover:text-foreground"
                          aria-label={`Sort by ${column.header}`}
                        >
                          {column.header}
                          <Icon className="size-3" aria-hidden="true" />
                        </button>
                      ) : (
                        column.header
                      )}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {visible.map((row) => (
                <tr
                  key={rowKey(row)}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  className={cn(
                    "border-t border-border/70 transition-colors",
                    onRowClick && "cursor-pointer hover:bg-surface/60",
                  )}
                >
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={cn(
                        "px-4 py-3 align-middle",
                        column.align === "right" ? "text-right" : "text-left",
                      )}
                    >
                      {column.cell(row)}
                    </td>
                  ))}
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
