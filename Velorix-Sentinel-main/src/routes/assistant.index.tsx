import { createFileRoute } from "@tanstack/react-router";

import { CopilotWorkspace } from "@/components/copilot";

export const Route = createFileRoute("/assistant/")({
  head: () => ({
    meta: [
      { title: "AI Security Copilot · Velorix Sentinel" },
      {
        name: "description",
        content:
          "AI-assisted investigation workspace for analysts across captures, threats and incidents.",
      },
      { property: "og:title", content: "AI Security Copilot · Velorix Sentinel" },
      {
        property: "og:description",
        content:
          "AI-assisted investigation workspace for analysts across captures, threats and incidents.",
      },
    ],
  }),
  component: () => <CopilotWorkspace />,
});
