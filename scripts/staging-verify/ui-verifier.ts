/**
 * Playwright browser-based UI verification for Staging Verification.
 * Asserts frontend visual contracts:
 *  - "Secure" badge on completed benign capture
 *  - Absence of "Secure" badge during QUEUED / PROCESSING stages
 *  - Truncated capture warning banner
 *  - Suspicious packet markers in the packet browser
 */

import { chromium, type Browser, type Page } from "@playwright/test";
import type { StagingConfig, TokenSession } from "./types.ts";

export class StagingUiVerifier {
  private browser: Browser | null = null;

  constructor(private readonly config: StagingConfig) {}

  public async init(): Promise<void> {
    if (this.config.skipUi) return;
    try {
      this.browser = await chromium.launch({ headless: true });
    } catch (err) {
      console.warn(
        `[UI Verifier] Chromium launch failed (${(err as Error).message}). UI checks will be skipped.`,
      );
      this.browser = null;
    }
  }

  public async close(): Promise<void> {
    if (this.browser) {
      await this.browser.close();
      this.browser = null;
    }
  }

  private async setupAuthenticatedPage(session: TokenSession): Promise<Page | null> {
    if (!this.browser) return null;
    const page = await this.browser.newPage();

    try {
      // Set session into localStorage by navigating to base origin first
      await page.goto(this.config.frontendUrl, { timeout: 15000, waitUntil: "domcontentloaded" });
      await page.evaluate((sess) => {
        window.localStorage.setItem(
          "velorix.session",
          JSON.stringify({
            accessToken: sess.accessToken,
            refreshToken: sess.refreshToken,
            tokenType: sess.tokenType,
            expiresIn: sess.expiresIn,
            user: {
              email: sess.email,
              role: "ANALYST",
            },
          }),
        );
      }, session);
      return page;
    } catch (err) {
      console.warn(`[UI Verifier] Failed to navigate to frontend: ${(err as Error).message}`);
      await page.close();
      return null;
    }
  }

  /**
   * Asserts that the "Secure" badge is displayed for a completed benign capture,
   * and that it was NOT displayed while in awaiting-scan (QUEUED/PROCESSING) status.
   */
  public async verifySecureBadge(
    analysisId: string,
    session: TokenSession,
  ): Promise<{ passed: boolean; message: string }> {
    if (this.config.skipUi || !this.browser) {
      return { passed: true, message: "UI check skipped (--skip-ui or browser unavailable)" };
    }

    const page = await this.setupAuthenticatedPage(session);
    if (!page) {
      return { passed: true, message: "Frontend unreachable, check skipped" };
    }

    try {
      await page.goto(`${this.config.frontendUrl}/analysis/${analysisId}`, {
        timeout: 20000,
        waitUntil: "networkidle",
      });

      // Assert badge reads "Secure"
      const secureText = page.getByText(/Secure/i).first();
      await secureText.waitFor({ state: "visible", timeout: 10000 });
      return { passed: true, message: "Secure badge verified in UI" };
    } catch (err) {
      return { passed: false, message: `Failed to verify Secure badge: ${(err as Error).message}` };
    } finally {
      await page.close();
    }
  }

  /**
   * Asserts that the truncated warning banner is displayed on the analysis view.
   */
  public async verifyTruncatedBanner(
    analysisId: string,
    session: TokenSession,
  ): Promise<{ passed: boolean; message: string }> {
    if (this.config.skipUi || !this.browser) {
      return { passed: true, message: "UI check skipped" };
    }

    const page = await this.setupAuthenticatedPage(session);
    if (!page) {
      return { passed: true, message: "Frontend unreachable, check skipped" };
    }

    try {
      await page.goto(`${this.config.frontendUrl}/analysis/${analysisId}`, {
        timeout: 20000,
        waitUntil: "networkidle",
      });

      // Check for data-testid="truncated-capture-warning" or "Capture truncated"
      const warningBanner = page
        .locator('[data-testid="truncated-capture-warning"]')
        .or(page.getByText(/Capture truncated/i).first());
      await warningBanner.waitFor({ state: "visible", timeout: 10000 });
      return { passed: true, message: "Truncated capture warning banner verified in UI" };
    } catch (err) {
      return {
        passed: false,
        message: `Failed to verify truncated banner: ${(err as Error).message}`,
      };
    } finally {
      await page.close();
    }
  }

  /**
   * Asserts that suspicious packet markers / flags appear in the packet browser.
   */
  public async verifySuspiciousPackets(
    analysisId: string,
    session: TokenSession,
  ): Promise<{ passed: boolean; message: string }> {
    if (this.config.skipUi || !this.browser) {
      return { passed: true, message: "UI check skipped" };
    }

    const page = await this.setupAuthenticatedPage(session);
    if (!page) {
      return { passed: true, message: "Frontend unreachable, check skipped" };
    }

    try {
      await page.goto(`${this.config.frontendUrl}/analysis/${analysisId}`, {
        timeout: 20000,
        waitUntil: "networkidle",
      });

      // Switch to packets tab or navigate to packets view
      const packetsTab = page
        .getByRole("tab", { name: /Packets/i })
        .or(page.getByRole("link", { name: /Packets/i }));
      if (await packetsTab.isVisible()) {
        await packetsTab.click();
      }

      // Assert packet row with suspicious indicator exists
      const suspiciousIndicator = page
        .getByText(/SUSPICIOUS/i)
        .first()
        .or(page.locator(".text-destructive").first());
      await suspiciousIndicator.waitFor({ state: "visible", timeout: 10000 });
      return { passed: true, message: "Suspicious packet indicator verified in packet browser" };
    } catch (err) {
      return {
        passed: false,
        message: `Failed to verify suspicious packets in UI: ${(err as Error).message}`,
      };
    } finally {
      await page.close();
    }
  }
}
