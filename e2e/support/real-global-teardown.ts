import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PID_FILE = path.resolve(__dirname, "../.backend-e2e.pid");

export default async function globalTeardown() {
  console.log("\n[E2E Real Teardown] Cleaning up full-stack test processes...");
  if (fs.existsSync(PID_FILE)) {
    const pid = fs.readFileSync(PID_FILE, "utf8").trim();
    if (pid) {
      console.log(`[E2E Real Teardown] Terminating Spring Boot process (PID ${pid})...`);
      try {
        if (process.platform === "win32") {
          execSync(`taskkill /PID ${pid} /T /F`, { stdio: "ignore" });
        } else {
          process.kill(Number(pid), "SIGTERM");
        }
      } catch {
        // process may have already exited
      }
    }
    try {
      fs.unlinkSync(PID_FILE);
    } catch {
      // ignore
    }
  }
  console.log("[E2E Real Teardown] Teardown complete.\n");
}
