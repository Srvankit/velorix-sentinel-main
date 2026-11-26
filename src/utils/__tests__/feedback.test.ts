import { describe, expect, it, afterEach } from "vitest";
import { getFeedbackEmail } from "../feedback";

describe("getFeedbackEmail", () => {
  const originalEmail = import.meta.env.VITE_FEEDBACK_EMAIL;

  afterEach(() => {
    if (originalEmail !== undefined) {
      import.meta.env.VITE_FEEDBACK_EMAIL = originalEmail;
    } else {
      delete import.meta.env.VITE_FEEDBACK_EMAIL;
    }
  });

  it("returns undefined when VITE_FEEDBACK_EMAIL is not set", () => {
    delete import.meta.env.VITE_FEEDBACK_EMAIL;
    expect(getFeedbackEmail()).toBeUndefined();
  });

  it("returns undefined when VITE_FEEDBACK_EMAIL is empty string", () => {
    import.meta.env.VITE_FEEDBACK_EMAIL = "";
    expect(getFeedbackEmail()).toBeUndefined();
  });

  it("returns undefined when VITE_FEEDBACK_EMAIL is whitespace only", () => {
    import.meta.env.VITE_FEEDBACK_EMAIL = "   ";
    expect(getFeedbackEmail()).toBeUndefined();
  });

  it("returns trimmed email when VITE_FEEDBACK_EMAIL is valid", () => {
    import.meta.env.VITE_FEEDBACK_EMAIL = "  beta-feedback@velorix.tech  ";
    expect(getFeedbackEmail()).toBe("beta-feedback@velorix.tech");
  });
});
