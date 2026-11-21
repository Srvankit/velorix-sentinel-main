import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AnalysisOverviewPanel } from "@/components/analysis/AnalysisOverviewPanel";
import type { Analysis, AnalysisThreatSummary } from "@/types/analysis";

describe("AnalysisOverviewPanel component (Task 7.3 Component Tests)", () => {
  const mockCompletedAnalysis: Analysis = {
    id: "test-analysis-1",
    uploadedFileId: "file-uuid-1",
    originalFileName: "sample_traffic.pcap",
    status: "COMPLETED",
    stage: "COMPLETED",
    progressPercent: 100,
    startedAt: "2026-11-20T10:00:00Z",
    completedAt: "2026-11-20T10:00:05Z",
    duration: 4500,
    totalPackets: 15420,
    processedPackets: 15400,
    malformedPackets: 20,
    truncated: false,
    maliciousPackets: 8,
    safePackets: 15392,
    averagePacketSize: 512,
    uniqueSourceIps: 12,
    uniqueDestinationIps: 25,
    captureStartedAt: "2026-11-20T09:00:00Z",
    captureEndedAt: "2026-11-20T09:02:00Z",
    captureDuration: 120000,
    riskScore: 75,
    analysisVersion: "1.0",
    failureReason: null,
    createdAt: "2026-11-20T10:00:00Z",
    updatedAt: "2026-11-20T10:00:05Z",
  };

  const mockSummary: AnalysisThreatSummary = {
    id: "summary-uuid-1",
    analysisId: "test-analysis-1",
    totalThreats: 14,
    criticalThreats: 2,
    highThreats: 5,
    mediumThreats: 4,
    lowThreats: 3,
    riskScore: 75,
    overallStatus: "HIGH_RISK",
  };

  describe("Loading and error states", () => {
    it("renders skeleton stats when isLoading is true", () => {
      const { container } = render(
        <AnalysisOverviewPanel
          analysis={undefined}
          summary={undefined}
          isLoading={true}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      // SkeletonStat items render pulse animated elements
      const pulses = container.querySelectorAll(".animate-pulse");
      expect(pulses.length).toBeGreaterThan(0);
    });

    it("renders skeleton stats when analysis is undefined and not loading", () => {
      const { container } = render(
        <AnalysisOverviewPanel
          analysis={undefined}
          summary={undefined}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      const pulses = container.querySelectorAll(".animate-pulse");
      expect(pulses.length).toBeGreaterThan(0);
    });

    it("renders ErrorState and triggers onRetry when error exists", () => {
      const onRetry = vi.fn();
      render(
        <AnalysisOverviewPanel
          analysis={mockCompletedAnalysis}
          summary={mockSummary}
          isLoading={false}
          error={new Error("Backend service unavailable")}
          onRetry={onRetry}
        />,
      );

      expect(screen.getByText("Backend service unavailable")).toBeInTheDocument();
      const retryButton = screen.getByRole("button", { name: /try again/i });
      fireEvent.click(retryButton);
      expect(onRetry).toHaveBeenCalledTimes(1);
    });
  });

  describe("Completed analysis with threat summary", () => {
    it("renders packet metrics, frame sizes, durations, and addresses accurately", () => {
      render(
        <AnalysisOverviewPanel
          analysis={mockCompletedAnalysis}
          summary={mockSummary}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      // Total packets
      expect(screen.getByText("Total packets")).toBeInTheDocument();
      expect(screen.getByText("15,420")).toBeInTheDocument();
      expect(screen.getByText(/15,400 stored · 20 malformed/)).toBeInTheDocument();

      // Average frame size
      expect(screen.getByText("Average frame size")).toBeInTheDocument();
      expect(screen.getByText("512 B")).toBeInTheDocument();

      // Capture duration
      expect(screen.getByText("Capture duration")).toBeInTheDocument();
      expect(screen.getByText("2m 0s")).toBeInTheDocument();

      // Processing time
      expect(screen.getByText("Processing time")).toBeInTheDocument();
      expect(screen.getByText("4.50 s")).toBeInTheDocument();

      // Unique addresses
      expect(screen.getByText("Unique addresses")).toBeInTheDocument();
      expect(screen.getByText("37")).toBeInTheDocument(); // 12 sources + 25 destinations
      expect(screen.getByText(/12 sources · 25 destinations/)).toBeInTheDocument();

      // Malicious frames
      expect(screen.getByText("Malicious frames")).toBeInTheDocument();
      expect(screen.getByText("8")).toBeInTheDocument();
      expect(screen.getByText("15,392 clean frames")).toBeInTheDocument();

      // Risk score
      expect(screen.getByText("Risk score")).toBeInTheDocument();
      expect(screen.getByText("75/100")).toBeInTheDocument();
      expect(screen.getByText("high risk")).toBeInTheDocument();

      // Threats detected
      expect(screen.getByText("Threats detected")).toBeInTheDocument();
      expect(screen.getByText("14")).toBeInTheDocument();
      expect(screen.getByText("2 critical · 5 high")).toBeInTheDocument();

      // Threat breakdown tiles
      expect(screen.getByText("Critical")).toBeInTheDocument();
      expect(screen.getByText("2")).toBeInTheDocument();
      expect(screen.getByText("High")).toBeInTheDocument();
      expect(screen.getByText("5")).toBeInTheDocument();
      expect(screen.getByText("Medium")).toBeInTheDocument();
      expect(screen.getByText("4")).toBeInTheDocument();
      expect(screen.getByText("Low")).toBeInTheDocument();
      expect(screen.getByText("3")).toBeInTheDocument();
    });

    it("renders truncated capture warning when truncated is true", () => {
      const truncatedAnalysis: Analysis = {
        ...mockCompletedAnalysis,
        truncated: true,
      };

      render(
        <AnalysisOverviewPanel
          analysis={truncatedAnalysis}
          summary={mockSummary}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      const warning = screen.getByTestId("truncated-capture-warning");
      expect(warning).toBeInTheDocument();
      expect(warning).toHaveTextContent("Capture truncated");
    });

    it("does not render truncated capture warning when truncated is false", () => {
      render(
        <AnalysisOverviewPanel
          analysis={mockCompletedAnalysis}
          summary={mockSummary}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      expect(screen.queryByTestId("truncated-capture-warning")).not.toBeInTheDocument();
    });
  });

  describe("Lifecycle status variations (incomplete scans)", () => {
    it("renders QUEUED analysis with awaiting scan hints", () => {
      const queuedAnalysis: Analysis = {
        ...mockCompletedAnalysis,
        status: "QUEUED",
        maliciousPackets: 0,
        safePackets: 0,
      };

      render(
        <AnalysisOverviewPanel
          analysis={queuedAnalysis}
          summary={null}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      // Em-dashes for unscanned metrics
      const emDashes = screen.getAllByText("—");
      expect(emDashes.length).toBeGreaterThanOrEqual(3);

      expect(screen.getAllByText("Awaiting scan").length).toBeGreaterThanOrEqual(2);
      expect(screen.getByText("Verdict available once scan completes")).toBeInTheDocument();
    });

    it("renders PROCESSING analysis with scanning in progress hint", () => {
      const processingAnalysis: Analysis = {
        ...mockCompletedAnalysis,
        status: "PROCESSING",
      };

      render(
        <AnalysisOverviewPanel
          analysis={processingAnalysis}
          summary={null}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      expect(screen.getByText("Scanning in progress…")).toBeInTheDocument();
    });

    it("renders FAILED analysis with inspection failed hints", () => {
      const failedAnalysis: Analysis = {
        ...mockCompletedAnalysis,
        status: "FAILED",
      };

      render(
        <AnalysisOverviewPanel
          analysis={failedAnalysis}
          summary={null}
          isLoading={false}
          error={null}
          onRetry={vi.fn()}
        />,
      );

      const failedHints = screen.getAllByText("Inspection failed");
      expect(failedHints.length).toBeGreaterThanOrEqual(3);
    });
  });
});
