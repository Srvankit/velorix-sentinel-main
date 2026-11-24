import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..");

export const PRODUCTION_BACKEND_ORIGIN = "https://velorix-sentinel-backend.onrender.com";
export const STAGING_BACKEND_ORIGIN = "https://velorix-sentinel-backend-staging.onrender.com";

/**
 * Resolves the backend origin from a raw API base URL.
 * Falls back to the canonical production origin if raw is missing, relative, or invalid.
 */
export function resolveBackendOrigin(rawApiBaseUrl) {
  if (!rawApiBaseUrl || typeof rawApiBaseUrl !== "string") {
    return PRODUCTION_BACKEND_ORIGIN;
  }
  const trimmed = rawApiBaseUrl.trim();
  if (trimmed.startsWith("/") || !/^https?:\/\//i.test(trimmed)) {
    return PRODUCTION_BACKEND_ORIGIN;
  }
  try {
    const parsed = new URL(trimmed);
    return parsed.origin;
  } catch {
    return PRODUCTION_BACKEND_ORIGIN;
  }
}

/**
 * Builds Content-Security-Policy directives with strict exact backend origin matching.
 */
export function buildContentSecurityPolicy(backendOrigin) {
  const origin = resolveBackendOrigin(backendOrigin);
  return (
    "default-src 'self'; " +
    "script-src 'self' 'unsafe-inline'; " +
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
    "font-src 'self' https://fonts.gstatic.com; " +
    "img-src 'self' data: https:; " +
    `connect-src 'self' ${origin} https://*.supabase.co; ` +
    "frame-ancestors 'none';"
  );
}

/**
 * Builds the complete Netlify _headers file content for all routes (/*).
 */
export function buildHeadersFileContent(backendOrigin) {
  const csp = buildContentSecurityPolicy(backendOrigin);
  return (
    "/*\n" +
    "  X-Frame-Options: DENY\n" +
    "  X-Content-Type-Options: nosniff\n" +
    "  Referrer-Policy: strict-origin-when-cross-origin\n" +
    "  Permissions-Policy: camera=(), microphone=(), geolocation=()\n" +
    "  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload\n" +
    `  Content-Security-Policy: ${csp}\n`
  );
}

/**
 * Main execution for build-time generation.
 * Generates dist/client/_headers if the dist directory exists,
 * or public/_headers if --write-public is specified.
 */
export function generateHeaders(targetDir, envApiUrl) {
  const backendOrigin = resolveBackendOrigin(envApiUrl || process.env.VITE_API_BASE_URL);
  const content = buildHeadersFileContent(backendOrigin);

  const targets = [];
  if (targetDir) {
    targets.push(path.join(targetDir, "_headers"));
  } else {
    // Check if dist/client exists (post-build)
    const distClient = path.join(rootDir, "dist", "client");
    if (fs.existsSync(distClient)) {
      targets.push(path.join(distClient, "_headers"));
    }
    // If --write-public flag or running prebuild
    if (process.argv.includes("--write-public")) {
      targets.push(path.join(rootDir, "public", "_headers"));
    }
  }

  for (const targetPath of targets) {
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    fs.writeFileSync(targetPath, content, "utf-8");
    console.log(
      `[generate-headers] Wrote CSP headers with connect-src '${backendOrigin}' to ${targetPath}`,
    );
  }

  return { backendOrigin, content, targets };
}

// Execute if run directly from CLI
if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  generateHeaders();
}
