import { createFileRoute } from "@tanstack/react-router";

import { CopilotWorkspace } from "@/components/copilot";

export const Route = createFileRoute("/assistant/$sessionId")({
  head: () => ({
    meta: [
      { title: "Copilot Session · Velorix Sentinel" },
      { name: "description", content: "Continue an AI Security Copilot investigation session." },
      { property: "og:title", content: "Copilot Session · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Continue an AI Security Copilot investigation session.",
      },
    ],
  }),
  component: AssistantSessionPage,
});

function AssistantSessionPage() {
  const { sessionId } = Route.useParams();
  return <CopilotWorkspace key={sessionId} sessionId={sessionId} />;
}
