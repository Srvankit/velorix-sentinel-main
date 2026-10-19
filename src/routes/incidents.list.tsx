import { createFileRoute } from "@tanstack/react-router";
import { LayoutList, UserPlus, Workflow } from "lucide-react";
import { toast } from "sonner";

import { CreateCaseDialog, IncidentCard, IncidentTable } from "@/components/incidents";
import { PageHeader } from "@/components/common";
import { Button } from "@/components/ui/button";
import { useAnalystWorkspace } from "@/hooks/useAnalystWorkspace";
import { useIncidentResource } from "@/hooks/useIncidentResource";
import { incidentService } from "@/services/incidentService";

export const Route = createFileRoute("/incidents/list")({
  head: () => ({
    meta: [
      { title: "Incident Queue · Velorix Sentinel" },
      {
        name: "description",
        content: "Filterable queue of security incidents with severity, status and owner.",
      },
      { property: "og:title", content: "Incident Queue · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Filterable queue of security incidents with severity, status and owner.",
      },
    ],
  }),
  component: IncidentListPage,
});

const UNAVAILABLE = "Available after backend integration.";

function IncidentListPage() {
  const { starredIncidentIds, toggleStar } = useAnalystWorkspace();
  const incidents = useIncidentResource((signal) => incidentService.list({ size: 100 }, signal));

  const rows = incidents.data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Incidents"
        description="Every case raised across detection, intelligence and manual reporting."
        actions={<CreateCaseDialog />}
      />

      <IncidentCard
        title="Incident Queue"
        description="Search, filter, sort and action cases in bulk"
        icon={LayoutList}
      >
        <IncidentTable
          incidents={rows}
          isLoading={incidents.status === "loading"}
          awaiting={incidents.status === "awaiting"}
          starredIds={starredIncidentIds}
          onToggleStar={toggleStar}
          bulkActions={(selectedIds, clear) => (
            <>
              <Button
                variant="outline"
                size="sm"
                disabled
                title={UNAVAILABLE}
                onClick={() => toast.info("Bulk assign", { description: UNAVAILABLE })}
              >
                <UserPlus className="size-4" aria-hidden="true" />
                Assign
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled
                title={UNAVAILABLE}
                onClick={() => toast.info("Bulk status change", { description: UNAVAILABLE })}
              >
                <Workflow className="size-4" aria-hidden="true" />
                Change status ({selectedIds.length})
              </Button>
              <Button variant="ghost" size="sm" onClick={clear}>
                Clear
              </Button>
            </>
          )}
        />
      </IncidentCard>
    </div>
  );
}
