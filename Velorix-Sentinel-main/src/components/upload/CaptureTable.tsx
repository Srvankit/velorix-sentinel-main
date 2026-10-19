import { ArrowDown, ArrowUp, FileArchive, Radar, Trash2 } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import type { UploadedFile } from "@/types/file";
import { formatBytes, formatDateTime } from "@/utils/format";
import { UploadStatusBadge } from "./UploadStatusBadge";

export type SortField = "originalFileName" | "fileSize" | "createdAt" | "uploadStatus";
export type SortDirection = "ASC" | "DESC";

interface CaptureTableProps {
  files: UploadedFile[];
  sort: SortField;
  direction: SortDirection;
  onSortChange: (field: SortField) => void;
  onDelete: (file: UploadedFile) => void;
  deletingId: string | null;
  /** Queues a deep packet inspection run for the capture. */
  onAnalyze: (file: UploadedFile) => void;
  analyzingId: string | null;
}

const COLUMNS: Array<{ field: SortField; label: string; className?: string }> = [
  { field: "originalFileName", label: "Capture" },
  { field: "fileSize", label: "Size" },
  { field: "uploadStatus", label: "Status" },
  { field: "createdAt", label: "Uploaded" },
];

/** Sortable listing of the caller's stored captures. */
export function CaptureTable({
  files,
  sort,
  direction,
  onSortChange,
  onDelete,
  deletingId,
  onAnalyze,
  analyzingId,
}: CaptureTableProps) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-border">
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            {COLUMNS.map((column) => (
              <TableHead key={column.field} className={column.className}>
                <button
                  type="button"
                  onClick={() => onSortChange(column.field)}
                  aria-label={`Sort by ${column.label}`}
                  className={cn(
                    "focus-ring inline-flex items-center gap-1 rounded text-xs font-medium tracking-wide uppercase",
                    sort === column.field ? "text-foreground" : "text-muted-foreground",
                  )}
                >
                  {column.label}
                  {sort === column.field &&
                    (direction === "ASC" ? (
                      <ArrowUp className="size-3" aria-hidden="true" />
                    ) : (
                      <ArrowDown className="size-3" aria-hidden="true" />
                    ))}
                </button>
              </TableHead>
            ))}
            <TableHead className="text-right text-xs uppercase">Actions</TableHead>
          </TableRow>
        </TableHeader>

        <TableBody>
          {files.map((file) => (
            <TableRow key={file.id}>
              <TableCell className="max-w-[22rem]">
                <div className="flex items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground"
                  >
                    <FileArchive className="size-4" />
                  </span>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{file.originalFileName}</p>
                    <p className="truncate text-xs text-muted-foreground">{file.fileType}</p>
                  </div>
                </div>
              </TableCell>
              <TableCell className="text-sm whitespace-nowrap">
                {formatBytes(file.fileSize)}
              </TableCell>
              <TableCell>
                <UploadStatusBadge status={file.uploadStatus} />
              </TableCell>
              <TableCell className="text-sm whitespace-nowrap text-muted-foreground">
                {formatDateTime(file.createdAt)}
              </TableCell>
              <TableCell className="text-right">
                <Button
                  variant="ghost"
                  size="sm"
                  className="mr-1 text-muted-foreground hover:text-primary"
                  disabled={analyzingId === file.id || file.uploadStatus === "FAILED"}
                  onClick={() => onAnalyze(file)}
                >
                  <Radar className="size-4" aria-hidden="true" />
                  Analyze
                </Button>
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Delete ${file.originalFileName}`}
                      disabled={deletingId === file.id}
                      className="text-muted-foreground hover:text-destructive"
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Delete this capture?</AlertDialogTitle>
                      <AlertDialogDescription>
                        “{file.originalFileName}” will be removed from storage permanently. Any
                        future analysis of this capture will no longer be possible.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Cancel</AlertDialogCancel>
                      <AlertDialogAction
                        onClick={() => onDelete(file)}
                        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                      >
                        Delete capture
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
