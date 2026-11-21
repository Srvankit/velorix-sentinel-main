import { test, expect } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { setupMockBackend } from "../support/mock-backend";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test.describe("Velorix Sentinel — Critical Path E2E Smoke Test", () => {
  const cleanCapturePath = path.resolve(__dirname, "../fixtures/clean_traffic.pcap");
  const portScanCapturePath = path.resolve(__dirname, "../fixtures/port_scan.pcap");

  test.beforeEach(async ({ page }) => {
    // When PLAYWRIGHT_USE_LIVE_API is not set, use stateful mock backend
    if (!process.env.PLAYWRIGHT_USE_LIVE_API) {
      await setupMockBackend(page);
    }
  });

  test("should complete primary user journey: register, verify, login, upload PCAP, verify threats, inspect dashboard, and logout", async ({
    page,
  }) => {
    const timestamp = Date.now();
    const testEmail = `e2e.analyst.${timestamp}@velorix.test`;
    const testPassword = "Password123!";

    // ------------------------------------------------------------------------
    // Step 1: User Registration
    // ------------------------------------------------------------------------
    await page.goto("/register");
    await page.waitForLoadState("networkidle");
    await expect(page).toHaveTitle(/Create your account|Velorix Sentinel/i);

    // Wait for client hydration before filling
    const firstNameInput = page.locator("#firstName");
    await expect(async () => {
      await firstNameInput.fill("Alex");
      await expect(firstNameInput).toHaveValue("Alex", { timeout: 1000 });
    }).toPass({ timeout: 15_000 });

    await page.locator("#lastName").fill("Mercer");
    await page.locator("#email").fill(testEmail);
    await page.locator("#password").fill(testPassword);
    await page.locator("#confirmPassword").fill(testPassword);

    // Accept Terms of Service (wait for hydration via toPass)
    const termsCheckbox = page.locator("#acceptTerms");
    await expect(async () => {
      await termsCheckbox.click();
      await expect(termsCheckbox).toHaveAttribute("data-state", "checked", { timeout: 1000 });
    }).toPass({ timeout: 15_000 });

    // Submit registration
    const submitBtn = page.locator('button[type="submit"]');
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();

    // Assert navigation to verify-email route
    await page.waitForURL(/\/verify-email/i, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Check your inbox/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(testEmail)).toBeVisible();

    // ------------------------------------------------------------------------
    // Step 2: Email Verification Simulation
    // ------------------------------------------------------------------------
    // Simulate clicking the email verification link
    const verificationUrl = `/verify-email?token=simulated-token-${timestamp}`;
    await page.goto(verificationUrl);
    await page.waitForLoadState("networkidle");

    // Assert verification completed view
    await expect(page.getByRole("heading", { name: /Your email is verified/i })).toBeVisible({
      timeout: 15_000,
    });
    const continueToSignInBtn = page.getByRole("link", { name: /Continue to sign in|Go to sign in/i });
    await expect(continueToSignInBtn).toBeVisible();

    // Click continue to sign in
    await continueToSignInBtn.click();
    await page.waitForURL(/\/login/i, { timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 3: User Login
    // ------------------------------------------------------------------------
    await page.waitForLoadState("networkidle");
    const loginEmailInput = page.locator("#email");
    await expect(async () => {
      await loginEmailInput.fill(testEmail);
      await expect(loginEmailInput).toHaveValue(testEmail, { timeout: 1000 });
    }).toPass({ timeout: 15_000 });

    await page.locator("#password").fill(testPassword);

    const signInBtn = page.locator('button[type="submit"]');
    await signInBtn.click();

    // Assert redirection to dashboard
    await page.waitForURL(/\/dashboard/i, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Security operations center/i })).toBeVisible({ timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 4: JWT Token Storage & Authenticated Session Verification
    // ------------------------------------------------------------------------
    const storedSession = await page.evaluate(() => {
      return window.localStorage.getItem("velorix.session");
    });
    expect(storedSession).toBeTruthy();
    const sessionObj = JSON.parse(storedSession!);
    expect(sessionObj.accessToken).toBeTruthy();
    expect(sessionObj.user).toBeTruthy();
    expect(sessionObj.user.email).toBe(testEmail);

    // ------------------------------------------------------------------------
    // Step 5: Benign PCAP Upload (clean_traffic.pcap)
    // ------------------------------------------------------------------------
    await page.getByRole("link", { name: "Upload", exact: true }).click();
    await page.waitForURL(/\/upload/i, { timeout: 15_000 });
    await expect(page.getByText(/Drop packet captures here|Capture uploads/i).first()).toBeVisible();

    // Upload clean_traffic.pcap via file input
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(cleanCapturePath);

    // Wait for the capture to appear in the table
    const cleanRow = page.locator("tr", { hasText: /clean_traffic\.pcap/i });
    await expect(cleanRow).toBeVisible({ timeout: 15_000 });

    // Click "Analyze" button on clean_traffic row
    const analyzeCleanBtn = cleanRow.locator("button", { hasText: /Analyze/i });
    await expect(analyzeCleanBtn).toBeVisible();
    await analyzeCleanBtn.click();

    // Assert navigation to analysis view
    await page.waitForURL(/\/analysis/i, { timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 6: Inspection & Detection Completion for Benign Capture
    // ------------------------------------------------------------------------
    // Wait for inspection run to show completed status
    await expect(page.getByText(/clean_traffic\.pcap/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Completed/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Secure|0\/100/i).first()).toBeVisible({ timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 7: Port Scan PCAP Upload & Threat Findings Verification
    // ------------------------------------------------------------------------
    await page.getByRole("link", { name: "Upload", exact: true }).click();
    await page.waitForURL(/\/upload/i, { timeout: 15_000 });
    await expect(page.getByText(/Drop packet captures here|Capture uploads/i).first()).toBeVisible();

    // Upload port_scan.pcap via file input
    await fileInput.setInputFiles(portScanCapturePath);

    // Wait for port scan row in table
    const portScanRow = page.locator("tr", { hasText: /port_scan\.pcap|syn_port_scan\.pcap/i });
    await expect(portScanRow).toBeVisible({ timeout: 15_000 });

    // Click "Analyze" button on port scan row
    const analyzePortScanBtn = portScanRow.locator("button", { hasText: /Analyze/i });
    await expect(analyzePortScanBtn).toBeVisible();
    await analyzePortScanBtn.click();

    // Assert navigation to analysis view
    await page.waitForURL(/\/analysis/i, { timeout: 15_000 });
    await expect(page.getByText(/Completed/i).first()).toBeVisible({ timeout: 15_000 });

    // Navigate to Threats view via navigation link
    await page.getByRole("link", { name: "Threats", exact: true }).click();
    await page.waitForURL(/\/threats/i, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Threat detection/i })).toBeVisible({ timeout: 15_000 });

    // Verify Port Scan threat finding in table
    const portScanThreat = page.locator("tr", { hasText: /Port Scan|SYN/i }).first();
    await expect(portScanThreat).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/HIGH/i).first()).toBeVisible();

    // Click finding row to open details drawer
    await portScanThreat.click();

    // Verify Threat Detail Drawer displays detection information
    const drawer = page.locator('[role="dialog"]');
    await expect(drawer).toBeVisible({ timeout: 10_000 });
    await expect(drawer.getByText(/Port Scan|Network Service Discovery|T1046/i).first()).toBeVisible();

    // Close drawer using Escape
    await page.keyboard.press("Escape");
    await expect(drawer).not.toBeVisible();

    // ------------------------------------------------------------------------
    // Step 8: Dashboard Chart & SOC Overview Verification
    // ------------------------------------------------------------------------
    await page.goto("/dashboard", { waitUntil: "domcontentloaded" });
    await page.waitForURL(/\/dashboard/i, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Security operations center/i })).toBeVisible({ timeout: 15_000 });

    // Verify Executive Overview KPI cards & security posture
    await expect(page.getByRole("heading", { name: /Executive security overview/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Threats detected/i).first()).toBeVisible({ timeout: 15_000 });

    // Verify Dashboard Charts rendered (Threat trend, Protocol distribution, Packet volume)
    await expect(page.getByRole("heading", { name: /Threat trend/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Protocol distribution/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Packet volume timeline/i })).toBeVisible({ timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 9: User Logout & Route Guard Verification
    // ------------------------------------------------------------------------
    const signOutBtn = page.getByLabel("Sign out");
    await expect(signOutBtn).toBeVisible({ timeout: 15_000 });
    await signOutBtn.click();

    // Assert redirection to login
    await page.waitForURL(/\/login/i, { timeout: 15_000 });
    await expect(page.locator("#email")).toBeVisible({ timeout: 15_000 });

    // Verify session was removed from localStorage
    const clearedSession = await page.evaluate(() => {
      return window.localStorage.getItem("velorix.session");
    });
    expect(clearedSession).toBeNull();

    // Verify route guard: visiting protected /dashboard redirects to /login
    await page.goto("/dashboard", { waitUntil: "domcontentloaded" });
    await page.waitForURL(/\/login/i, { timeout: 15_000 });
    await expect(page.locator("#email")).toBeVisible({ timeout: 15_000 });
  });
});
