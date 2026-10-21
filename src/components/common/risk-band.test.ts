import { describe, expect, test } from "bun:test";
import { riskBand } from "./risk-band";

describe("Risk score synchronization and explicit scan states (Task 1.3)", () => {
  test("Check 1: Every integer score 0 through 100 maps to consistent classifications when detection is complete", () => {
    for (let score = 0; score <= 100; score++) {
      const result = riskBand(score, { status: "COMPLETED", isDetectionComplete: true });
      expect(result.isScanned).toBe(true);

      if (score === 0) {
        expect(result.classification).toBe("SECURE");
        expect(result.label).toBe("Secure");
      } else if (score < 25) {
        expect(result.classification).toBe("LOW_RISK");
        expect(result.label).toBe("Low");
      } else if (score < 50) {
        expect(result.classification).toBe("ELEVATED");
        expect(result.label).toBe("Elevated");
      } else if (score < 80) {
        expect(result.classification).toBe("HIGH_RISK");
        expect(result.label).toBe("High");
      } else {
        expect(result.classification).toBe("CRITICAL");
        expect(result.label).toBe("Critical");
      }
    }
  });

  test("Check 2: Scores 25, 50, and 80 produce matching boundary classifications", () => {
    // Boundary 25: 24 is Low, 25 is Elevated
    const score24 = riskBand(24);
    expect(score24.classification).toBe("LOW_RISK");
    expect(score24.label).toBe("Low");

    const score25 = riskBand(25);
    expect(score25.classification).toBe("ELEVATED");
    expect(score25.label).toBe("Elevated");

    // Boundary 50: 49 is Elevated, 50 is High
    const score49 = riskBand(49);
    expect(score49.classification).toBe("ELEVATED");
    expect(score49.label).toBe("Elevated");

    const score50 = riskBand(50);
    expect(score50.classification).toBe("HIGH_RISK");
    expect(score50.label).toBe("High");

    // Boundary 80: 79 is High, 80 is Critical
    const score79 = riskBand(79);
    expect(score79.classification).toBe("HIGH_RISK");
    expect(score79.label).toBe("High");

    const score80 = riskBand(80);
    expect(score80.classification).toBe("CRITICAL");
    expect(score80.label).toBe("Critical");
  });

  test("Check 3: A QUEUED analysis with score 0 is not shown as SECURE", () => {
    const result = riskBand(0, { status: "QUEUED" });
    expect(result.classification).toBe("AWAITING_SCAN");
    expect(result.label).toBe("Awaiting scan");
    expect(result.isScanned).toBe(false);
    expect(result.label).not.toBe("Secure");
  });

  test("Check 4: A PROCESSING analysis with score 0 is not shown as SECURE", () => {
    const result = riskBand(0, { status: "PROCESSING" });
    expect(result.classification).toBe("PROCESSING");
    expect(result.label).toBe("Scanning…");
    expect(result.isScanned).toBe(false);
    expect(result.label).not.toBe("Secure");
  });

  test("Check 5: A FAILED analysis is not shown as SECURE", () => {
    const result = riskBand(0, { status: "FAILED" });
    expect(result.classification).toBe("FAILED");
    expect(result.label).toBe("Failed");
    expect(result.isScanned).toBe(false);
    expect(result.label).not.toBe("Secure");
  });

  test("Check 6: A COMPLETED analysis with successful detection, score 0, and no findings is shown as SECURE", () => {
    const result = riskBand(0, {
      status: "COMPLETED",
      isDetectionComplete: true,
      criticalThreats: 0,
      highThreats: 0,
    });
    expect(result.classification).toBe("SECURE");
    expect(result.label).toBe("Secure");
    expect(result.isScanned).toBe(true);
  });

  test("Check 6b: A COMPLETED analysis where detection has NOT executed is not shown as SECURE", () => {
    const result = riskBand(0, {
      status: "COMPLETED",
      isDetectionComplete: false,
    });
    expect(result.classification).toBe("AWAITING_SCAN");
    expect(result.label).toBe("Awaiting scan");
    expect(result.isScanned).toBe(false);
  });

  test("Check 7: Critical or high-severity findings cannot be hidden by an inconsistent risk score", () => {
    // Critical threat override even with score 0
    const criticalWithZero = riskBand(0, { criticalThreats: 1 });
    expect(criticalWithZero.classification).toBe("CRITICAL");
    expect(criticalWithZero.label).toBe("Critical");

    // Critical threat override with low score
    const criticalWithLow = riskBand(15, { criticalThreats: 2 });
    expect(criticalWithLow.classification).toBe("CRITICAL");
    expect(criticalWithLow.label).toBe("Critical");

    // High threat override even with score 0
    const highWithZero = riskBand(0, { highThreats: 1 });
    expect(highWithZero.classification).toBe("HIGH_RISK");
    expect(highWithZero.label).toBe("High");

    // High threat override with low score
    const highWithLow = riskBand(20, { highThreats: 3 });
    expect(highWithLow.classification).toBe("HIGH_RISK");
    expect(highWithLow.label).toBe("High");

    // Critical takes precedence over high
    const mixed = riskBand(10, { criticalThreats: 1, highThreats: 5 });
    expect(mixed.classification).toBe("CRITICAL");
    expect(mixed.label).toBe("Critical");
  });
});
