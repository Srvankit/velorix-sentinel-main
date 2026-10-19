/**
 * Password strength scoring used by the register / reset-password forms.
 * Purely client-side UX feedback — the backend remains the source of truth.
 */

export interface PasswordStrength {
  /** 0–4 */
  score: number;
  label: "Very weak" | "Weak" | "Fair" | "Strong" | "Very strong";
  /** Requirements the password still fails. */
  unmet: string[];
}

const RULES: Array<{ test: (v: string) => boolean; hint: string }> = [
  { test: (v) => v.length >= 10, hint: "At least 10 characters" },
  { test: (v) => /[A-Z]/.test(v), hint: "One uppercase letter" },
  { test: (v) => /[a-z]/.test(v), hint: "One lowercase letter" },
  { test: (v) => /[0-9]/.test(v), hint: "One number" },
  { test: (v) => /[^A-Za-z0-9]/.test(v), hint: "One special character" },
];

const LABELS: PasswordStrength["label"][] = ["Very weak", "Weak", "Fair", "Strong", "Very strong"];

export function scorePassword(value: string): PasswordStrength {
  if (!value) return { score: 0, label: "Very weak", unmet: RULES.map((r) => r.hint) };

  const unmet = RULES.filter((r) => !r.test(value)).map((r) => r.hint);
  let passed = RULES.length - unmet.length;
  if (value.length >= 16 && passed === RULES.length) passed += 1;

  const score = Math.max(0, Math.min(4, passed - 1));
  return { score, label: LABELS[score], unmet };
}
