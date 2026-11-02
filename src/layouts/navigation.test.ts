import { describe, expect, test } from "bun:test";
import fs from "node:fs";
import path from "node:path";

describe("Route Shell Gating & Navigation Filtering (Task 5.2)", () => {
  const layoutsDir = path.resolve(__dirname);
  const routesDir = path.resolve(__dirname, "../routes");
  const commonDir = path.resolve(__dirname, "../components/common");
  const servicesDir = path.resolve(__dirname, "../services");

  const navigationContent = fs.readFileSync(path.join(layoutsDir, "navigation.ts"), "utf-8");

  describe("Navigation Registry Filtering", () => {
    test("sidebar navigation defines the 8 functional core modules", () => {
      // Functional modules required by Task 5.2
      const requiredItems = [
        { label: "Dashboard", to: "/dashboard" },
        { label: "Upload", to: "/upload" },
        { label: "Analysis", to: "/analysis" },
        { label: "Threats", to: "/threats" },
        { label: "Threat Intelligence", to: "/intel" },
        { label: "Knowledge", to: "/knowledge" },
        { label: "Profile", to: "/profile" },
        { label: "Settings", to: "/settings" },
      ];

      for (const item of requiredItems) {
        expect(navigationContent).toContain(`label: "${item.label}"`);
        expect(navigationContent).toContain(`to: "${item.to}"`);
      }
    });

    test("unbuilt modules are removed from the active navigation array", () => {
      // Find the declaration of `export const navigation: NavSection[] = [...]`
      const navArrayMatch = navigationContent.match(/export const navigation:\s*NavSection\[\]\s*=\s*\[([\s\S]*?)\];/);
      expect(navArrayMatch).not.toBeNull();
      const activeNavBlock = navArrayMatch![1];

      // These unbuilt routes must NOT appear in the active navigation array
      const unbuiltRoutes = [
        'to: "/incidents"',
        'to: "/soc"',
        'to: "/assistant"',
        'to: "/reports"',
        'to: "/history"',
        'to: "/notifications"',
        'to: "/admin"',
        'to: "/ops"',
      ];

      for (const unbuilt of unbuiltRoutes) {
        expect(activeNavBlock.includes(unbuilt)).toBe(false);
      }
    });

    test("deferred navigation items are preserved in registry for Phase 2", () => {
      expect(navigationContent).toContain("deferredNavigationItems");
      expect(navigationContent).toContain('to: "/incidents"');
      expect(navigationContent).toContain('to: "/soc"');
      expect(navigationContent).toContain('to: "/assistant"');
      expect(navigationContent).toContain('to: "/reports"');
      expect(navigationContent).toContain('to: "/admin"');
      expect(navigationContent).toContain('to: "/ops"');
    });

    test("NavItem supports optional badge property", () => {
      expect(navigationContent).toContain("badge?: string;");
    });
  });

  describe("Deferred Route Shell Gating", () => {
    test("backend readiness flags remain false in services", () => {
      const incidentService = fs.readFileSync(path.join(servicesDir, "incidentService.ts"), "utf-8");
      expect(incidentService).toContain("export const INCIDENT_BACKEND_READY = false;");

      const socService = fs.readFileSync(path.join(servicesDir, "socService.ts"), "utf-8");
      expect(socService).toContain("export const SOC_BACKEND_READY = false;");

      const obsService = fs.readFileSync(path.join(servicesDir, "observabilityService.ts"), "utf-8");
      expect(obsService).toContain("export const OBSERVABILITY_BACKEND_READY = false;");

      const govService = fs.readFileSync(path.join(servicesDir, "governanceService.ts"), "utf-8");
      expect(govService).toContain("export const GOVERNANCE_BACKEND_READY = false;");

      const adminService = fs.readFileSync(path.join(servicesDir, "adminService.ts"), "utf-8");
      expect(adminService).toContain("export const ADMIN_BACKEND_READY = false;");
    });

    test("incidents.tsx gates when backend is not ready and uses ModulePlaceholder", () => {
      const content = fs.readFileSync(path.join(routesDir, "incidents.tsx"), "utf-8");
      expect(content).toContain("INCIDENT_BACKEND_READY");
      expect(content).toContain("ModulePlaceholder");
      expect(content).toContain("Incident Response & Case Management");
    });

    test("soc.tsx gates when backend is not ready and uses ModulePlaceholder", () => {
      const content = fs.readFileSync(path.join(routesDir, "soc.tsx"), "utf-8");
      expect(content).toContain("SOC_BACKEND_READY");
      expect(content).toContain("ModulePlaceholder");
      expect(content).toContain("SOC Analyst Workspace");
    });

    test("ops.tsx gates when backend is not ready and uses ModulePlaceholder", () => {
      const content = fs.readFileSync(path.join(routesDir, "ops.tsx"), "utf-8");
      expect(content).toContain("OBSERVABILITY_BACKEND_READY");
      expect(content).toContain("ModulePlaceholder");
      expect(content).toContain("Platform Observability");
    });

    test("reports.tsx gates when backend is not ready and uses ModulePlaceholder", () => {
      const content = fs.readFileSync(path.join(routesDir, "reports.tsx"), "utf-8");
      expect(content).toContain("GOVERNANCE_BACKEND_READY");
      expect(content).toContain("ModulePlaceholder");
      expect(content).toContain("Executive Reporting & Compliance");
    });

    test("admin.tsx gates when backend is not ready and uses ModulePlaceholder", () => {
      const content = fs.readFileSync(path.join(routesDir, "admin.tsx"), "utf-8");
      expect(content).toContain("ADMIN_BACKEND_READY");
      expect(content).toContain("ModulePlaceholder");
      expect(content).toContain("Enterprise Administration Center");
    });

    test("history.tsx and notifications.tsx use ModulePlaceholder", () => {
      const historyContent = fs.readFileSync(path.join(routesDir, "history.tsx"), "utf-8");
      expect(historyContent).toContain("ModulePlaceholder");

      const notifContent = fs.readFileSync(path.join(routesDir, "notifications.tsx"), "utf-8");
      expect(notifContent).toContain("ModulePlaceholder");
    });
  });

  describe("ModulePlaceholder Content and Recovery Navigation", () => {
    test("ModulePlaceholder component source includes dashboard recovery links", () => {
      const placeholderContent = fs.readFileSync(
        path.join(commonDir, "ModulePlaceholder.tsx"),
        "utf-8",
      );

      // Explains preview/roadmap state
      expect(placeholderContent).toContain("Preview");
      expect(placeholderContent).toContain("Scheduled for");
      expect(placeholderContent).toContain("under active development");

      // Links back to Analysis Dashboard
      expect(placeholderContent).toContain('to="/dashboard"');
      expect(placeholderContent).toContain("Analysis Dashboard");

      // Alternative quick links
      expect(placeholderContent).toContain('to="/upload"');
      expect(placeholderContent).toContain('to="/threats"');

      // Planned capabilities checklist support
      expect(placeholderContent).toContain("plannedFeatures");
    });
  });
});
