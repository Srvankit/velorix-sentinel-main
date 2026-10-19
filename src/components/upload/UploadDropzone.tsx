import { UploadCloud } from "lucide-react";
import { useCallback, useRef, useState, type DragEvent } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ACCEPTED_EXTENSIONS, MAX_UPLOAD_BYTES } from "@/types/file";
import { formatBytes } from "@/utils/format";

interface UploadDropzoneProps {
  onFiles: (files: File[]) => void;
  disabled?: boolean;
}

/** Large enterprise drag-and-drop target with an accessible browse fallback. */
export function UploadDropzone({ onFiles, disabled = false }: UploadDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setDragging(false);
      if (disabled) return;
      const files = Array.from(event.dataTransfer.files ?? []);
      if (files.length > 0) onFiles(files);
    },
    [disabled, onFiles],
  );

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="Upload capture files"
      aria-disabled={disabled}
      onClick={() => !disabled && inputRef.current?.click()}
      onKeyDown={(event) => {
        if (disabled) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          inputRef.current?.click();
        }
      }}
      onDragOver={(event) => {
        event.preventDefault();
        if (!disabled) setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      className={cn(
        "focus-ring relative grid cursor-pointer place-items-center gap-4 overflow-hidden rounded-2xl",
        "border-2 border-dashed border-border bg-surface/40 px-6 py-14 text-center transition-colors",
        "hover:border-primary/60 hover:bg-primary/5",
        dragging && "border-primary bg-primary/10",
        disabled && "pointer-events-none opacity-60",
      )}
    >
      <span
        aria-hidden="true"
        className="grid size-14 place-items-center rounded-2xl bg-gradient-brand text-primary-foreground shadow-lg"
      >
        <UploadCloud className="size-6" />
      </span>

      <div className="space-y-1">
        <p className="text-base font-semibold">Drop packet captures here</p>
        <p className="text-sm text-muted-foreground">
          {ACCEPTED_EXTENSIONS.join(" and ")} files up to {formatBytes(MAX_UPLOAD_BYTES, 0)} each.
          Captures are encrypted in transit and scoped to your account.
        </p>
      </div>

      <Button
        type="button"
        variant="outline"
        disabled={disabled}
        onClick={(event) => {
          event.stopPropagation();
          inputRef.current?.click();
        }}
      >
        Browse files
      </Button>

      <input
        ref={inputRef}
        type="file"
        multiple
        className="sr-only"
        accept={ACCEPTED_EXTENSIONS.join(",")}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          if (files.length > 0) onFiles(files);
          event.target.value = "";
        }}
      />
    </div>
  );
}
