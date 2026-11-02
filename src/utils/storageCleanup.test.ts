import { describe, expect, test } from "bun:test";
import fs from "node:fs";
import path from "node:path";
import { STORAGE_KEYS, storage } from "./storage";

describe("Build Artifact Cleanup & Legacy Key Deprecation (Task 5.6, L7)", () => {
  const rootDir = path.resolve(__dirname, "../..");
  const gitignorePath = path.join(rootDir, ".gitignore");
  const storagePath = path.join(rootDir, "src", "utils", "storage.ts");
  const apiTypesPath = path.join(rootDir, "src", "types", "api.ts");

  const gitignoreContent = fs.readFileSync(gitignorePath, "utf-8");
  const storageContent = fs.readFileSync(storagePath, "utf-8");
  const apiTypesContent = fs.readFileSync(apiTypesPath, "utf-8");

  describe("Standardized Storage Key", () => {
    test("STORAGE_KEYS.session is standardized on velorix.session", () => {
      expect(STORAGE_KEYS.session).toBe("velorix.session");
    });

    test("storage.ts source does not contain legacy sentinelai.session key", () => {
      expect(storageContent).not.toContain("sentinelai.session");
      expect(storageContent).toContain('session: "velorix.session"');
    });

    test("storage operations function properly with standardized key", () => {
      const mockSession = { token: "test-jwt", user: { id: "u-1" } };
      storage.set(STORAGE_KEYS.session, mockSession);

      const retrieved = storage.get<typeof mockSession>(STORAGE_KEYS.session);
      expect(retrieved).toEqual(mockSession);

      storage.remove(STORAGE_KEYS.session);
      expect(storage.get(STORAGE_KEYS.session)).toBeNull();
    });
  });

  describe("Gitignore Artifact Configuration", () => {
    test(".gitignore contains .netlify/ entry", () => {
      expect(gitignoreContent).toMatch(/^\.netlify\//m);
    });

    test(".gitignore contains .lovable/ entry", () => {
      expect(gitignoreContent).toMatch(/^\.lovable\//m);
    });
  });

  describe("API Types Modernization", () => {
    test("api.ts references Velorix Sentinel instead of SentinelAI", () => {
      expect(apiTypesContent).toContain("Velorix Sentinel Spring Boot backend");
      expect(apiTypesContent).not.toContain("SentinelAI Spring Boot backend");
    });
  });
});

