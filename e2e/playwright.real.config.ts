import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  testDir: path.resolve(__dirname, "tests"),
  testMatch: /full-stack-.*\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  timeout: 120_000,
  expect: {
    timeout: 30_000,
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: path.resolve(__dirname, "../playwright-report-real"), open: "never" }],
  ],
  globalSetup: path.resolve(__dirname, "support/real-global-setup.ts"),
  globalTeardown: path.resolve(__dirname, "support/real-global-teardown.ts"),
  use: {
    baseURL: "http://127.0.0.1:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "firefox",
      use: { ...devices["Desktop Firefox"] },
    },
  ],
  webServer: {
    command: "npx vite dev --host 127.0.0.1 --port 5173",
    url: "http://127.0.0.1:5173",
    reuseExistingServer: true,
    timeout: 120_000,
    env: {
      VITE_API_BASE_URL: "http://127.0.0.1:8080/api",
    },
  },
});
