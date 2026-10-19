import { createFileRoute } from "@tanstack/react-router";
import { Bell } from "lucide-react";

import { ModulePlaceholder } from "@/components/common";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/notifications")({
  component: NotificationsPage,
  head: () => ({
    meta: [
      { title: "Notifications · " + APP_NAME },
      { name: "description", content: "Alerts for completed inspections and critical findings." },
      { property: "og:title", content: "Notifications · " + APP_NAME },
      {
        property: "og:description",
        content: "Alerts for completed inspections and critical findings.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function NotificationsPage() {
  return (
    <ModulePlaceholder
      title="Notifications"
      description="Alerts for completed inspections and critical findings."
      icon={Bell}
    />
  );
}
