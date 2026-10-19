import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { UPLOAD_STATUS_LABELS, type UploadStatus } from "@/types/file";

const STATUS_STYLES: Record<UploadStatus, string> = {
  UPLOADED: "border-info/40 bg-info/10 text-info",
  VALIDATING: "border-warning/40 bg-warning/10 text-warning",
  READY_FOR_ANALYSIS: "border-success/40 bg-success/10 text-success",
  FAILED: "border-destructive/40 bg-destructive/10 text-destructive",
  DELETED: "border-border bg-muted text-muted-foreground",
};

/** Colour-coded capture lifecycle indicator. */
export function UploadStatusBadge({ status }: { status: UploadStatus }) {
  return (
    <Badge variant="outline" className={cn("font-medium", STATUS_STYLES[status])}>
      {UPLOAD_STATUS_LABELS[status]}
    </Badge>
  );
}
