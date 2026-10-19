import { createFileRoute } from "@tanstack/react-router";
import {
  Activity,
  Bell,
  Command,
  Gauge,
  ListChecks,
  RadioTower,
  ServerCog,
  TerminalSquare,
  Workflow,
} from "lucide-react";
import { useCallback, useRef, useState } from "react";

import { ProtectedRoute } from "@/components/auth";
import { ErrorBoundary, PageHeader } from "@/components/common";
import { Button } from "@/components/ui/button";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";
import {
  ActivityConsole,
  AlertStream,
  AlertTriageQueue,
  InvestigationPanel,
  NotificationDrawer,
  ProductivityPanel,
  QuickActions,
  SystemStatusPanel,
  WorkspaceCommandPalette,
  WorkspacePanel,
} from "@/components/workspace";
import { DashboardLayout } from "@/layouts";
import { useSocHotkeys } from "@/hooks/useSocHotkeys";
import { useSocResource } from "@/hooks/useSocResource";
import { socService } from "@/services/socService";
import { APP_NAME } from "@/utils/constants";
import type { SocAlert } from "@/types/soc";

export const Route = createFileRoute("/soc")({
  component: SocWorkspacePage,
  head: () => ({
    meta: [
      { title: `SOC Analyst Workspace · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Security operations workspace: live alert stream, triage queue, investigation panel, system health, analyst productivity and activity console.",
      },
      { property: "og:title", content: `SOC Analyst Workspace · ${APP_NAME}` },
      {
        property: "og:description",
        content:
          "Triage alerts, run investigations and monitor platform health from a single security operations workspace.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function SocWorkspacePage() {
  const [selected, setSelected] = useState<SocAlert | null>(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const triageRef = useRef<HTMLDivElement>(null);

  const queue = useSocResource((signal) => socService.getTriageQueue(signal));

  const focusTriage = useCallback(() => {
    triageRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    triageRef.current?.querySelector<HTMLInputElement>('input[type="text"]')?.focus();
  }, []);

  useSocHotkeys({
    onCommandPalette: (event) => {
      event.preventDefault();
      setPaletteOpen(true);
    },
    onSearch: (event) => {
      event.preventDefault();
      focusTriage();
    },
    onEscape: () => {
      setPaletteOpen(false);
      setNotificationsOpen(false);
    },
  });

  return (
    <ProtectedRoute>
      <DashboardLayout>
        <ErrorBoundary>
          <div className="space-y-5">
            <PageHeader
              title="SOC Analyst Workspace"
              description="Live triage, investigation and platform health in one operations console. Press ⌘K for commands, / to search the queue."
              actions={
                <div className="flex flex-wrap items-center gap-1.5">
                  <Button variant="outline" size="sm" onClick={() => setPaletteOpen(true)}>
                    <Command className="size-3.5" aria-hidden="true" />
                    Commands
                    <kbd className="ms-1 rounded border border-border px-1 font-mono text-[10px]">
                      ⌘K
                    </kbd>
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setNotificationsOpen(true)}>
                    <Bell className="size-3.5" aria-hidden="true" />
                    Notifications
                  </Button>
                </div>
              }
            />

            <ResizablePanelGroup
              orientation="horizontal"
              className="hidden min-h-[680px] gap-2 lg:flex"
            >
              <ResizablePanel defaultSize="26" minSize="18">
                <WorkspacePanel
                  title="Live alert stream"
                  description="Critical to informational"
                  icon={RadioTower}
                  scroll
                  className="h-full"
                >
                  <AlertStream selectedId={selected?.id ?? null} onSelect={setSelected} />
                </WorkspacePanel>
              </ResizablePanel>

              <ResizableHandle withHandle />

              <ResizablePanel defaultSize="48" minSize="30">
                <WorkspacePanel
                  title="Investigation"
                  description={selected ? selected.reference : "No alert selected"}
                  icon={Workflow}
                  className="h-full"
                  bodyClassName="flex min-h-0 flex-col"
                >
                  <InvestigationPanel alert={selected} className="flex-1" />
                </WorkspacePanel>
              </ResizablePanel>

              <ResizableHandle withHandle />

              <ResizablePanel defaultSize="26" minSize="18">
                <div className="flex h-full min-h-0 flex-col gap-2">
                  <WorkspacePanel title="System status" icon={ServerCog} scroll>
                    <SystemStatusPanel />
                  </WorkspacePanel>
                  <WorkspacePanel title="Quick actions" icon={Activity} scroll>
                    <QuickActions disabled={!selected} />
                  </WorkspacePanel>
                  <WorkspacePanel
                    title="My productivity"
                    icon={Gauge}
                    scroll
                    className="min-h-0 flex-1"
                  >
                    <ProductivityPanel />
                  </WorkspacePanel>
                </div>
              </ResizablePanel>
            </ResizablePanelGroup>

            <div className="grid gap-3 lg:hidden">
              <WorkspacePanel title="Live alert stream" icon={RadioTower}>
                <AlertStream selectedId={selected?.id ?? null} onSelect={setSelected} />
              </WorkspacePanel>
              <WorkspacePanel title="Investigation" icon={Workflow}>
                <InvestigationPanel alert={selected} />
              </WorkspacePanel>
              <WorkspacePanel title="System status" icon={ServerCog}>
                <SystemStatusPanel />
              </WorkspacePanel>
              <WorkspacePanel title="Quick actions" icon={Activity}>
                <QuickActions disabled={!selected} />
              </WorkspacePanel>
              <WorkspacePanel title="My productivity" icon={Gauge}>
                <ProductivityPanel />
              </WorkspacePanel>
            </div>

            <div ref={triageRef}>
              <WorkspacePanel
                title="Alert triage queue"
                description="Filter, sort and bulk-triage detections"
                icon={ListChecks}
              >
                <AlertTriageQueue
                  alerts={queue.data ?? []}
                  isLoading={queue.status === "loading"}
                  awaiting={queue.status === "awaiting"}
                  selectedAlertId={selected?.id ?? null}
                  onOpen={setSelected}
                  bulkActions={(ids, clear) => (
                    <div className="flex flex-wrap gap-1.5">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled
                        title="Requires the SOC action API."
                      >
                        Acknowledge {ids.length}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled
                        title="Requires the SOC action API."
                      >
                        Assign
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled
                        title="Requires the SOC action API."
                      >
                        Escalate
                      </Button>
                      <Button size="sm" variant="ghost" onClick={clear}>
                        Clear
                      </Button>
                    </div>
                  )}
                />
              </WorkspacePanel>
            </div>

            <WorkspacePanel
              title="Activity console"
              description="System, audit, security and packet engine events"
              icon={TerminalSquare}
            >
              <ActivityConsole className="max-h-72" />
            </WorkspacePanel>
          </div>

          <WorkspaceCommandPalette
            open={paletteOpen}
            onOpenChange={setPaletteOpen}
            onOpenNotifications={() => setNotificationsOpen(true)}
            onFocusTriage={focusTriage}
          />
          <NotificationDrawer open={notificationsOpen} onOpenChange={setNotificationsOpen} />
        </ErrorBoundary>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
