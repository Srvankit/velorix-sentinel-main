import { expect, test } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const cleanCapturePath = path.resolve(__dirname, "../fixtures/clean_traffic.pcap");
const portScanCapturePath = path.resolve(__dirname, "../fixtures/syn_port_scan.pcap");

test.describe("Velorix Sentinel - Real Backend Full-Stack Integration", () => {
  test("Complete primary analyst journey across React, Spring Boot, and PostgreSQL", async ({
    page,
    request,
  }) => {
    page.on("console", (msg) => {
      if (msg.type() === "error") {
        console.log(`[Browser Console Error] ${msg.text()}`);
      }
    });
    page.on("pageerror", (err) => {
      console.log(`[Browser Page Error] ${err.stack || err.message}`);
    });
    // Generate unique credentials for isolated, repeatable execution
    const runId = `${Date.now()}.${Math.floor(Math.random() * 10_000)}`;
    const testEmail = `analyst.real.${runId}@velorix.test`;
    const testPassword = "Velorix!Secure123";
    const firstName = "E2E";
    const lastName = "Analyst";

    // ------------------------------------------------------------------------
    // Step 1 & 2: User Registration via Real Backend
    // ------------------------------------------------------------------------
    await page.goto("/register", { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle");
    await expect(page).toHaveTitle(/Create your account|Velorix Sentinel/i);

    // Wait for client hydration before filling
    await expect(async () => {
      await page.locator("#firstName").fill(firstName);
      await page.locator("#lastName").fill(lastName);
      await page.locator("#email").fill(testEmail);
      await page.locator("#password").fill(testPassword);
      await page.locator("#confirmPassword").fill(testPassword);
      await expect(page.locator("#email")).toHaveValue(testEmail, { timeout: 1000 });
      await expect(page.locator("#confirmPassword")).toHaveValue(testPassword, { timeout: 1000 });
    }).toPass({ timeout: 20_000 });

    const termsCheckbox = page.locator("#acceptTerms");
    await expect(async () => {
      await termsCheckbox.click();
      await expect(termsCheckbox).toHaveAttribute("data-state", "checked", { timeout: 1000 });
    }).toPass({ timeout: 10_000 });

    const submitBtn = page.locator('button[type="submit"]');
    await submitBtn.click();

    // Verify redirection to /verify-email upon successful registration
    await page.waitForURL(/\/verify-email/i, { timeout: 20_000 });
    await expect(page.getByRole("heading", { name: /Check your inbox/i })).toBeVisible({ timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 3: Real Email Verification Simulation via Test Helper
    // ------------------------------------------------------------------------
    // Request the verification token issued in PostgreSQL for this real account
    const tokenRes = await request.post(
      `http://127.0.0.1:8080/api/v1/auth/test/verification-token?email=${encodeURIComponent(testEmail)}`,
    );
    expect(tokenRes.ok()).toBeTruthy();
    const tokenJson = (await tokenRes.json()) as {
      success: boolean;
      data: { token: string; email: string };
    };
    expect(tokenJson.success).toBe(true);
    expect(tokenJson.data?.token).toBeTruthy();
    const verificationToken = tokenJson.data.token;

    // Visit verification URL with actual token
    await page.goto(`/verify-email?token=${encodeURIComponent(verificationToken)}`, {
      waitUntil: "domcontentloaded",
    });

    // Verify UI reflects real backend activation
    await expect(page.getByRole("heading", { name: /Your email is verified/i })).toBeVisible({
      timeout: 15_000,
    });

    // ------------------------------------------------------------------------
    // Step 4: Real Authentication via Spring Boot /api/v1/auth/login
    // ------------------------------------------------------------------------
    await page.getByRole("link", { name: /Continue to sign in/i }).click();
    await page.waitForURL(/\/login/i, { timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    const loginEmailInput = page.locator("#email");
    await expect(async () => {
      await loginEmailInput.fill(testEmail);
      await page.locator("#password").fill(testPassword);
      await expect(loginEmailInput).toHaveValue(testEmail, { timeout: 1000 });
      await expect(page.locator("#password")).toHaveValue(testPassword, { timeout: 1000 });
    }).toPass({ timeout: 20_000 });

    const signInBtn = page.locator('button[type="submit"]');
    await signInBtn.click();

    // Assert redirection to dashboard
    await page.waitForURL(/\/dashboard/i, { timeout: 20_000 });
    await expect(page.getByRole("heading", { name: /Security operations center/i })).toBeVisible({
      timeout: 20_000,
    });

    // ------------------------------------------------------------------------
    // Step 5: Authenticated Session Verification
    // ------------------------------------------------------------------------
    const storedSession = await page.evaluate(() => {
      return window.localStorage.getItem("velorix.session");
    });
    expect(storedSession).toBeTruthy();
    const sessionObj = JSON.parse(storedSession!);
    expect(sessionObj.accessToken).toBeTruthy();
    expect(sessionObj.user).toBeTruthy();
    expect(sessionObj.user.email).toBe(testEmail.toLowerCase());

    // ------------------------------------------------------------------------
    // Step 6: Benign PCAP Upload (clean_traffic.pcap)
    // ------------------------------------------------------------------------
    await page.getByRole("link", { name: "Upload", exact: true }).click();
    await page.waitForURL(/\/upload/i, { timeout: 15_000 });
    await expect(page.getByText(/Drop packet captures here|Capture uploads/i).first()).toBeVisible();

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(cleanCapturePath);

    // Wait for clean_traffic.pcap row in the stored captures table
    const cleanRow = page.locator("tr", { hasText: /clean_traffic\.pcap/i });
    await expect(cleanRow).toBeVisible({ timeout: 20_000 });

    // Click Analyze button
    const analyzeCleanBtn = cleanRow.locator("button", { hasText: /Analyze/i });
    await expect(analyzeCleanBtn).toBeVisible({ timeout: 10_000 });
    await analyzeCleanBtn.click();

    // ------------------------------------------------------------------------
    // Step 7 & 8: Real Pipeline Inspection & Clean Result Verification
    // ------------------------------------------------------------------------
    await page.waitForURL(/\/analysis/i, { timeout: 15_000 });
    await expect(page.getByText(/clean_traffic\.pcap/i).first()).toBeVisible({ timeout: 15_000 });

    // Wait for the asynchronous analysis pipeline to complete
    await expect(page.getByText(/Completed/i).first()).toBeVisible({ timeout: 30_000 });
    // Verify benign status: Secure verdict / 0 risk score
    await expect(page.getByText(/Secure|0\/100/i).first()).toBeVisible({ timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 9: Attack PCAP Upload (syn_port_scan.pcap)
    // ------------------------------------------------------------------------
    await page.getByRole("link", { name: "Upload", exact: true }).click();
    await page.waitForURL(/\/upload/i, { timeout: 15_000 });

    await fileInput.setInputFiles(portScanCapturePath);

    const portScanRow = page.locator("tr", { hasText: /syn_port_scan\.pcap/i });
    await expect(portScanRow).toBeVisible({ timeout: 20_000 });

    const analyzeAttackBtn = portScanRow.locator("button", { hasText: /Analyze/i });
    await expect(analyzeAttackBtn).toBeVisible({ timeout: 10_000 });
    await analyzeAttackBtn.click();

    // ------------------------------------------------------------------------
    // Step 10 & 11: Real Detection & Threat Findings Verification
    // ------------------------------------------------------------------------
    await page.waitForURL(/\/analysis/i, { timeout: 15_000 });
    await expect(page.getByText(/syn_port_scan\.pcap/i).first()).toBeVisible({ timeout: 15_000 });

    // Wait for the port-scan analysis pipeline to finish
    await expect(page.getByText(/Completed/i).first()).toBeVisible({ timeout: 30_000 });

    // Navigate to /threats to verify real detection findings persisted in database
    await page.getByRole("link", { name: "Threats", exact: true }).click();
    await page.waitForURL(/\/threats/i, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Threat detection/i })).toBeVisible({ timeout: 15_000 });

    // Look for threat row produced by detection engine (PORT_SCAN or SYN_SCAN)
    const threatRow = page.locator("tr", { hasText: /Port scan|SYN.*scan|PORT_SCAN|SYN_SCAN/i }).first();
    await expect(threatRow).toBeVisible({ timeout: 20_000 });

    // Click finding row to open threat drawer
    await threatRow.click();

    // Verify Threat Details drawer displays MITRE ATT&CK T1046 mapping
    await expect(page.getByText(/T1046/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Network Service Discovery/i).first()).toBeVisible({ timeout: 15_000 });

    // Close the drawer so it doesn't block navigation clicks
    await page.keyboard.press("Escape");
    await expect(page.getByText(/Network Service Discovery/i)).not.toBeVisible({ timeout: 10_000 });

    // ------------------------------------------------------------------------
    // Step 12 & 13: Real Dashboard & Chart Verification
    // ------------------------------------------------------------------------
    await page.getByRole("link", { name: "Dashboard", exact: true }).click();
    await page.waitForURL(/\/dashboard/i, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: /Security operations center/i })).toBeVisible({
      timeout: 15_000,
    });

    // Verify Recharts SVG charts render from actual backend API responses
    await expect(page.locator(".recharts-surface").first()).toBeVisible({ timeout: 15_000 });

    // ------------------------------------------------------------------------
    // Step 14, 15 & 16: Real Logout & Route Guard Verification
    // ------------------------------------------------------------------------
    const signOutBtn = page.getByLabel("Sign out");
    await expect(signOutBtn).toBeVisible({ timeout: 10_000 });
    await signOutBtn.click();

    await page.waitForURL(/\/login/i, { timeout: 15_000 });

    // Verify session storage was wiped
    const postLogoutSession = await page.evaluate(() => {
      return window.localStorage.getItem("velorix.session");
    });
    expect(postLogoutSession).toBeNull();

    // Verify route guard rejects unauthenticated direct access
    await page.goto("/dashboard", { waitUntil: "domcontentloaded" });
    await page.waitForURL(/\/login/i, { timeout: 15_000 });
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  });
});
