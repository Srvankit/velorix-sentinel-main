import { Link } from "@tanstack/react-router";
import { CalendarClock, LogOut, Mail, Settings, ShieldCheck, UserRound } from "lucide-react";

import { StatusBadge } from "@/components/common/StatusBadge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";
import { formatDateTime, getInitials } from "@/utils/format";

/** Premium identity card for the signed-in analyst. */
export function UserProfilePanel({ className }: { className?: string }) {
  const { user, logout } = useAuth();
  if (!user) return null;

  return (
    <section className={cn("glass-panel relative isolate overflow-hidden rounded-2xl", className)}>
      <div className="h-20 bg-gradient-brand opacity-80" aria-hidden="true" />
      <div className="-mt-10 p-5">
        <div className="grid grid-cols-[auto_minmax(0,1fr)] items-end gap-3">
          <span className="grid size-16 shrink-0 place-items-center rounded-2xl border-4 border-background bg-gradient-brand font-display text-lg font-semibold text-primary-foreground">
            {getInitials(user.fullName)}
          </span>
          <div className="min-w-0 pb-1">
            <p className="truncate font-display text-lg font-semibold tracking-tight">
              {user.fullName}
            </p>
            <p className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
              <Mail className="size-3 shrink-0" aria-hidden="true" />
              <span className="truncate">{user.email}</span>
            </p>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/40 bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
            <ShieldCheck className="size-3" aria-hidden="true" />
            {user.role}
          </span>
          <StatusBadge
            status={user.active ? "ACTIVE" : "SUSPENDED"}
            tone={user.active ? "success" : "danger"}
          />
          <StatusBadge
            status={user.verified ? "VERIFIED" : "UNVERIFIED"}
            tone={user.verified ? "info" : "warning"}
          />
        </div>

        <dl className="mt-4 space-y-2 text-xs">
          <div className="flex items-center justify-between gap-2">
            <dt className="flex items-center gap-1.5 text-muted-foreground">
              <CalendarClock className="size-3.5" aria-hidden="true" />
              Member since
            </dt>
            <dd className="font-mono">{formatDateTime(user.createdAt)}</dd>
          </div>
          <div className="flex items-center justify-between gap-2">
            <dt className="flex items-center gap-1.5 text-muted-foreground">
              <CalendarClock className="size-3.5" aria-hidden="true" />
              Last activity
            </dt>
            <dd className="font-mono">{formatDateTime(user.updatedAt)}</dd>
          </div>
        </dl>

        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link to="/profile">
              <UserRound className="size-4" aria-hidden="true" />
              Profile
            </Link>
          </Button>
          <Button variant="outline" size="sm" asChild>
            <Link to="/settings">
              <Settings className="size-4" aria-hidden="true" />
              Settings
            </Link>
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="col-span-2 text-muted-foreground"
            onClick={() => void logout()}
          >
            <LogOut className="size-4" aria-hidden="true" />
            Sign out
          </Button>
        </div>
      </div>
    </section>
  );
}
