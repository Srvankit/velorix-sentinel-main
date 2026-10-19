import { createFileRoute } from "@tanstack/react-router";
import { History } from "lucide-react";

import { ModulePlaceholder } from "@/components/common";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/history")({
  component: HistoryPage,
  head: () => ({
    meta: [
      { title: "History · " + APP_NAME },
      {
        name: "description",
        content: "A full audit trail of inspections, detections and exports.",
      },
      { property: "og:title", content: "History · " + APP_NAME },
      {
        property: "og:description",
        content: "A full audit trail of inspections, detections and exports.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function HistoryPage() {
  return (
    <ModulePlaceholder
      title="History"
      description="A full audit trail of inspections, detections and exports."
      icon={History}
    />
  );
}
