import { useCallback, useRef, useState } from "react";

import { fileService } from "@/services/fileService";
import { toApiError } from "@/services/apiClient";
import type { UploadedFile } from "@/types/file";
import { validateCaptureFile } from "@/utils/uploadValidation";

export type QueueItemState = "pending" | "uploading" | "success" | "error";

export interface UploadQueueItem {
  id: string;
  file: File;
  state: QueueItemState;
  progress: number;
  error?: string;
  result?: UploadedFile;
}

let sequence = 0;
const nextId = () => `upload-${Date.now()}-${(sequence += 1)}`;

/**
 * Sequential upload queue with per-file progress, validation and retry.
 *
 * Uploads run one at a time so a batch of large captures cannot saturate the
 * connection or the backend's multipart buffers.
 */
export function useUploadQueue(onUploaded: () => void) {
  const [items, setItems] = useState<UploadQueueItem[]>([]);
  const running = useRef(false);
  const queue = useRef<string[]>([]);
  const itemsRef = useRef<UploadQueueItem[]>([]);

  const update = useCallback((id: string, patch: Partial<UploadQueueItem>) => {
    setItems((current) => {
      const next = current.map((item) => (item.id === id ? { ...item, ...patch } : item));
      itemsRef.current = next;
      return next;
    });
  }, []);

  const drain = useCallback(async () => {
    if (running.current) return;
    running.current = true;

    try {
      while (queue.current.length > 0) {
        const id = queue.current.shift();
        if (!id) continue;
        const item = itemsRef.current.find((candidate) => candidate.id === id);
        if (!item) continue;

        update(id, { state: "uploading", progress: 0, error: undefined });
        try {
          const result = await fileService.upload(item.file, {
            onProgress: (progress) => update(id, { progress }),
          });
          update(id, { state: "success", progress: 100, result });
          onUploaded();
        } catch (error) {
          update(id, { state: "error", error: toApiError(error).message });
        }
      }
    } finally {
      running.current = false;
    }
  }, [onUploaded, update]);

  const enqueue = useCallback(
    (files: File[]) => {
      if (files.length === 0) return;

      const created: UploadQueueItem[] = files.map((file) => {
        const validationError = validateCaptureFile(file);
        return {
          id: nextId(),
          file,
          state: validationError ? "error" : "pending",
          progress: 0,
          error: validationError ?? undefined,
        };
      });

      setItems((current) => {
        const next = [...created, ...current];
        itemsRef.current = next;
        return next;
      });

      queue.current.push(...created.filter((item) => item.state === "pending").map((i) => i.id));
      void drain();
    },
    [drain],
  );

  const retry = useCallback(
    (id: string) => {
      update(id, { state: "pending", progress: 0, error: undefined });
      const item = itemsRef.current.find((candidate) => candidate.id === id);
      if (item && validateCaptureFile(item.file)) {
        update(id, { state: "error", error: validateCaptureFile(item.file) ?? undefined });
        return;
      }
      queue.current.push(id);
      void drain();
    },
    [drain, update],
  );

  const dismiss = useCallback((id: string) => {
    queue.current = queue.current.filter((queued) => queued !== id);
    setItems((current) => {
      const next = current.filter((item) => item.id !== id);
      itemsRef.current = next;
      return next;
    });
  }, []);

  const clearCompleted = useCallback(() => {
    setItems((current) => {
      const next = current.filter((item) => item.state !== "success");
      itemsRef.current = next;
      return next;
    });
  }, []);

  return { items, enqueue, retry, dismiss, clearCompleted };
}
