import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute } from "@tanstack/react-router";
import { MailCheck, Send } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { AuthError } from "@/components/auth";
import { Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthLayout } from "@/layouts";
import { authService, toApiError } from "@/services";
import type { ApiError } from "@/types/api";
import { APP_NAME, ROUTES } from "@/utils/constants";
import { forgotPasswordSchema, type ForgotPasswordFormValues } from "@/utils/validation";

const TITLE = `Reset your password — ${APP_NAME}`;
const DESCRIPTION = `Request a password reset link for your ${APP_NAME} account.`;

export const Route = createFileRoute("/forgot-password")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { name: "robots", content: "noindex" },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.forgotPassword },
    ],
    links: [{ rel: "canonical", href: ROUTES.forgotPassword }],
  }),
  component: ForgotPasswordPage,
});

function ForgotPasswordPage() {
  const [error, setError] = useState<ApiError | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await authService.forgotPassword(values);
      setSentTo(values.email);
    } catch (err) {
      setError(toApiError(err));
    }
  });

  return (
    <AuthLayout
      title={sentTo ? "Check your inbox" : "Forgot your password?"}
      subtitle={
        sentTo
          ? undefined
          : "Enter the email tied to your account and we'll send a secure reset link."
      }
      footer={
        <Link to={ROUTES.login} className="focus-ring rounded text-primary hover:underline">
          Back to sign in
        </Link>
      }
    >
      {sentTo ? (
        <div className="space-y-4 text-sm">
          <span className="grid size-12 place-items-center rounded-2xl border border-success/30 bg-success/10 text-success">
            <MailCheck className="size-5" aria-hidden="true" />
          </span>
          <p className="text-muted-foreground">
            If an account exists for <span className="text-foreground">{sentTo}</span>, a password
            reset link is on its way. The link expires shortly for your security.
          </p>
          <Button variant="outline" className="w-full" onClick={() => setSentTo(null)}>
            Use a different email
          </Button>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="space-y-5">
          <AuthError error={error} />

          <div className="space-y-2">
            <Label htmlFor="email">Work email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@company.com"
              aria-invalid={Boolean(errors.email)}
              {...register("email")}
            />
            {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? <Spinner className="size-4" /> : <Send className="size-4" />}
            {isSubmitting ? "Sending link…" : "Send reset link"}
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
