import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";

import { Spinner } from "@/components/common";
import { useAuth } from "@/hooks/useAuth";
import type { AuthSession } from "@/types/auth";
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

    const hash = window.location.hash.slice(1);
    if (!hash) {
      hasProcessedRef.current = true;
      void navigate({ to: ROUTES.login, replace: true });
      return;
    }

    const params = new URLSearchParams(hash);
    const accessToken = params.get("accessToken");
    const refreshToken = params.get("refreshToken");
    const accessTokenExpiresAt = params.get("accessTokenExpiresAt") ?? "";
    const refreshTokenExpiresAt = params.get("refreshTokenExpiresAt") ?? "";

    if (!accessToken || !refreshToken) {
      hasProcessedRef.current = true;
      void navigate({ to: ROUTES.login, replace: true });
      return;
    }

    hasProcessedRef.current = true;
    // Strip sensitive tokens from the browser URL bar and history immediately
    window.history.replaceState(null, "", window.location.pathname);

    const session: Omit<AuthSession, "user"> = {
      accessToken,
      refreshToken,
      tokenType: "Bearer",
      accessTokenExpiresAt,
      refreshTokenExpiresAt,
    };
    void completeOAuthLogin(session)
      .then(() => navigate({ to: ROUTES.dashboard, replace: true }))
      .catch(() => {
        void navigate({ to: ROUTES.login, replace: true });
      });
  }, [completeOAuthLogin, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Spinner className="size-6" />
    </div>
  );
}
