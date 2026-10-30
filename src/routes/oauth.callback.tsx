import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";

import { Spinner } from "@/components/common";
import { useAuth } from "@/hooks/useAuth";
import { authService } from "@/services/authService";
import { ROUTES } from "@/utils/constants";

export const Route = createFileRoute("/oauth/callback")({
  component: OAuthCallbackPage,
});

function OAuthCallbackPage() {
  const navigate = useNavigate();
  const { completeOAuthLogin } = useAuth();
  const hasProcessedRef = useRef(false);

  useEffect(() => {
    if (hasProcessedRef.current) return;

    // Secure one-time authorization code exchange (Task 3.5)
    const searchParams = new URLSearchParams(window.location.search);
    const code = searchParams.get("code");
    const error = searchParams.get("error");

    if (error) {
      hasProcessedRef.current = true;
      window.history.replaceState(null, "", window.location.pathname);
      void navigate({ to: ROUTES.login, replace: true });
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
        .catch(() => {
          void navigate({ to: ROUTES.login, replace: true });
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

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Spinner className="size-6" />
    </div>
  );
}
