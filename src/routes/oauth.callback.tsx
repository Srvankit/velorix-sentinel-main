import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { AlertCircle, ArrowLeft, Mail } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { describeError, type DescribedError } from "@/lib/errors";
import { authService } from "@/services/authService";
import { toApiError } from "@/services";
import { ROUTES } from "@/utils/constants";
import { getFeedbackEmail } from "@/utils/feedback";

export const Route = createFileRoute("/oauth/callback")({
  component: OAuthCallbackPage,
});

function OAuthCallbackPage() {
  const navigate = useNavigate();
  const { completeOAuthLogin } = useAuth();
  const hasProcessedRef = useRef(false);
  const [errorState, setErrorState] = useState<DescribedError | null>(null);

  useEffect(() => {
    if (hasProcessedRef.current) return;

    // Secure one-time authorization code exchange (Task 3.5)
    const searchParams = new URLSearchParams(window.location.search);
    const code = searchParams.get("code");
    const error = searchParams.get("error");
    const errorDescription = searchParams.get("error_description");

    if (error) {
      hasProcessedRef.current = true;
      window.history.replaceState(null, "", window.location.pathname);
      setErrorState(
        describeError({
          status: 400,
          code: error,
          message: errorDescription || `OAuth provider returned an error: ${error}`,
        }),
      );
      return;
    }

    if (code) {
      hasProcessedRef.current = true;
      // Strip authorization code from the browser URL bar and history immediately
      window.history.replaceState(null, "", window.location.pathname);

      void authService
        .exchangeOAuthCode({ code })
        .then((session) => completeOAuthLogin(session))
        .then(() => navigate({ to: ROUTES.dashboard, replace: true }))
        .catch((err) => {
          const apiError = toApiError(err);
          setErrorState(describeError(apiError));
        });
      return;
    }

    // Code is missing (legacy token fragments are strictly rejected)
    hasProcessedRef.current = true;
    if (window.location.hash) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    void navigate({ to: ROUTES.login, replace: true });
  }, [completeOAuthLogin, navigate]);

  if (errorState) {
    const feedbackEmail = getFeedbackEmail();
    return (
      <div className="flex min-h-screen items-center justify-center p-4 bg-background">
        <div className="w-full max-w-md space-y-6 rounded-2xl border border-border bg-card p-8 text-center shadow-lg">
          <span className="inline-grid size-12 place-items-center rounded-xl bg-destructive/15 text-destructive">
            <AlertCircle className="size-6" aria-hidden="true" />
          </span>
          <div className="space-y-2">
            <h2 className="text-xl font-bold tracking-tight text-foreground">{errorState.title}</h2>
            <p className="text-sm text-muted-foreground">{errorState.message}</p>
          </div>
          <div className="flex flex-col gap-2.5 pt-2">
            <Button asChild variant="default" className="w-full">
              <Link to={ROUTES.login}>
                <ArrowLeft className="mr-2 size-4" />
                Return to Sign In
              </Link>
            </Button>
            {feedbackEmail && (
              <Button asChild variant="outline" className="w-full">
                <a
                  href={`mailto:${feedbackEmail}?subject=${encodeURIComponent("Velorix Sentinel Beta Access Request")}`}
                >
                  <Mail className="mr-2 size-4" />
                  Request Beta Invitation
                </a>
              </Button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Spinner className="size-6" />
    </div>
  );
}
