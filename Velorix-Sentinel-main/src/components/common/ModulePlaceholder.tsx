import type { LucideIcon } from "lucide-react";

import { ProtectedRoute } from "@/components/auth";
import { EmptyState, PageHeader } from "@/components/common";
import { DashboardLayout } from "@/layouts";
import type { UserRole } from "@/types/auth";

interface ModulePlaceholderProps {
  title: string;
  description: string;
  icon: LucideIcon;
  /** Restrict the page to specific roles. */
  roles?: UserRole[];
}

/**
 * Shell for modules that are navigable but not yet implemented. It keeps sidebar
 * links honest without inventing data: no fake metrics, just an explicit state.
 */
export function ModulePlaceholder({ title, description, icon, roles }: ModulePlaceholderProps) {
  return (
    <ProtectedRoute roles={roles}>
      <DashboardLayout>
        <PageHeader title={title} description={description} />
        <EmptyState
          className="mt-8"
          icon={icon}
          title={`${title} is coming next`}
          description="This module is part of the Velorix Sentinel roadmap and will connect to live backend data once implemented."
        />
      </DashboardLayout>
    </ProtectedRoute>
  );
}
