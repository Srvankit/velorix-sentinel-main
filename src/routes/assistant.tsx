import { Outlet, createFileRoute } from "@tanstack/react-router";

import { ProtectedRoute } from "@/components/auth";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/assistant")({
  component: AssistantLayout,
  head: () => ({
    meta: [
      { title: `AI Security Copilot · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Enterprise AI security copilot for threat explanation, investigation summaries and SOC reporting across your network captures.",
      },
      { property: "og:title", content: `AI Security Copilot · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Ask analyst-grade security questions and get structured, framework-aware guidance.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function AssistantLayout() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <Outlet />
      </DashboardLayout>
    </ProtectedRoute>
  );
}
