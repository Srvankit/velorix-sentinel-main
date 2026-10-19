import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute, useNavigate } from "@tanstack/react-router";
import { KeyRound, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AuthError, PasswordInput, PasswordStrength } from "@/components/auth";
import { Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { AuthLayout } from "@/layouts";
import { authService, toApiError } from "@/services";
import type { ApiError } from "@/types/api";
import { APP_NAME, ROUTES } from "@/utils/constants";
import { resetPasswordSchema, type ResetPasswordFormValues } from "@/utils/validation";

const TITLE = `Set a new password — ${APP_NAME}`;
const DESCRIPTION = `Choose a new password for your ${APP_NAME} account.`;

const searchSchema = z.object({ token: z.string().optional() });

export const Route = createFileRoute("/reset-password")({
  validateSearch: searchSchema,
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { name: "robots", content: "noindex" },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.resetPassword },
    ],
    links: [{ rel: "canonical", href: ROUTES.resetPassword }],
  }),
  component: ResetPasswordPage,
});

function ResetPasswordPage() {
  const { token } = Route.useSearch();
  const navigate = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  const password = watch("password") ?? "";

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    if (!token) {
      setError({
        status: 400,
        code: "MISSING_TOKEN",
        message: "This reset link is invalid. Request a new one to continue.",
      });
      return;
    }
    try {
      await authService.resetPassword({ token, newPassword: values.password });
      setDone(true);
    } catch (err) {
      setError(toApiError(err));
    }
  });

  if (done) {
    return (
      <AuthLayout title="Password updated" subtitle="You can now sign in with your new password.">
        <div className="space-y-5 text-sm">
          <span className="grid size-12 place-items-center rounded-2xl border border-success/30 bg-success/10 text-success">
            <ShieldCheck className="size-5" aria-hidden="true" />
          </span>
          <p className="text-muted-foreground">
            For your security, every other active session has been invalidated.
          </p>
          <Button className="w-full" onClick={() => navigate({ to: ROUTES.login, replace: true })}>
            Continue to sign in
          </Button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Set a new password"
      subtitle="Choose a strong password you haven't used before."
      footer={
        <Link to={ROUTES.login} className="focus-ring rounded text-primary hover:underline">
          Back to sign in
        </Link>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        <AuthError error={error} />

        {!token && (
          <p className="rounded-xl border border-warning/40 bg-warning/10 px-3.5 py-3 text-sm text-warning">
            This page needs a valid reset link.{" "}
            <Link to={ROUTES.forgotPassword} className="underline">
              Request a new one
            </Link>
            .
          </p>
        )}

        <div className="space-y-2">
          <Label htmlFor="password">New password</Label>
          <PasswordInput
            id="password"
            autoComplete="new-password"
            placeholder="••••••••••"
            aria-invalid={Boolean(errors.password)}
            {...register("password")}
          />
          <PasswordStrength value={password} />
          {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
        </div>

        <div className="space-y-2">
          <Label htmlFor="confirmPassword">Confirm new password</Label>
          <PasswordInput
            id="confirmPassword"
            autoComplete="new-password"
            placeholder="••••••••••"
            aria-invalid={Boolean(errors.confirmPassword)}
            {...register("confirmPassword")}
          />
          {errors.confirmPassword && (
            <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>
          )}
        </div>

        <Button type="submit" className="w-full" disabled={isSubmitting || !token}>
          {isSubmitting ? <Spinner className="size-4" /> : <KeyRound className="size-4" />}
          {isSubmitting ? "Updating password…" : "Update password"}
        </Button>
      </form>
    </AuthLayout>
  );
}
