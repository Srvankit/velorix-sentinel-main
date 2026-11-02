import { createFileRoute } from "@tanstack/react-router";

import { CopilotWorkspace } from "@/components/copilot";

export const Route = createFileRoute("/assistant/")({
  head: () => ({
    meta: [
      { title: "AI Security Copilot (Experimental / Join Waitlist) · Velorix Sentinel" },
      {
        name: "description",
        content:
          "Experimental AI-assisted investigation workspace for analysts across captures, threats and incidents (Roadmap Preview).",
      },
      {
        property: "og:title",
        content: "AI Security Copilot (Experimental / Join Waitlist) · Velorix Sentinel",
      },
      {
        property: "og:description",
        content:
          "Experimental AI-assisted investigation workspace for analysts across captures, threats and incidents (Roadmap Preview).",
      },
    ],
  }),
  component: () => <CopilotWorkspace />,
});
