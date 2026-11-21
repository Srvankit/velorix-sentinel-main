import { describe, expect, it } from "vitest";
import { riskBand } from "@/components/common/risk-band";

describe("riskBand utility (Task 7.3 Unit Tests)", () => {
  describe("Scanned integer score mapping (0 to 100)", () => {
    it("maps 0 to SECURE with success styling when detection is complete", () => {
      const result = riskBand(0, { status: "COMPLETED", isDetectionComplete: true });
      expect(result.classification).toBe("SECURE");
      expect(result.label).toBe("Secure");
      expect(result.className).toBe("border-success/40 bg-success/15 text-success");
      expect(result.isScanned).toBe(true);
    });

    it("maps scores 1 through 24 to LOW_RISK with info styling", () => {
      for (const score of [1, 5, 12, 24]) {
        const result = riskBand(score, { status: "COMPLETED", isDetectionComplete: true });
        expect(result.classification).toBe("LOW_RISK");
        expect(result.label).toBe("Low");
        expect(result.className).toBe("border-info/40 bg-info/15 text-info");
        expect(result.isScanned).toBe(true);
      }
    });

    it("maps scores 25 through 49 to ELEVATED with warning styling", () => {
      for (const score of [25, 30, 40, 49]) {
        const result = riskBand(score, { status: "COMPLETED", isDetectionComplete: true });
        expect(result.classification).toBe("ELEVATED");
        expect(result.label).toBe("Elevated");
        expect(result.className).toBe("border-warning/40 bg-warning/15 text-warning");
        expect(result.isScanned).toBe(true);
      }
    });

    it("maps scores 50 through 79 to HIGH_RISK with destructive styling", () => {
      for (const score of [50, 60, 70, 79]) {
        const result = riskBand(score, { status: "COMPLETED", isDetectionComplete: true });
        expect(result.classification).toBe("HIGH_RISK");
        expect(result.label).toBe("High");
        expect(result.className).toBe("border-destructive/40 bg-destructive/15 text-destructive");
        expect(result.isScanned).toBe(true);
      }
    });

    it("maps scores 80 through 100 to CRITICAL with critical styling", () => {
      for (const score of [80, 85, 95, 100]) {
        const result = riskBand(score, { status: "COMPLETED", isDetectionComplete: true });
        expect(result.classification).toBe("CRITICAL");
        expect(result.label).toBe("Critical");
        expect(result.className).toBe("border-critical/40 bg-critical/15 text-critical");
        expect(result.isScanned).toBe(true);
      }
    });
  });

  describe("Boundary thresholds", () => {
    it("differentiates 24 (Low) and 25 (Elevated)", () => {
      expect(riskBand(24).classification).toBe("LOW_RISK");
      expect(riskBand(25).classification).toBe("ELEVATED");
    });

    it("differentiates 49 (Elevated) and 50 (High)", () => {
      expect(riskBand(49).classification).toBe("ELEVATED");
      expect(riskBand(50).classification).toBe("HIGH_RISK");
    });

    it("differentiates 79 (High) and 80 (Critical)", () => {
      expect(riskBand(79).classification).toBe("HIGH_RISK");
      expect(riskBand(80).classification).toBe("CRITICAL");
    });
  });

  describe("Score clamping and decimal rounding", () => {
    it("clamps negative scores to 0", () => {
      const result = riskBand(-10, { status: "COMPLETED", isDetectionComplete: true });
      expect(result.classification).toBe("SECURE");
      expect(result.label).toBe("Secure");
    });

    it("clamps scores exceeding 100 to 100", () => {
      const result = riskBand(150, { status: "COMPLETED", isDetectionComplete: true });
      expect(result.classification).toBe("CRITICAL");
      expect(result.label).toBe("Critical");
    });

    it("rounds decimal scores to nearest integer", () => {
      expect(riskBand(24.4).classification).toBe("LOW_RISK");
      expect(riskBand(24.6).classification).toBe("ELEVATED");
      expect(riskBand(49.4).classification).toBe("ELEVATED");
      expect(riskBand(49.5).classification).toBe("HIGH_RISK");
      expect(riskBand(79.4).classification).toBe("HIGH_RISK");
      expect(riskBand(79.5).classification).toBe("CRITICAL");
    });
  });

  describe("Lifecycle and non-completed scanning states", () => {
    it("returns FAILED state with destructive styling when status is FAILED", () => {
      const result = riskBand(0, { status: "FAILED" });
      expect(result.classification).toBe("FAILED");
      expect(result.label).toBe("Failed");
      expect(result.className).toBe("border-destructive/40 bg-destructive/15 text-destructive");
      expect(result.isScanned).toBe(false);
    });

    it("returns PROCESSING state with warning styling when status is PROCESSING", () => {
      const result = riskBand(0, { status: "PROCESSING" });
      expect(result.classification).toBe("PROCESSING");
      expect(result.label).toBe("Scanning…");
      expect(result.className).toBe("border-warning/40 bg-warning/15 text-warning");
      expect(result.isScanned).toBe(false);
    });

    it("returns AWAITING_SCAN state when status is QUEUED", () => {
      const result = riskBand(0, { status: "QUEUED" });
      expect(result.classification).toBe("AWAITING_SCAN");
      expect(result.label).toBe("Awaiting scan");
      expect(result.className).toBe("border-info/40 bg-info/15 text-info");
      expect(result.isScanned).toBe(false);
    });

    it("returns AWAITING_SCAN state when status is UPLOADED", () => {
      const result = riskBand(0, { status: "UPLOADED" });
      expect(result.classification).toBe("AWAITING_SCAN");
      expect(result.label).toBe("Awaiting scan");
      expect(result.isScanned).toBe(false);
    });

    it("returns AWAITING_SCAN when isDetectionComplete is explicitly false", () => {
      const result = riskBand(0, { status: "COMPLETED", isDetectionComplete: false });
      expect(result.classification).toBe("AWAITING_SCAN");
      expect(result.label).toBe("Awaiting scan");
      expect(result.isScanned).toBe(false);
    });
  });

  describe("Threat and overallStatus overrides", () => {
    it("forces CRITICAL when criticalThreats > 0 regardless of base score", () => {
      const result = riskBand(0, {
        status: "COMPLETED",
        isDetectionComplete: true,
        criticalThreats: 1,
      });
      expect(result.classification).toBe("CRITICAL");
      expect(result.label).toBe("Critical");
      expect(result.className).toBe("border-critical/40 bg-critical/15 text-critical");
    });

    it("forces HIGH_RISK when highThreats > 0 regardless of base score", () => {
      const result = riskBand(10, {
        status: "COMPLETED",
        isDetectionComplete: true,
        highThreats: 2,
      });
      expect(result.classification).toBe("HIGH_RISK");
      expect(result.label).toBe("High");
      expect(result.className).toBe("border-destructive/40 bg-destructive/15 text-destructive");
    });

    it("criticalThreats takes precedence over highThreats", () => {
      const result = riskBand(10, {
        status: "COMPLETED",
        isDetectionComplete: true,
        criticalThreats: 1,
        highThreats: 5,
      });
      expect(result.classification).toBe("CRITICAL");
    });

    it("respects overallStatus strings", () => {
      expect(riskBand(0, { overallStatus: "CRITICAL" }).classification).toBe("CRITICAL");
      expect(riskBand(0, { overallStatus: "HIGH_RISK" }).classification).toBe("HIGH_RISK");
      expect(riskBand(0, { overallStatus: "ELEVATED" }).classification).toBe("ELEVATED");
      expect(riskBand(0, { overallStatus: "LOW_RISK" }).classification).toBe("LOW_RISK");
    });
  });
});
