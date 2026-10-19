import { Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { ArrowRight, PlayCircle, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { APP_NAME } from "@/utils/constants";
import { DashboardPreview } from "./DashboardPreview";
import { ParticleField } from "./ParticleField";

export function Hero() {
  return (
    <section id="home" className="relative overflow-hidden px-6 pb-20 pt-32 sm:pt-40">
      <div className="ambient-grid pointer-events-none absolute inset-0 opacity-90" aria-hidden />
      <ParticleField count={28} />

      <div className="relative mx-auto grid w-full max-w-7xl items-center gap-14 lg:grid-cols-2">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          className="flex flex-col gap-6"
        >
          <span className="inline-flex w-fit items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs text-primary">
            <ShieldCheck className="size-3.5" />A Velorix Technologies platform
          </span>

          <h1 className="text-4xl font-semibold leading-[1.08] text-balance sm:text-5xl xl:text-6xl">
            Protect Your Network with{" "}
            <span className="text-gradient-brand">AI-Powered Threat Intelligence</span>
          </h1>

          <p className="max-w-xl text-lg text-muted-foreground text-pretty">
            {APP_NAME} performs deep packet inspection on live traffic and PCAP captures, correlates
            signals across your estate, and turns raw frames into prioritised, analyst-ready
            intelligence — with automated IOC extraction, risk scoring and remediation guidance.
          </p>

          <div className="flex flex-col gap-3 sm:flex-row">
            <Button asChild size="lg" className="rounded-full hover-glow">
              <Link to="/register">
                Create your account
                <ArrowRight className="size-4" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline" className="rounded-full">
              <a href="#features">
                <PlayCircle className="size-4" />
                Explore the platform
              </a>
            </Button>
          </div>
        </motion.div>

        <DashboardPreview />
      </div>
    </section>
  );
}
