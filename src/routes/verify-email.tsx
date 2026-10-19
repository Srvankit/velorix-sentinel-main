import { createFileRoute } from "@tanstack/react-router";
import { MailCheck, MailWarning } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { z } from "zod";

import { AuthStatus } from "@/components/auth";
import { PageLoader } from "@/components/common";
import { authService, toApiError } from "@/services";
import type { ApiError } from "@/types/api";
import { APP_NAME, ROUTES } from "@/utils/constants";

const TITLE = `Email verification — ${APP_NAME}`;
const DESCRIPTION = `Confirm your ${APP_NAME} email address to activate your account.`;

const searchSchema = z.object({ token: z.string().optional() });

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

type Status = "verifying" | "verified" | "failed";

function VerifyEmailPage() {
  const { token } = Route.useSearch();
  const [status, setStatus] = useState<Status>(token ? "verifying" : "failed");
  const [error, setError] = useState<ApiError | null>(null);
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

  if (status === "verifying") {
    return <PageLoader label="Verifying your email" />;
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
