import { Lock, ShieldCheck, Users } from "lucide-react";

import { AwaitingAdminData } from "./AwaitingAdminData";
import { cn } from "@/lib/utils";
import type { RoleDefinition } from "@/types/admin";

const LEVEL_TONE: Record<RoleDefinition["privilegeLevel"], string> = {
  CRITICAL: "border-primary/40 bg-primary/10 text-primary",
  HIGH: "border-amber-500/30 bg-amber-500/10 text-amber-400",
  MEDIUM: "border-sky-500/30 bg-sky-500/10 text-sky-400",
  LOW: "border-border bg-muted/40 text-muted-foreground",
};

const EFFECT_TONE = {
  ALLOW: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400",
  DENY: "border-primary/40 bg-primary/10 text-primary",
  CONDITIONAL: "border-amber-500/30 bg-amber-500/10 text-amber-400",
} as const;

interface RoleCardProps {
  role: RoleDefinition;
  selected?: boolean;
  onSelect?: (role: RoleDefinition) => void;
}

/** Compact role tile used in the RBAC catalogue list. */
export function RoleCard({ role, selected = false, onSelect }: RoleCardProps) {
  return (
    <button
      type="button"
      onClick={() => onSelect?.(role)}
      aria-pressed={selected}
      className={cn(
        "focus-ring w-full rounded-2xl border p-4 text-left transition-colors",
        selected
          ? "border-primary/50 bg-primary/5"
          : "border-border/70 bg-card/40 hover:border-primary/30 hover:bg-card/60",
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{role.name}</p>
          <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{role.description}</p>
        </div>
        <span
          className={cn(
            "shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium tracking-wide uppercase",
            LEVEL_TONE[role.privilegeLevel],
          )}
        >
          {role.privilegeLevel}
        </span>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-3 text-[11px] text-muted-foreground">
        <span className="inline-flex items-center gap-1">
          <Lock className="size-3" aria-hidden="true" />
          {role.scope.toLowerCase()}
        </span>
        <span className="inline-flex items-center gap-1">
          <ShieldCheck className="size-3" aria-hidden="true" />
          {role.permissions.length} permission groups
        </span>
        <span className="inline-flex items-center gap-1">
          <Users className="size-3" aria-hidden="true" />
          {role.assignedUsers ?? "Awaiting Live Data"}
        </span>
      </div>
    </button>
  );
}

/** Detailed permission breakdown for the selected role. */
export function PermissionMatrix({ role }: { role: RoleDefinition }) {
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <MetaTile label="Scope" value={role.scope} />
        <MetaTile label="Privilege" value={role.privilegeLevel} />
        <MetaTile label="Type" value={role.builtIn ? "Built-in" : "Custom template"} />
      </div>

      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface/60">
            <tr>
              <th className="px-4 py-2.5 text-left text-xs font-medium tracking-wider text-muted-foreground uppercase">
                Resource
              </th>
              <th className="px-4 py-2.5 text-left text-xs font-medium tracking-wider text-muted-foreground uppercase">
                Actions
              </th>
              <th className="px-4 py-2.5 text-left text-xs font-medium tracking-wider text-muted-foreground uppercase">
                Effect
              </th>
            </tr>
          </thead>
          <tbody>
            {role.permissions.map((permission) => (
              <tr key={permission.resource} className="border-t border-border/70">
                <td className="px-4 py-2.5 text-xs font-medium">{permission.resource}</td>
                <td className="px-4 py-2.5">
                  <div className="flex flex-wrap gap-1">
                    {permission.actions.map((action) => (
                      <span
                        key={action}
                        className="rounded-md border border-border bg-muted/30 px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground"
                      >
                        {action}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-2.5">
                  <span
                    className={cn(
                      "rounded-full border px-2 py-0.5 text-[10px] font-medium tracking-wide uppercase",
                      EFFECT_TONE[permission.effect],
                    )}
                  >
                    {permission.effect}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div>
        <p className="mb-2 text-xs font-medium">Resource scope</p>
        <div className="flex flex-wrap gap-1.5">
          {role.resources.map((resource) => (
            <span
              key={resource}
              className="rounded-full border border-border bg-muted/30 px-2.5 py-0.5 text-[11px] text-muted-foreground"
            >
              {resource}
            </span>
          ))}
        </div>
      </div>

      <AwaitingAdminData
        compact
        title="Assigned users unavailable"
        detail="Role assignment counts and membership lists load from the administration backend. Role editing ships with the same release."
      />
    </div>
  );
}

function MetaTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border/70 bg-muted/10 px-3 py-2.5">
      <p className="text-[10px] tracking-wide text-muted-foreground uppercase">{label}</p>
      <p className="mt-1 text-xs font-medium capitalize">{value.toLowerCase()}</p>
    </div>
  );
}
