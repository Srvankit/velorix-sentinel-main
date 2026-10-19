import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";

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

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.slice(1));
    const accessToken = params.get("accessToken");
    const refreshToken = params.get("refreshToken");
    const accessTokenExpiresAt = params.get("accessTokenExpiresAt") ?? "";
    const refreshTokenExpiresAt = params.get("refreshTokenExpiresAt") ?? "";

    if (!accessToken || !refreshToken) {
      void navigate({ to: ROUTES.login, replace: true });
      return;
    }

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
        window.history.replaceState(null, "", window.location.pathname);
        void navigate({ to: ROUTES.login, replace: true });
      });
  }, [completeOAuthLogin, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Spinner className="size-6" />
    </div>
  );
}
