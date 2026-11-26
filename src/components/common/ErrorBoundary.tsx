import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, Mail, RotateCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { getFeedbackEmail } from "@/utils/feedback";

interface Props {
  children: ReactNode;
  /** Shown in place of the crashed subtree. */
  title?: string;
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Builds a sanitized mailto link for crash reports.
 * Strictly includes only origin and pathname (never query search or hash fragments)
 * to ensure sensitive tokens (e.g., verification or password reset tokens) are never leaked.
 * The error message is truncated to at most 300 characters and contains no stack trace.
 */
export function buildCrashReportMailto(feedbackEmail: string, errorMessage: string): string {
  const safeMessage = (errorMessage || "Unknown error").slice(0, 300);
  const safeLocation =
    typeof window !== "undefined" && window.location
      ? `${window.location.origin}${window.location.pathname}`
      : "unknown";

  const subject = "Velorix Sentinel Crash Report";
  const body = `Error: ${safeMessage}\nLocation: ${safeLocation}`;
  return `mailto:${feedbackEmail}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

/**
 * Widget-level error boundary. Keeps one failing panel from taking down the
 * whole dashboard.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  private reset = () => this.setState({ error: null });

  render() {
    if (!this.state.error) return this.props.children;
    if (this.props.fallback) return this.props.fallback;

    const feedbackEmail = getFeedbackEmail();

    return (
      <div
        role="alert"
        className="flex flex-col items-center gap-3 rounded-2xl border border-destructive/30 bg-destructive/5 px-6 py-10 text-center"
      >
        <span className="grid size-11 place-items-center rounded-xl bg-destructive/15 text-destructive">
          <AlertTriangle className="size-5" aria-hidden="true" />
        </span>
        <h3 className="text-sm font-semibold">
          {this.props.title ?? "This panel could not be displayed"}
        </h3>
        <p className="max-w-md text-xs text-muted-foreground">{this.state.error.message}</p>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={this.reset}>
            <RotateCw className="size-4" aria-hidden="true" />
            Retry
          </Button>
          {feedbackEmail && (
            <Button variant="ghost" size="sm" asChild>
              <a href={buildCrashReportMailto(feedbackEmail, this.state.error.message)}>
                <Mail className="size-4" aria-hidden="true" />
                Report issue
              </a>
            </Button>
          )}
        </div>
      </div>
    );
  }
}
