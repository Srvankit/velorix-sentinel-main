import type { ReactNode } from "react";

import { Logo } from "@/components/common";
import { APP_TAGLINE } from "@/utils/constants";

interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Split layout for all unauthenticated flows
 * (login, register, forgot password, reset password).
 */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <div className="ambient-grid min-h-dvh">
      <div className="mx-auto flex min-h-dvh w-full max-w-6xl flex-col px-6 py-8">
        <Logo />

        <main className="flex flex-1 items-center justify-center py-12">
          <div className="w-full max-w-md">
            <div className="glass-panel p-8">
              <div className="space-y-1.5 pb-6">
                <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
                {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
              </div>
              {children}
            </div>
            {footer && (
              <div className="pt-6 text-center text-sm text-muted-foreground">{footer}</div>
            )}
          </div>
        </main>

        <footer className="text-center text-xs text-muted-foreground">{APP_TAGLINE}</footer>
      </div>
    </div>
  );
}
