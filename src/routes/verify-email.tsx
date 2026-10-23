import { createFileRoute } from "@tanstack/react-router";
import { Mail, MailCheck, MailWarning, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";

import { AuthStatus } from "@/components/auth";
import { PageLoader, Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { authService, toApiError } from "@/services";
import type { ApiError } from "@/types/api";
import { APP_NAME, ROUTES } from "@/utils/constants";

const TITLE = `Email verification — ${APP_NAME}`;
const DESCRIPTION = `Confirm your ${APP_NAME} email address to activate your account.`;

const searchSchema = z.object({
  token: z.string().optional(),
  email: z.string().optional(),
});

export const Route = createFileRoute("/verify-email")({
  validateSearch: searchSchema,
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { name: "robots", content: "noindex" },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.verifyEmail },
    ],
    links: [{ rel: "canonical", href: ROUTES.verifyEmail }],
  }),
  component: VerifyEmailPage,
});

type Status = "verifying" | "verified" | "failed" | "awaiting";

function VerifyEmailPage() {
  const { token, email } = Route.useSearch();
  const [status, setStatus] = useState<Status>(() => {
    if (token) return "verifying";
    if (email) return "awaiting";
    return "failed";
  });
  const [error, setError] = useState<ApiError | null>(null);
  const [resending, setResending] = useState(false);
  const [resendSuccess, setResendSuccess] = useState(false);
  const [resendError, setResendError] = useState<string | null>(null);
  const requested = useRef(false);

  useEffect(() => {
    if (!token || requested.current) return;
    requested.current = true;

    authService
      .verifyEmail({ token })
      .then(() => setStatus("verified"))
      .catch((err) => {
        setError(toApiError(err));
        setStatus("failed");
      });
  }, [token]);

  const handleResend = async () => {
    if (!email || resending) return;
    setResending(true);
    setResendError(null);
    try {
      await authService.resendVerification({ email });
      setResendSuccess(true);
    } catch (err) {
      const apiError = toApiError(err);
      setResendError(apiError.message || "Failed to resend verification link. Please try again.");
    } finally {
      setResending(false);
    }
  };

  if (status === "verifying") {
    return <PageLoader label="Verifying your email" />;
  }

  if (status === "awaiting") {
    return (
      <AuthStatus
        icon={Mail}
        tone="primary"
        code="Verification required"
        title="Check your inbox"
        description={
          email
            ? `We sent a verification link to ${email}. Please check your inbox and click the link to activate your Velorix Sentinel account.`
            : "A verification email has been sent to your address. Please click the link inside to activate your account."
        }
        primaryAction={{ label: "Go to sign in", to: ROUTES.login }}
        secondaryAction={{ label: "Back to home", to: ROUTES.home }}
      >
        {email && (
          <div className="mt-6 flex flex-col items-center gap-3">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={resending || resendSuccess}
              onClick={handleResend}
              className="gap-2 rounded-full"
            >
              {resending ? <Spinner className="size-4" /> : <RefreshCw className="size-3.5" />}
              {resending
                ? "Sending link..."
                : resendSuccess
                  ? "Verification link sent!"
                  : "Resend verification email"}
            </Button>
            {resendError && (
              <p className="text-xs text-destructive" role="alert">
                {resendError}
              </p>
            )}
            {resendSuccess && (
              <p className="text-xs text-success" role="status">
                If the account is eligible, a fresh verification email has been dispatched.
              </p>
            )}
          </div>
        )}
      </AuthStatus>
    );
  }

  if (status === "failed") {
    return (
      <AuthStatus
        icon={MailWarning}
        tone="warning"
        code="Verification failed"
        title="This verification link isn't valid"
        description={
          error?.message ??
          "The link is missing, has expired, or has already been used. Sign in to request a new verification email."
        }
        primaryAction={{ label: "Go to sign in", to: ROUTES.login }}
        secondaryAction={{ label: "Back to home", to: ROUTES.home }}
      />
    );
  }

  return (
    <AuthStatus
      icon={MailCheck}
      tone="success"
      code="Verification complete"
      title="Your email is verified"
      description="Thanks for confirming your address. Your account is active and you can sign in to the console."
      primaryAction={{ label: "Continue to sign in", to: ROUTES.login }}
      secondaryAction={{ label: "Back to home", to: ROUTES.home }}
    />
  );
}
