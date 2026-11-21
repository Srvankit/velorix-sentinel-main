import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RiskBadge } from "@/components/common/RiskBadge";

describe("RiskBadge component (Task 7.3 Component Tests)", () => {
  describe("Rendering across all risk states", () => {
    it("renders SECURE state with green success styling and score", () => {
      render(
        <RiskBadge
          score={0}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("0")).toBeInTheDocument();
      expect(screen.getByText("Secure")).toBeInTheDocument();
      const badge = screen.getByTitle("Risk score 0 of 100 (Secure)");
      expect(badge).toHaveClass("border-success/40", "text-success");
    });

    it("renders LOW_RISK state with info styling and score", () => {
      render(
        <RiskBadge
          score={15}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("15")).toBeInTheDocument();
      expect(screen.getByText("Low")).toBeInTheDocument();
      const badge = screen.getByTitle("Risk score 15 of 100 (Low)");
      expect(badge).toHaveClass("border-info/40", "text-info");
    });

    it("renders ELEVATED state with warning styling and score", () => {
      render(
        <RiskBadge
          score={35}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("35")).toBeInTheDocument();
      expect(screen.getByText("Elevated")).toBeInTheDocument();
      const badge = screen.getByTitle("Risk score 35 of 100 (Elevated)");
      expect(badge).toHaveClass("border-warning/40", "text-warning");
    });

    it("renders HIGH_RISK state with destructive styling and score", () => {
      render(
        <RiskBadge
          score={65}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("65")).toBeInTheDocument();
      expect(screen.getByText("High")).toBeInTheDocument();
      const badge = screen.getByTitle("Risk score 65 of 100 (High)");
      expect(badge).toHaveClass("border-destructive/40", "text-destructive");
    });

    it("renders CRITICAL state with critical styling and score", () => {
      render(
        <RiskBadge
          score={90}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("90")).toBeInTheDocument();
      expect(screen.getByText("Critical")).toBeInTheDocument();
      const badge = screen.getByTitle("Risk score 90 of 100 (Critical)");
      expect(badge).toHaveClass("border-critical/40", "text-critical");
    });

    it("renders QUEUED status with em-dash and Awaiting scan label", () => {
      render(<RiskBadge score={0} status="QUEUED" />);

      expect(screen.getByText("—")).toBeInTheDocument();
      expect(screen.getByText("Awaiting scan")).toBeInTheDocument();
      expect(screen.getByTitle("Risk status: Awaiting scan")).toBeInTheDocument();
    });

    it("renders PROCESSING status with em-dash and Scanning… label", () => {
      render(<RiskBadge score={0} status="PROCESSING" />);

      expect(screen.getByText("—")).toBeInTheDocument();
      expect(screen.getByText("Scanning…")).toBeInTheDocument();
      expect(screen.getByTitle("Risk status: Scanning…")).toBeInTheDocument();
    });

    it("renders FAILED status with em-dash and Failed label", () => {
      render(<RiskBadge score={0} status="FAILED" />);

      expect(screen.getByText("—")).toBeInTheDocument();
      expect(screen.getByText("Failed")).toBeInTheDocument();
      expect(screen.getByTitle("Risk status: Failed")).toBeInTheDocument();
    });
  });

  describe("Threat count overrides", () => {
    it("renders Critical badge when criticalThreats > 0 regardless of score", () => {
      render(
        <RiskBadge
          score={10}
          criticalThreats={1}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("10")).toBeInTheDocument();
      expect(screen.getByText("Critical")).toBeInTheDocument();
      expect(screen.getByTitle("Risk score 10 of 100 (Critical)")).toHaveClass("border-critical/40");
    });

    it("renders High badge when highThreats > 0 regardless of score", () => {
      render(
        <RiskBadge
          score={10}
          highThreats={3}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("10")).toBeInTheDocument();
      expect(screen.getByText("High")).toBeInTheDocument();
      expect(screen.getByTitle("Risk score 10 of 100 (High)")).toHaveClass("border-destructive/40");
    });
  });

  describe("Props and customization", () => {
    it("hides label span when showLabel is false", () => {
      render(
        <RiskBadge
          score={85}
          status="COMPLETED"
          isDetectionComplete={true}
          showLabel={false}
        />,
      );

      expect(screen.getByText("85")).toBeInTheDocument();
      expect(screen.queryByText("Critical")).not.toBeInTheDocument();
    });

    it("applies custom className", () => {
      render(
        <RiskBadge
          score={50}
          status="COMPLETED"
          isDetectionComplete={true}
          className="custom-test-class"
        />,
      );

      const badge = screen.getByTitle("Risk score 50 of 100 (High)");
      expect(badge).toHaveClass("custom-test-class");
    });

    it("clamps out-of-range scores", () => {
      render(
        <RiskBadge
          score={150}
          status="COMPLETED"
          isDetectionComplete={true}
        />,
      );

      expect(screen.getByText("100")).toBeInTheDocument();
      expect(screen.getByTitle("Risk score 100 of 100 (Critical)")).toBeInTheDocument();
    });
  });
});
