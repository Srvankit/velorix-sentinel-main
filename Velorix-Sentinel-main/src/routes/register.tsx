import { zodResolver } from "@hookform/resolvers/zod";
import { Link, createFileRoute, useNavigate } from "@tanstack/react-router";
import { UserPlus } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { AuthError, OAuthButtons, PasswordInput, PasswordStrength } from "@/components/auth";
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
import { registerSchema, type RegisterFormValues } from "@/utils/validation";

const TITLE = `Create an account — ${APP_NAME}`;
const DESCRIPTION = `Create a ${APP_NAME} account to start inspecting network traffic with AI-assisted threat detection.`;

export const Route = createFileRoute("/register")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.register },
    ],
    links: [{ rel: "canonical", href: ROUTES.register }],
  }),
  component: RegisterPage,
});

function RegisterPage() {
  const { register: registerAccount } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<ApiError | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      firstName: "",
      lastName: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const password = watch("password") ?? "";

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await registerAccount({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
        confirmPassword: values.confirmPassword,
      });
      await navigate({ to: ROUTES.dashboard, replace: true });
    } catch (err) {
      const apiError = toApiError(err);
      Object.entries(apiError.fieldErrors ?? {}).forEach(([field, message]) => {
        if (field in values) {
          setFieldError(field as keyof RegisterFormValues, { message });
        }
      });
      setError(apiError);
    }
  });

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Provision a workspace for your security team in a few seconds."
      footer={
        <>
          Already registered?{" "}
          <Link to={ROUTES.login} className="focus-ring rounded text-primary hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-5">
        <AuthError error={error} />

        <div className="grid gap-5 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="firstName">First name</Label>
            <Input
              id="firstName"
              autoComplete="given-name"
              placeholder="Alex"
              aria-invalid={Boolean(errors.firstName)}
              {...register("firstName")}
            />
            {errors.firstName && (
              <p className="text-xs text-destructive">{errors.firstName.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="lastName">Last name</Label>
            <Input
              id="lastName"
              autoComplete="family-name"
              placeholder="Mercer"
              aria-invalid={Boolean(errors.lastName)}
              {...register("lastName")}
            />
            {errors.lastName && (
              <p className="text-xs text-destructive">{errors.lastName.message}</p>
            )}
          </div>
        </div>

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
          <Label htmlFor="password">Password</Label>
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
          <Label htmlFor="confirmPassword">Confirm password</Label>
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

        <div className="flex items-start gap-2.5">
          <Checkbox
            id="acceptTerms"
            className="mt-0.5"
            checked={watch("acceptTerms") === true}
            onCheckedChange={(checked) =>
              setValue("acceptTerms", (checked === true) as true, { shouldValidate: true })
            }
          />
          <Label
            htmlFor="acceptTerms"
            className="text-sm font-normal leading-relaxed text-muted-foreground"
          >
            I agree to the Terms of Service and Privacy Policy.
          </Label>
        </div>
        {errors.acceptTerms && (
          <p className="text-xs text-destructive">{errors.acceptTerms.message}</p>
        )}

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? <Spinner className="size-4" /> : <UserPlus className="size-4" />}
          {isSubmitting ? "Creating account…" : "Create account"}
        </Button>

        <OAuthButtons action="Sign up" />
      </form>
    </AuthLayout>
  );
}
