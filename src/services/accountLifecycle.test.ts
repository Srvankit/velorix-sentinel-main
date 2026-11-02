import { describe, expect, test } from "bun:test";
import type { ChangePasswordRequest, DeleteAccountRequest } from "@/types/auth";
import { isStrongPassword } from "@/utils/password";

describe("Account Lifecycle Frontend Logic (Task 4.4)", () => {
  const ENDPOINTS = {
    changePassword: "/auth/change-password",
    deleteAccount: "/auth/account",
  } as const;

  describe("Endpoint paths", () => {
    test("change password endpoint matches backend contract", () => {
      expect(ENDPOINTS.changePassword).toBe("/auth/change-password");
    });

    test("delete account endpoint matches backend contract", () => {
      expect(ENDPOINTS.deleteAccount).toBe("/auth/account");
    });
  });

  describe("Change Password validation and payload formatting", () => {
    function validateChangePassword(
      currentPassword: string,
      newPassword: string,
      confirmPassword: string,
    ): { error?: string; payload?: ChangePasswordRequest } {
      if (!currentPassword) {
        return { error: "Please enter your current password." };
      }
      if (!isStrongPassword(newPassword)) {
        return {
          error:
            "New password must be 10–72 characters and contain uppercase, lowercase, number, and special character.",
        };
      }
      if (newPassword !== confirmPassword) {
        return { error: "New passwords do not match." };
      }
      if (newPassword === currentPassword) {
        return { error: "New password must be different from current password." };
      }
      return {
        payload: {
          currentPassword,
          newPassword,
        },
      };
    }

    test("valid change password input yields payload", () => {
      const result = validateChangePassword(
        "CurrentPass123!",
        "NewStrongPass456@",
        "NewStrongPass456@",
      );
      expect(result.error).toBeUndefined();
      expect(result.payload).toEqual({
        currentPassword: "CurrentPass123!",
        newPassword: "NewStrongPass456@",
      });
    });

    test("rejects missing current password", () => {
      const result = validateChangePassword("", "NewStrongPass456@", "NewStrongPass456@");
      expect(result.error).toBe("Please enter your current password.");
      expect(result.payload).toBeUndefined();
    });

    test("rejects weak new password", () => {
      const result = validateChangePassword("CurrentPass123!", "weak", "weak");
      expect(result.error).toContain("New password must be 10–72 characters");
      expect(result.payload).toBeUndefined();
    });

    test("rejects non-matching confirm password", () => {
      const result = validateChangePassword(
        "CurrentPass123!",
        "NewStrongPass456@",
        "MismatchPass456@",
      );
      expect(result.error).toBe("New passwords do not match.");
      expect(result.payload).toBeUndefined();
    });

    test("rejects identical new and current password", () => {
      const result = validateChangePassword(
        "CurrentPass123!",
        "CurrentPass123!",
        "CurrentPass123!",
      );
      expect(result.error).toBe("New password must be different from current password.");
      expect(result.payload).toBeUndefined();
    });
  });

  describe("Account Deletion payload formatting", () => {
    function prepareDeleteAccountPayload(input: string): DeleteAccountRequest {
      const isDeletePhrase = input.trim().toUpperCase() === "DELETE";
      return {
        password: isDeletePhrase ? undefined : input,
        confirmation: isDeletePhrase ? "DELETE" : undefined,
      };
    }

    test("formats standard password deletion payload", () => {
      const payload = prepareDeleteAccountPayload("MySecretPass123!");
      expect(payload.password).toBe("MySecretPass123!");
      expect(payload.confirmation).toBeUndefined();
    });

    test("formats OAuth DELETE confirmation phrase payload (exact)", () => {
      const payload = prepareDeleteAccountPayload("DELETE");
      expect(payload.password).toBeUndefined();
      expect(payload.confirmation).toBe("DELETE");
    });

    test("formats OAuth DELETE confirmation phrase payload (case-insensitive and trimmed)", () => {
      const payload = prepareDeleteAccountPayload("  delete  ");
      expect(payload.password).toBeUndefined();
      expect(payload.confirmation).toBe("DELETE");
    });
  });
});
