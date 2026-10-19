import { createFileRoute, Outlet } from "@tanstack/react-router";

export const Route = createFileRoute("/reports/compliance")({
  head: () => ({
    meta: [
      { title: "Compliance · Velorix Sentinel" },
      {
        name: "description",
        content: "Compliance framework coverage and control assessment status.",
      },
      { property: "og:title", content: "Compliance · Velorix Sentinel" },
      {
        property: "og:description",
        content: "Compliance framework coverage and control assessment status.",
      },
    ],
  }),
  component: () => <Outlet />,
});
