import { createFileRoute } from "@tanstack/react-router";
import { TimerOff } from "lucide-react";

import { AuthStatus } from "@/components/auth";
import { APP_NAME, ROUTES } from "@/utils/constants";

const TITLE = `Session expired — ${APP_NAME}`;
const DESCRIPTION = "Your session has expired. Sign in again to continue.";

export const Route = createFileRoute("/session-expired")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { name: "robots", content: "noindex" },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.sessionExpired },
    ],
    links: [{ rel: "canonical", href: ROUTES.sessionExpired }],
  }),
  component: SessionExpiredPage,
});

function SessionExpiredPage() {
  return (
    <AuthStatus
      icon={TimerOff}
      tone="warning"
      code="Session ended"
      title="Your session has expired"
      description={`For your security, ${APP_NAME} ends inactive sessions automatically. Sign in again to pick up where you left off.`}
      primaryAction={{ label: "Sign in again", to: ROUTES.login }}
      secondaryAction={{ label: "Back to home", to: ROUTES.home }}
    />
  );
}
