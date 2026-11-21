import { execSync, spawn } from "node:child_process";
import fs from "node:fs";
import net from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PID_FILE = path.resolve(__dirname, "../.backend-e2e.pid");

async function checkPortOpen(port: number, host = "127.0.0.1", timeoutMs = 2000): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    socket.setTimeout(timeoutMs);
    socket.once("connect", () => {
      socket.destroy();
      resolve(true);
    });
    socket.once("timeout", () => {
      socket.destroy();
      resolve(false);
    });
    socket.once("error", () => {
      resolve(false);
    });
    socket.connect(port, host);
  });
}

async function isBackendHealthy(): Promise<boolean> {
  try {
    const res = await fetch("http://127.0.0.1:8080/api/health");
    if (!res.ok) return false;
    const body = (await res.json()) as { success?: boolean; data?: { status?: string } };
    return body?.data?.status === "UP" || body?.success === true;
  } catch {
    return false;
  }
}

export default async function globalSetup() {
  console.log("\n[E2E Real Setup] Initializing full-stack test environment...");

  // 1. Ensure PostgreSQL container is running on port 5499
  const pgPortOpen = await checkPortOpen(5499);
  if (!pgPortOpen) {
    console.log("[E2E Real Setup] PostgreSQL container not detected on port 5499. Launching velorix-e2e-pg...");
    try {
      execSync("docker rm -f velorix-e2e-pg", { stdio: "ignore" });
    } catch {
      // ignore
    }
    execSync(
      "docker run -d --rm --name velorix-e2e-pg -p 5499:5432 -e POSTGRES_DB=sentinel_e2e -e POSTGRES_USER=sentinel -e POSTGRES_PASSWORD=sentinel postgres:16-alpine",
      { stdio: "inherit" },
    );
  }

  // Wait for PostgreSQL to accept connections
  console.log("[E2E Real Setup] Waiting for PostgreSQL readiness on port 5499...");
  const pgDeadline = Date.now() + 30_000;
  let pgReady = false;
  while (Date.now() < pgDeadline) {
    if (await checkPortOpen(5499)) {
      pgReady = true;
      break;
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  if (!pgReady) {
    throw new Error("[E2E Real Setup] PostgreSQL container failed to accept connections on port 5499 within 30s.");
  }
  console.log("[E2E Real Setup] PostgreSQL is ready on port 5499.");

  // 2. Ensure Spring Boot backend is running
  if (await isBackendHealthy()) {
    console.log("[E2E Real Setup] Spring Boot backend is already running and healthy on port 8080.");
    return;
  }

  const rootDir = path.resolve(__dirname, "../..");
  const jarPath = path.resolve(rootDir, "backend/target/sentinel-backend-0.1.0.jar");
  if (!fs.existsSync(jarPath)) {
    console.log("[E2E Real Setup] Backend jar not found. Building with Maven...");
    execSync("mvn clean package -DskipTests -f backend/pom.xml", { cwd: rootDir, stdio: "inherit" });
  }

  console.log("[E2E Real Setup] Starting Spring Boot backend in e2e profile on port 8080...");
  const logStream = fs.createWriteStream(path.resolve(__dirname, "../backend-e2e.log"), { flags: "a" });
  const backendProc = spawn(
    "java",
    ["-Dspring.profiles.active=e2e", "-jar", jarPath],
    {
      cwd: rootDir,
      detached: true,
      stdio: ["ignore", logStream, logStream],
    },
  );

  backendProc.unref();
  if (backendProc.pid) {
    fs.writeFileSync(PID_FILE, String(backendProc.pid), "utf8");
    console.log(`[E2E Real Setup] Spring Boot process spawned (PID: ${backendProc.pid}).`);
  }

  // Poll for backend health
  console.log("[E2E Real Setup] Waiting for Spring Boot health probe (/api/health)...");
  const backendDeadline = Date.now() + 90_000;
  let backendReady = false;
  while (Date.now() < backendDeadline) {
    if (await isBackendHealthy()) {
      backendReady = true;
      break;
    }
    await new Promise((r) => setTimeout(r, 1500));
  }

  if (!backendReady) {
    const recentLogs = fs.existsSync(path.resolve(__dirname, "../backend-e2e.log"))
      ? fs.readFileSync(path.resolve(__dirname, "../backend-e2e.log"), "utf8").slice(-2000)
      : "No logs found";
    throw new Error(`[E2E Real Setup] Spring Boot backend failed to become healthy within 90s.\nRecent backend logs:\n${recentLogs}`);
  }

  console.log("[E2E Real Setup] Spring Boot backend is UP and accepting traffic on port 8080.\n");
}
