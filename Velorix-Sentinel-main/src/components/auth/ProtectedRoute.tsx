import { useNavigate } from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";

import { PageLoader } from "@/components/common";
import { useAuth } from "@/hooks/useAuth";
import type { UserRole } from "@/types/auth";
import { ROUTES } from "@/utils/constants";

interface ProtectedRouteProps {
  children: ReactNode;
  /** When provided, the user must hold one of these roles. */
  roles?: UserRole[];
}

/**
 * Client-side route guard for every authenticated module.
 *
 * Rendering is deferred until the persisted session has been restored, then:
 *  - expired sessions land on /session-expired
 *  - anonymous visitors land on /login
 *  - authenticated users missing a required role land on /unauthorized
 */
export function ProtectedRoute({ children, roles }: ProtectedRouteProps) {
  const { isAuthenticated, isInitializing, sessionExpired, hasRole } = useAuth();
  const navigate = useNavigate();

  const roleSatisfied = !roles || hasRole(...roles);
  const authorized = isAuthenticated && roleSatisfied;

  useEffect(() => {
    if (isInitializing || authorized) return;

    if (sessionExpired) {
      void navigate({ to: ROUTES.sessionExpired, replace: true });
    } else if (!isAuthenticated) {
      void navigate({ to: ROUTES.login, replace: true });
    } else {
      void navigate({ to: ROUTES.unauthorized, replace: true });
    }
  }, [isInitializing, authorized, isAuthenticated, sessionExpired, navigate]);

  if (isInitializing || !authorized) {
    return <PageLoader label="Verifying session" />;
  }

  return <>{children}</>;
}
