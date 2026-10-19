import { createFileRoute } from "@tanstack/react-router";

import {
  About,
  AiFeatures,
  CtaBanner,
  Faq,
  Features,
  Footer,
  Hero,
  Navbar,
  Pricing,
  Screenshots,
  Stats,
  Workflow,
} from "@/components/landing";

const TITLE = "Velorix Sentinel — AI Deep Packet Inspection & Threat Detection";
const DESCRIPTION =
  "Velorix Sentinel by Velorix Technologies inspects every packet, detects network threats in real time and turns raw traffic into analyst-ready intelligence.";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: "/" },
      { name: "twitter:title", content: TITLE },
      { name: "twitter:description", content: DESCRIPTION },
    ],
    links: [{ rel: "canonical", href: "/" }],
  }),
  component: LandingPage,
});

function LandingPage() {
  return (
    <div className="min-h-dvh bg-background">
      <Navbar />
      <main>
        <Hero />
        <Features />
        <Workflow />
        <AiFeatures />
        <Screenshots />
        <Stats />
        <About />
        <Pricing />
        <Faq />
        <CtaBanner />
      </main>
      <Footer />
    </div>
  );
}
