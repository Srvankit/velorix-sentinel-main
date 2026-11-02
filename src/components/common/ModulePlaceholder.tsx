import { Link } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import {
  ArrowLeft,
  CheckCircle2,
  Clock,
  LayoutDashboard,
  ShieldAlert,
  Sparkles,
  UploadCloud,
} from "lucide-react";
import type { ReactNode } from "react";

import { ProtectedRoute } from "@/components/auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DashboardLayout } from "@/layouts";
import type { UserRole } from "@/types/auth";
import { APP_NAME } from "@/utils/constants";

export interface ModulePlaceholderProps {
  title: string;
  description: string;
  icon: LucideIcon;
  /** Phase label (defaults to "Phase 2"). */
  phase?: string;
  /** List of upcoming capabilities planned for this module. */
  plannedFeatures?: string[];
  /** Restrict the page to specific roles. */
  roles?: UserRole[];
  /** Optional custom action to render at the bottom. */
  action?: ReactNode;
}

/**
 * Polished, informative placeholder for deferred routes under active development.
 * Used when users navigate directly via URL to features scheduled for Phase 2.
 * Explains what is coming, lists planned capabilities, and provides clear navigation
 * back to the functional core (Analysis Dashboard, Upload, Threats).
 */
export function ModulePlaceholder({
  title,
  description,
  icon: Icon,
  phase = "Phase 2",
  plannedFeatures,
  roles,
  action,
}: ModulePlaceholderProps) {
  return (
    <ProtectedRoute roles={roles}>
      <DashboardLayout>
        <div className="space-y-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Button
                  asChild
                  variant="ghost"
                  size="sm"
                  className="-ml-2 h-7 px-2 text-xs text-muted-foreground hover:text-foreground"
                >
                  <Link to="/dashboard">
                    <ArrowLeft className="mr-1 size-3.5" />
                    Back to Dashboard
                  </Link>
                </Button>
                <Badge variant="outline" className="border-primary/40 bg-primary/10 text-primary text-[11px]">
                  <Clock className="mr-1 size-3" />
                  {phase} Preview
                </Badge>
              </div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{title}</h1>
              <p className="text-sm text-muted-foreground">{description}</p>
            </div>

            <Button asChild className="shrink-0 gap-2">
              <Link to="/dashboard">
                <LayoutDashboard className="size-4" />
                Go to Analysis Dashboard
              </Link>
            </Button>
          </div>

          <div className="relative overflow-hidden rounded-2xl border border-border bg-card/60 p-6 sm:p-10">
            <div className="ambient-grid pointer-events-none absolute inset-0 opacity-50" aria-hidden />

            <div className="relative z-10 mx-auto max-w-2xl text-center">
              <div className="mx-auto mb-6 grid size-16 place-items-center rounded-2xl border border-primary/30 bg-primary/10 text-primary shadow-lg shadow-primary/5">
                <Icon className="size-8" aria-hidden="true" />
              </div>

              <span className="inline-flex items-center gap-1.5 rounded-full bg-gradient-brand px-3 py-1 text-xs font-semibold text-primary-foreground">
                <Sparkles className="size-3.5" />
                Scheduled for {phase}
              </span>

              <h2 className="mt-4 text-xl font-semibold sm:text-2xl">
                {title} is under active development
              </h2>

              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                During the current public beta, {APP_NAME} focuses on deterministic offline PCAP and
                PCAPNG inspection, 13 automated threat detection rules, and verified capture storage.
                Full interactive {title.toLowerCase()} capabilities will connect to live backend
                services in {phase}.
              </p>

              {plannedFeatures && plannedFeatures.length > 0 && (
                <div className="mt-8 rounded-xl border border-border/70 bg-surface/40 p-5 text-left">
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Planned {phase} Capabilities
                  </h3>
                  <ul className="mt-3 grid gap-2.5 sm:grid-cols-2">
                    {plannedFeatures.map((feature) => (
                      <li key={feature} className="flex items-start gap-2 text-xs text-foreground/90">
                        <CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-primary" aria-hidden="true" />
                        <span>{feature}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
                <Button asChild size="default">
                  <Link to="/dashboard">
                    <LayoutDashboard className="mr-2 size-4" />
                    Return to Dashboard
                  </Link>
                </Button>
                <Button asChild variant="outline" size="default">
                  <Link to="/upload">
                    <UploadCloud className="mr-2 size-4" />
                    Upload Capture
                  </Link>
                </Button>
                <Button asChild variant="outline" size="default">
                  <Link to="/threats">
                    <ShieldAlert className="mr-2 size-4" />
                    View Detected Threats
                  </Link>
                </Button>
              </div>

              {action && <div className="mt-6 pt-4 border-t border-border/50">{action}</div>}
            </div>
          </div>
        </div>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
