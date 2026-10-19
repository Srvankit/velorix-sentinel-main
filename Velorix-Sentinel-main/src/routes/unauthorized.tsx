import { createFileRoute } from "@tanstack/react-router";
import { ShieldX } from "lucide-react";

import { AuthStatus } from "@/components/auth";
import { APP_NAME, ROUTES } from "@/utils/constants";

const TITLE = `Access denied — ${APP_NAME}`;
const DESCRIPTION = "You don't have permission to view this area.";

export const Route = createFileRoute("/unauthorized")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { name: "robots", content: "noindex" },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.unauthorized },
    ],
    links: [{ rel: "canonical", href: ROUTES.unauthorized }],
  }),
  component: UnauthorizedPage,
});

function UnauthorizedPage() {
  return (
    <AuthStatus
      icon={ShieldX}
      code="Error 403"
      title="You don't have access to this area"
      description="Your account is signed in, but it doesn't hold the role required for this module. Ask a workspace administrator to grant access."
      primaryAction={{ label: "Back to home", to: ROUTES.home }}
      secondaryAction={{ label: "Switch account", to: ROUTES.login }}
    />
  );
}
