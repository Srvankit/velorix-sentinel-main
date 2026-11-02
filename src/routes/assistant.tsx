import { Link, Outlet, createFileRoute } from "@tanstack/react-router";
import { ArrowLeft, CheckCircle2, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";

import { ProtectedRoute } from "@/components/auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/assistant")({
  component: AssistantLayout,
  head: () => ({
    meta: [
      { title: `AI Security Copilot (Experimental / Join Waitlist) · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Experimental preview for the Velorix Sentinel AI Security Copilot. Join the waitlist for early access to post-beta LLM integration.",
      },
      {
        property: "og:title",
        content: `AI Security Copilot (Experimental / Join Waitlist) · ${APP_NAME}`,
      },
      {
        property: "og:description",
        content:
          "Experimental roadmap preview for the AI Security Copilot. No live LLM inference is connected in the public beta.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function AssistantLayout() {
  const [joined, setJoined] = useState(false);

  useEffect(() => {
    try {
      if (typeof window !== "undefined") {
        setJoined(window.localStorage.getItem("velorix.copilot.waitlist_joined") === "true");
      }
    } catch {
      // Storage access failure resilience
    }
  }, []);

  const handleJoinWaitlist = () => {
    try {
      if (typeof window !== "undefined") {
        window.localStorage.setItem("velorix.copilot.waitlist_joined", "true");
      }
      setJoined(true);
    } catch {
      setJoined(true);
    }
  };

  return (
    <ProtectedRoute>
      <DashboardLayout>
        <div className="space-y-4">
          <section
            data-testid="assistant-experimental-banner"
            aria-label="Experimental AI preview and waitlist banner"
            className="flex flex-col gap-4 rounded-xl border border-warning/40 bg-warning/10 p-4 text-sm text-foreground sm:flex-row sm:items-center sm:justify-between"
          >
            <div className="flex items-start gap-3">
              <Sparkles className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden="true" />
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-foreground">
                    AI Security Copilot — Experimental / Join Waitlist
                  </span>
                  <Badge
                    variant="outline"
                    className="border-warning/50 bg-warning/20 font-mono text-[10px] uppercase text-warning"
                  >
                    Roadmap Preview
                  </Badge>
                </div>
                <p className="text-xs text-muted-foreground">
                  No live LLM model (e.g. Gemini / OpenAI API) is connected during this public beta.
                  Prompts are stored locally in browser storage for interface testing. Join the waitlist for priority access to AI-assisted threat triage.
                </p>
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-2">
              {joined ? (
                <div
                  data-testid="waitlist-joined-badge"
                  className="inline-flex items-center gap-1.5 rounded-lg border border-success/40 bg-success/15 px-3 py-1.5 text-xs font-medium text-success"
                >
                  <CheckCircle2 className="size-3.5" aria-hidden="true" />
                  Waitlist Joined
                </div>
              ) : (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleJoinWaitlist}
                  data-testid="join-waitlist-btn"
                  className="border-warning/40 hover:bg-warning/20 hover:text-warning"
                >
                  <Sparkles className="mr-1.5 size-3.5" aria-hidden="true" />
                  Join Waitlist
                </Button>
              )}
              <Button asChild size="sm" variant="ghost">
                <Link to="/dashboard">
                  <ArrowLeft className="mr-1.5 size-3.5" aria-hidden="true" />
                  Back to Dashboard
                </Link>
              </Button>
            </div>
          </section>

          <Outlet />
        </div>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
