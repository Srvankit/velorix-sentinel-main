import { createFileRoute } from "@tanstack/react-router";
import {
  Bot,
  Cloud,
  Globe,
  KeyRound,
  Mail,
  MessageSquare,
  Radar,
  Search,
  ServerCog,
  Sparkles,
  Terminal,
  Webhook,
  Workflow,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { AdminSection, AwaitingAdminData, IntegrationCard } from "@/components/admin";
import { INTEGRATION_CATALOGUE } from "@/data/admin-reference";

export const Route = createFileRoute("/admin/integrations")({
  head: () => ({
    meta: [
      { title: "API & Integrations · Velorix Sentinel" },
      {
        name: "description",
        content: "Configure API keys, webhooks, SIEM, SOAR and AI provider integrations.",
      },
      { property: "og:title", content: "API & Integrations · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Configure API keys, webhooks, SIEM, SOAR and AI provider integrations.",
      },
    ],
  }),
  component: IntegrationsPage,
});

const ICONS: Record<string, LucideIcon> = {
  "rest-api": Terminal,
  webhooks: Webhook,
  "api-keys": KeyRound,
  siem: ServerCog,
  soar: Workflow,
  slack: MessageSquare,
  teams: MessageSquare,
  email: Mail,
  virustotal: Radar,
  abuseipdb: Globe,
  shodan: Search,
  openai: Sparkles,
  "azure-openai": Cloud,
  "google-gemini": Bot,
};

const GROUPS: { title: string; description: string; categories: string[] }[] = [
  {
    title: "Platform APIs",
    description: "Programmatic access surfaces exposed by Velorix Sentinel.",
    categories: ["API"],
  },
  {
    title: "Security Operations",
    description: "Forward detections and trigger response automation downstream.",
    categories: ["SIEM", "SOAR"],
  },
  {
    title: "Notifications",
    description: "Deliver alerts and case updates to the channels your teams already use.",
    categories: ["MESSAGING", "EMAIL"],
  },
  {
    title: "Threat Intelligence Enrichment",
    description: "External reputation and exposure context for observed indicators.",
    categories: ["THREAT_INTEL"],
  },
  {
    title: "AI Providers",
    description: "Model backends for the AI Security Copilot and automated summarisation.",
    categories: ["AI"],
  },
];

function IntegrationsPage() {
  return (
    <div className="space-y-4">
      {GROUPS.map((group) => {
        const items = INTEGRATION_CATALOGUE.filter((integration) =>
          group.categories.includes(integration.category),
        );
        return (
          <AdminSection key={group.title} title={group.title} description={group.description}>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {items.map((integration) => (
                <IntegrationCard
                  key={integration.id}
                  integration={integration}
                  icon={ICONS[integration.id] ?? ServerCog}
                />
              ))}
            </div>
          </AdminSection>
        );
      })}

      <div className="grid gap-4 lg:grid-cols-2">
        <AdminSection
          title="API Keys"
          description="Scoped service credentials with rotation and last-used tracking."
        >
          <AwaitingAdminData
            icon={KeyRound}
            detail="Issued keys are listed once the API key service ships. No credentials exist in this deployment."
          />
        </AdminSection>
        <AdminSection
          title="Webhook Subscriptions"
          description="Signed outbound event delivery with retry and failure tracking."
        >
          <AwaitingAdminData
            icon={Webhook}
            detail="Endpoints, event selections and delivery health load from the administration backend."
          />
        </AdminSection>
      </div>
    </div>
  );
}
