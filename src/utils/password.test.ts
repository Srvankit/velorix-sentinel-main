import { describe, expect, test } from "bun:test";
import { isStrongPassword, scorePassword } from "./password";

describe("password policy consistency (frontend)", () => {
  test("accepts valid 10-character password meeting complexity", () => {
    const password = "A1!aaaaaaa"; // 10 chars
    expect(isStrongPassword(password)).toBe(true);
    const score = scorePassword(password);
    expect(score.unmet).toHaveLength(0);
  });

  test("rejects password shorter than 10 characters", () => {
    expect(isStrongPassword("A1!aaaaaa")).toBe(false); // 9 chars
    expect(isStrongPassword("A1!aaaaa")).toBe(false); // 8 chars
    expect(isStrongPassword("A1!a")).toBe(false); // 4 chars
    const score = scorePassword("A1!aaaaaa");
    expect(score.unmet).toContain("At least 10 characters");
  });

  test("accepts password at exact 72-byte limit (ASCII)", () => {
    const password = "A1!" + "a".repeat(69); // 72 chars, 72 bytes
    expect(password.length).toBe(72);
    expect(new TextEncoder().encode(password).length).toBe(72);
    expect(isStrongPassword(password)).toBe(true);
  });

  test("rejects ASCII password exceeding 72 characters", () => {
    const password = "A1!" + "a".repeat(70); // 73 chars, 73 bytes
    expect(password.length).toBe(73);
    expect(isStrongPassword(password)).toBe(false);
  });

  test("accepts multibyte password at or below 72 UTF-8 bytes", () => {
    const password70 = "Aa1!" + "€".repeat(22); // 26 chars, 70 bytes
    expect(password70.length).toBe(26);
    expect(new TextEncoder().encode(password70).length).toBe(70);
    expect(isStrongPassword(password70)).toBe(true);

    const password72 = "Aa1!bb" + "€".repeat(22); // 28 chars, 72 bytes
    expect(password72.length).toBe(28);
    expect(new TextEncoder().encode(password72).length).toBe(72);
    expect(isStrongPassword(password72)).toBe(true);
  });

  test("rejects multibyte password exceeding 72 UTF-8 bytes even when char length <= 72", () => {
    // 29 characters total, but 79 UTF-8 bytes
    const password79 = "Aa1!" + "€".repeat(25);
    expect(password79.length).toBe(29);
    expect(new TextEncoder().encode(password79).length).toBe(79);
    expect(isStrongPassword(password79)).toBe(false);

    // 28 characters total, 73 bytes
    const password73 = "Aa1!b" + "€".repeat(22) + "é";
    expect(password73.length).toBe(28);
    expect(new TextEncoder().encode(password73).length).toBe(73);
    expect(isStrongPassword(password73)).toBe(false);
  });

  test("missing complexity dimensions are rejected", () => {
    expect(isStrongPassword("lowercase1!")).toBe(false); // missing uppercase
    expect(isStrongPassword("UPPERCASE1!")).toBe(false); // missing lowercase
    expect(isStrongPassword("UpperLower!")).toBe(false); // missing digit
    expect(isStrongPassword("UpperLower1")).toBe(false); // missing special char
  });
});
