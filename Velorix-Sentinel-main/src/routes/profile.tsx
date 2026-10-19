import { createFileRoute } from "@tanstack/react-router";

import { ProtectedRoute } from "@/components/auth";
import { PageHeader } from "@/components/common";
import { RecentActivityPanel, UserProfilePanel } from "@/components/soc";
import { DashboardLayout } from "@/layouts";
import { APP_NAME } from "@/utils/constants";

export const Route = createFileRoute("/profile")({
  component: ProfilePage,
  head: () => ({
    meta: [
      { title: `Profile · ${APP_NAME}` },
      {
        name: "description",
        content:
          "Your Velorix Sentinel analyst profile: identity, role, account status and recent security activity.",
      },
      { property: "og:title", content: `Profile · ${APP_NAME}` },
      {
        property: "og:description",
        content: "Analyst identity, role, account status and recent activity.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
});

function ProfilePage() {
  return (
    <ProtectedRoute>
      <DashboardLayout>
        <PageHeader
          title="Profile"
          description="Your account identity, role and recent security activity."
        />
        <div className="mt-6 grid gap-6 lg:grid-cols-[22rem_minmax(0,1fr)]">
          <UserProfilePanel />
          <RecentActivityPanel limit={12} />
        </div>
      </DashboardLayout>
    </ProtectedRoute>
  );
}
