import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { RefreshCw, Search, UploadCloud } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { ProtectedRoute } from "@/components/auth";
import { EmptyState, ErrorState, PageHeader, SkeletonTable } from "@/components/common";
import { CaptureTable, UploadDropzone, UploadQueue } from "@/components/upload";
import type { SortDirection, SortField } from "@/components/upload/CaptureTable";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useStartInspection } from "@/hooks/useAnalysis";
import { useUploadQueue } from "@/hooks/useUploadQueue";
import { DashboardLayout } from "@/layouts";
import { fileService } from "@/services/fileService";
import { toApiError } from "@/services/apiClient";
import type { ApiError } from "@/types/api";
import { UPLOAD_STATUS_LABELS, type UploadStatus, type UploadedFile } from "@/types/file";
import { APP_NAME } from "@/utils/constants";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/upload")({
  component: UploadPage,
  head: () => ({
    meta: [
      { title: `Capture uploads · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Securely upload PCAP and PCAPNG network captures to Velorix Sentinel for deep packet inspection.",
      },
      { property: "og:title", content: `Capture uploads · ${APP_NAME}` },
      {
        property: "og:description",
        content: "Enterprise capture ingestion: validated, checksummed and scoped to your account.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

const PAGE_SIZE = 20;
const STATUS_OPTIONS: Array<UploadStatus | "ALL"> = [
  "ALL",
  "READY_FOR_ANALYSIS",
  "UPLOADED",
  "VALIDATING",
  "FAILED",
];

function UploadPage() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <CaptureWorkspace />
      </DashboardLayout>
    </ProtectedRoute>
  );
}

function CaptureWorkspace() {
  const [files, setFiles] = useState<UploadedFile[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [status, setStatus] = useState<UploadStatus | "ALL">("ALL");
  const [sort, setSort] = useState<SortField>("createdAt");
  const [direction, setDirection] = useState<SortDirection>("DESC");
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const navigate = useNavigate();
  const [analyzingId, setAnalyzingId] = useState<string | null>(null);
  const startInspection = useStartInspection();

  /** Queues a deep packet inspection run and routes the analyst to the workspace. */
  const handleAnalyze = useCallback(
    (file: UploadedFile) => {
      setAnalyzingId(file.id);
      startInspection.mutate(file.id, {
        onSuccess: () => {
          toast.success("Inspection started", {
            description: `${file.originalFileName} is being decoded. Follow the progress in Analysis.`,
          });
          void navigate({ to: "/analysis" });
        },
        onError: (error) => {
          toast.error("Could not start the inspection", { description: error.message });
        },
        onSettled: () => setAnalyzingId(null),
      });
    },
    [navigate, startInspection],
  );

  const [refreshing, setRefreshing] = useState(false);
  const requestId = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const load = useCallback(
    async (options: { silent?: boolean } = {}) => {
      const current = ++requestId.current;
      if (options.silent) setRefreshing(true);
      else setLoading(true);

      try {
        const page = await fileService.list({
          status: status === "ALL" ? undefined : status,
          search: debouncedSearch,
          page: 0,
          size: PAGE_SIZE,
          sort,
          direction,
        });
        if (current !== requestId.current) return;
        setFiles(page.content);
        setTotal(page.totalElements);
        setError(null);
      } catch (caught) {
        if (current !== requestId.current) return;
        setError(toApiError(caught));
      } finally {
        if (current === requestId.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [debouncedSearch, direction, sort, status],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const handleUploaded = useCallback(() => {
    toast.success("Capture uploaded", {
      description: "The file passed validation and is ready for analysis.",
    });
    void load({ silent: true });
  }, [load]);

  const { items, enqueue, retry, dismiss, clearCompleted } = useUploadQueue(handleUploaded);

  const handleSortChange = (field: SortField) => {
    if (field === sort) {
      setDirection((current) => (current === "ASC" ? "DESC" : "ASC"));
    } else {
      setSort(field);
      setDirection("DESC");
    }
  };

  const handleDelete = async (file: UploadedFile) => {
    setDeletingId(file.id);
    try {
      await fileService.remove(file.id);
      toast.success("Capture deleted", { description: file.originalFileName });
      await load({ silent: true });
    } catch (caught) {
      toast.error("Delete failed", { description: toApiError(caught).message });
    } finally {
      setDeletingId(null);
    }
  };

  const uploading = items.some((item) => item.state === "uploading" || item.state === "pending");

  return (
    <div className="space-y-8">
      <PageHeader
        title="Capture uploads"
        description="Upload PCAP and PCAPNG traffic captures for deep packet inspection. Every file is validated, checksummed and stored against your account only."
        actions={
          <Button
            variant="outline"
            onClick={() => void load({ silent: true })}
            disabled={refreshing || loading}
          >
            <RefreshCw className={cn("size-4", refreshing && "animate-spin")} aria-hidden="true" />
            Refresh
          </Button>
        }
      />

      <UploadDropzone onFiles={enqueue} />

      <UploadQueue
        items={items}
        onRetry={retry}
        onDismiss={dismiss}
        onClearCompleted={clearCompleted}
      />

      <section className="space-y-4" aria-label="Stored captures">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-lg font-semibold">
            Stored captures{" "}
            <span className="text-sm font-normal text-muted-foreground">({total})</span>
          </h2>

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <div className="relative">
              <Search
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
                aria-hidden="true"
              />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search captures…"
                aria-label="Search captures by file name"
                className="focus-ring h-9 w-full rounded-lg border border-input bg-surface pr-3 pl-9 text-sm placeholder:text-muted-foreground sm:w-64"
              />
            </div>

            <Select
              value={status}
              onValueChange={(value) => setStatus(value as UploadStatus | "ALL")}
            >
              <SelectTrigger className="h-9 w-full sm:w-48" aria-label="Filter by status">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option === "ALL" ? "All statuses" : UPLOAD_STATUS_LABELS[option]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {loading ? (
          <SkeletonTable rows={5} />
        ) : error ? (
          <ErrorState
            title="Captures could not be loaded"
            error={error}
            onRetry={() => void load()}
          />
        ) : files.length === 0 ? (
          <EmptyState
            icon={UploadCloud}
            title={
              debouncedSearch || status !== "ALL"
                ? "No captures match your filters"
                : "No captures uploaded yet"
            }
            description={
              debouncedSearch || status !== "ALL"
                ? "Adjust the search term or status filter to widen the results."
                : "Drop a .pcap or .pcapng file above to add your first capture."
            }
          />
        ) : (
          <CaptureTable
            files={files}
            sort={sort}
            direction={direction}
            onSortChange={handleSortChange}
            onDelete={(file) => void handleDelete(file)}
            deletingId={deletingId}
            onAnalyze={handleAnalyze}
            analyzingId={startInspection.isPending ? analyzingId : null}
          />
        )}

        {uploading && (
          <p className="text-xs text-muted-foreground">
            Uploads in progress — the list refreshes automatically as each capture completes.
          </p>
        )}
      </section>
    </div>
  );
}
