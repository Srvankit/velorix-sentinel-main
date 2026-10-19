import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute, useNavigate } from "@tanstack/react-router";
import { LogIn } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { AuthError, OAuthButtons, PasswordInput } from "@/components/auth";
import { Spinner } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/hooks/useAuth";
import { AuthLayout } from "@/layouts";
import { toApiError } from "@/services";
import type { ApiError } from "@/types/api";
import { APP_NAME, ROUTES } from "@/utils/constants";
import { loginSchema, type LoginFormValues } from "@/utils/validation";

const TITLE = `Sign in — ${APP_NAME}`;
const DESCRIPTION = `Sign in to the ${APP_NAME} console to inspect traffic and review AI threat intelligence.`;

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { name: "robots", content: "noindex" },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.login },
    ],
    links: [{ rel: "canonical", href: ROUTES.login }],
  }),
  component: LoginPage,
});

function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "", rememberMe: false },
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = form;

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await login(values);
      await navigate({ to: ROUTES.dashboard, replace: true });
    } catch (err) {
      const apiError = toApiError(err);
      Object.entries(apiError.fieldErrors ?? {}).forEach(([field, message]) => {
        if (field === "email" || field === "password") {
          setFieldError(field, { message });
        }
      });
      setError(apiError);
    }
  });

  return (
    <AuthLayout
      title="Sign in to your console"
      subtitle="Access live packet inspection and AI-assisted investigations."
      footer={
        <>
          Don&apos;t have an account?{" "}
          <Link to={ROUTES.register} className="focus-ring rounded text-primary hover:underline">
            Create one
          </Link>
        </>
      }
    >
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

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label htmlFor="password">Password</Label>
            <Link
              to={ROUTES.forgotPassword}
              className="focus-ring rounded text-xs text-muted-foreground hover:text-foreground"
            >
              Forgot password?
            </Link>
          </div>
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="••••••••••"
            aria-invalid={Boolean(errors.password)}
            {...register("password")}
          />
          {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
        </div>

        <div className="flex items-center gap-2.5">
          <Checkbox
            id="rememberMe"
            checked={Boolean(watch("rememberMe"))}
            onCheckedChange={(checked) => setValue("rememberMe", checked === true)}
          />
          <Label htmlFor="rememberMe" className="text-sm font-normal text-muted-foreground">
            Keep me signed in on this device
          </Label>
        </div>

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? <Spinner className="size-4" /> : <LogIn className="size-4" />}
          {isSubmitting ? "Signing in…" : "Sign in"}
        </Button>

        <OAuthButtons action="Sign in" />
      </form>
    </AuthLayout>
  );
}
