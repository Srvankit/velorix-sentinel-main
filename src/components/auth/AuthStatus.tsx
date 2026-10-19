import { Link } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { Logo } from "@/components/common";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { APP_TAGLINE } from "@/utils/constants";

type Tone = "primary" | "success" | "warning";

const TONE_CLASS: Record<Tone, string> = {
  primary: "border-primary/30 bg-primary/10 text-primary",
  success: "border-success/30 bg-success/10 text-success",
  warning: "border-warning/30 bg-warning/10 text-warning",
};

interface AuthStatusProps {
  icon: LucideIcon;
  tone?: Tone;
  code?: string;
  title: string;
  description: string;
  primaryAction?: { label: string; to: string };
  secondaryAction?: { label: string; to: string };
  children?: ReactNode;
}

/**
 * Shared full-page status screen for the non-form auth surfaces:
 * email verified, session expired, unauthorized and 404.
 */
export function AuthStatus({
  icon: Icon,
  tone = "primary",
  code,
  title,
  description,
  primaryAction,
  secondaryAction,
  children,
}: AuthStatusProps) {
  return (
    <div className="ambient-grid min-h-dvh">
      <div className="mx-auto flex min-h-dvh w-full max-w-6xl flex-col px-6 py-8">
        <Link to="/" aria-label="Velorix Sentinel home" className="focus-ring w-fit rounded-xl">
          <Logo />
        </Link>

        <main className="flex flex-1 items-center justify-center py-12">
          <div className="glass-panel w-full max-w-md p-8 text-center">
            <span
              className={cn(
                "mx-auto grid size-14 place-items-center rounded-2xl border",
                TONE_CLASS[tone],
              )}
            >
              <Icon className="size-6" aria-hidden="true" />
            </span>

            {code && (
              <p className="mt-6 font-mono text-xs uppercase tracking-[0.25em] text-muted-foreground">
                {code}
              </p>
            )}

            <h1 className="mt-3 text-2xl font-semibold tracking-tight text-balance">{title}</h1>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground text-pretty">
              {description}
            </p>

            {children}

            <div className="mt-8 flex flex-col gap-2 sm:flex-row sm:justify-center">
              {primaryAction && (
                <Button asChild className="rounded-full">
                  <Link to={primaryAction.to}>{primaryAction.label}</Link>
                </Button>
              )}
              {secondaryAction && (
                <Button asChild variant="outline" className="rounded-full">
                  <Link to={secondaryAction.to}>{secondaryAction.label}</Link>
                </Button>
              )}
            </div>
          </div>
        </main>

        <footer className="text-center text-xs text-muted-foreground">{APP_TAGLINE}</footer>
      </div>
    </div>
  );
}
