import { describe, expect, test } from "bun:test";
import { formatBitrate, formatDuration, formatEndpoint, isTruncatedCaptureWarningVisible } from "./analysisFormat";

describe("Truncated Capture Warning Visibility (Task 1.4)", () => {
  test("Warning is visible when capture is completed and marked truncated", () => {
    expect(isTruncatedCaptureWarningVisible("COMPLETED", true)).toBe(true);
  });

  test("Warning is NOT visible for ordinary complete capture", () => {
    expect(isTruncatedCaptureWarningVisible("COMPLETED", false)).toBe(false);
  });

  test("Warning is NOT visible when capture is still queued or processing", () => {
    expect(isTruncatedCaptureWarningVisible("QUEUED", true)).toBe(false);
    expect(isTruncatedCaptureWarningVisible("QUEUED", false)).toBe(false);
    expect(isTruncatedCaptureWarningVisible("PROCESSING", true)).toBe(false);
    expect(isTruncatedCaptureWarningVisible("PROCESSING", false)).toBe(false);
  });

  test("Warning is NOT visible when capture has failed", () => {
    expect(isTruncatedCaptureWarningVisible("FAILED", true)).toBe(false);
    expect(isTruncatedCaptureWarningVisible("FAILED", false)).toBe(false);
  });

  test("Warning handles undefined status or truncated flags gracefully", () => {
    expect(isTruncatedCaptureWarningVisible(undefined, true)).toBe(false);
    expect(isTruncatedCaptureWarningVisible("COMPLETED", undefined)).toBe(false);
    expect(isTruncatedCaptureWarningVisible(undefined, undefined)).toBe(false);
  });
});

describe("analysisFormat utility functions", () => {
  test("formatDuration formats milliseconds correctly", () => {
    expect(formatDuration(null)).toBe("—");
    expect(formatDuration(undefined)).toBe("—");
    expect(formatDuration(-10)).toBe("—");
    expect(formatDuration(450)).toBe("450 ms");
    expect(formatDuration(1500)).toBe("1.50 s");
    expect(formatDuration(65000)).toBe("1m 5s");
    expect(formatDuration(3665000)).toBe("1h 1m");
  });

  test("formatBitrate formats bps correctly", () => {
    expect(formatBitrate(0)).toBe("0 bps");
    expect(formatBitrate(500)).toBe("500 bps");
    expect(formatBitrate(1500)).toBe("1.50 Kbps");
  });

  test("formatEndpoint formats ip and port correctly", () => {
    expect(formatEndpoint("192.168.1.1", 8080)).toBe("192.168.1.1:8080");
    expect(formatEndpoint("10.0.0.1", null)).toBe("10.0.0.1");
    expect(formatEndpoint("10.0.0.1", undefined)).toBe("10.0.0.1");
  });
});
