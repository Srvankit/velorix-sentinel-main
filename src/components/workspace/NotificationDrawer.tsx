import { AtSign, BellRing, CheckCheck, Info, TriangleAlert, UserPlus } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { AwaitingTelemetry } from "./AwaitingTelemetry";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { SkeletonList } from "@/components/common";
import { useSocResource } from "@/hooks/useSocResource";
import { socService } from "@/services/socService";
import { cn } from "@/lib/utils";
import type { NotificationKind } from "@/types/soc";

const KIND_ICON: Record<NotificationKind, LucideIcon> = {
  MENTION: AtSign,
  ASSIGNMENT: UserPlus,
  WARNING: TriangleAlert,
  SYSTEM: Info,
};

const KIND_TONE: Record<NotificationKind, string> = {
  MENTION: "text-sky-400",
  ASSIGNMENT: "text-emerald-400",
  WARNING: "text-amber-400",
  SYSTEM: "text-muted-foreground",
};

interface NotificationDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** Enterprise notification centre for the SOC workspace. */
export function NotificationDrawer({ open, onOpenChange }: NotificationDrawerProps) {
  const notifications = useSocResource((signal) => socService.listNotifications(signal));
  const items = notifications.data ?? [];
  const unread = items.filter((item) => !item.read).length;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col gap-4 sm:max-w-md">
        <SheetHeader className="space-y-1">
          <SheetTitle className="flex items-center gap-2 text-sm">
            <BellRing className="size-4 text-primary" aria-hidden="true" />
            Notifications
          </SheetTitle>
          <SheetDescription className="text-xs">
            Mentions, case assignments and platform warnings routed to you.
          </SheetDescription>
        </SheetHeader>

        <div className="flex items-center justify-between px-4">
          <span className="text-[11px] text-muted-foreground">
            {unread > 0 ? `${unread} unread` : "Nothing unread"}
          </span>
          <Button
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-[11px]"
            disabled={unread === 0}
            onClick={() => void socService.markAllNotificationsRead()}
          >
            <CheckCheck className="size-3.5" aria-hidden="true" />
            Mark all read
          </Button>
        </div>

        <div className="min-h-0 flex-1 space-y-2 overflow-y-auto px-4 pb-4">
          {notifications.status === "loading" ? (
            <SkeletonList items={4} />
          ) : items.length === 0 ? (
            <AwaitingTelemetry
              icon={BellRing}
              title="No notifications."
              detail="Your notification feed loads from the SOC backend. No placeholder alerts are generated here."
            />
          ) : (
            items.map((item) => {
              const Icon = KIND_ICON[item.kind];
              return (
                <article
                  key={item.id}
                  className={cn(
                    "rounded-xl border border-border/70 bg-card/40 p-3",
                    !item.read && "border-primary/30 bg-primary/5",
                  )}
                >
                  <div className="flex items-start gap-2.5">
                    <Icon
                      className={cn("mt-0.5 size-4 shrink-0", KIND_TONE[item.kind])}
                      aria-hidden="true"
                    />
                    <div className="min-w-0">
                      <h3 className="truncate text-xs font-semibold">{item.title}</h3>
                      <p className="mt-0.5 text-[11px] text-muted-foreground">{item.body}</p>
                      <p className="mt-1 font-mono text-[10px] text-muted-foreground">
                        {new Date(item.createdAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                </article>
              );
            })
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
