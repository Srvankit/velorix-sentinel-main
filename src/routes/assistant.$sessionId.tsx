import { createFileRoute } from "@tanstack/react-router";

import { CopilotWorkspace } from "@/components/copilot";

export const Route = createFileRoute("/assistant/$sessionId")({
  head: () => ({
    meta: [
      { title: "Copilot Session (Experimental Preview) · Velorix Sentinel" },
      {
        name: "description",
        content: "AI Security Copilot experimental session preview (Roadmap Preview).",
      },
      {
        property: "og:title",
        content: "Copilot Session (Experimental Preview) · Velorix Sentinel",
      },
      {
        property: "og:description",
        content: "AI Security Copilot experimental session preview (Roadmap Preview).",
      },
    ],
  }),
  component: AssistantSessionPage,
});

function AssistantSessionPage() {
  const { sessionId } = Route.useParams();
  return <CopilotWorkspace key={sessionId} sessionId={sessionId} />;
}
