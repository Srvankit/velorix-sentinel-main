import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";

import { AdminSection, PermissionMatrix, RoleCard } from "@/components/admin";
import { BUILT_IN_ROLES } from "@/data/admin-reference";
import { ADMIN_WRITE_READY } from "@/services/adminService";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/admin/roles")({
  head: () => ({
    meta: [
      { title: "Roles & Permissions · Velorix Sentinel" },
      {
        name: "description",
        content: "Role-based access control matrix with built-in SOC roles and permission scopes.",
      },
      { property: "og:title", content: "Roles & Permissions · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Role-based access control matrix with built-in SOC roles and permission scopes.",
      },
    ],
  }),
  component: RolesPage,
});

function RolesPage() {
  const [selectedId, setSelectedId] = useState(BUILT_IN_ROLES[0].id);
  const selected = BUILT_IN_ROLES.find((role) => role.id === selectedId) ?? BUILT_IN_ROLES[0];

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <AdminSection
        title="Role Catalogue"
        description="Built-in roles shipped with the platform, plus the custom role template."
        bodyClassName="space-y-2.5"
      >
        {BUILT_IN_ROLES.map((role) => (
          <RoleCard
            key={role.id}
            role={role}
            selected={role.id === selectedId}
            onSelect={(next) => setSelectedId(next.id)}
          />
        ))}
      </AdminSection>

      <AdminSection
        title={`${selected.name} — Privilege Summary`}
        description={selected.description}
        actions={
          <Button size="sm" variant="outline" disabled={!ADMIN_WRITE_READY}>
            Edit permissions
          </Button>
        }
      >
        <PermissionMatrix role={selected} />
      </AdminSection>
    </div>
  );
}
