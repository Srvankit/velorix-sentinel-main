import { ACCEPTED_EXTENSIONS, MAX_UPLOAD_BYTES } from "@/types/file";
import { formatBytes } from "./format";

/**
 * Client-side pre-flight mirroring the backend upload gate. It exists purely to give
 * instant feedback — the backend re-validates every byte and remains authoritative.
 */
export function validateCaptureFile(file: File): string | null {
  const name = file.name.toLowerCase();
  const hasAcceptedExtension = ACCEPTED_EXTENSIONS.some((extension) => name.endsWith(extension));

  if (!hasAcceptedExtension) {
    return `Only ${ACCEPTED_EXTENSIONS.join(" and ")} captures are supported.`;
  }
  if (file.size <= 0) {
    return "This file is empty.";
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `The capture is ${formatBytes(file.size)}. The maximum allowed size is ${formatBytes(
      MAX_UPLOAD_BYTES,
    )}.`;
  }
  return null;
}
