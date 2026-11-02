import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, FileCheck, Scale, ShieldAlert, Cpu, AlertCircle, Mail } from "lucide-react";

import { Footer, Navbar } from "@/components/landing";
import { Button } from "@/components/ui/button";
import { APP_NAME, COMPANY_NAME, ROUTES } from "@/utils/constants";

const TITLE = `Terms of Service · ${APP_NAME}`;
const DESCRIPTION = `Terms of Service and conditions of use for ${APP_NAME} by ${COMPANY_NAME}.`;

export const Route = createFileRoute("/terms")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.terms },
    ],
    links: [{ rel: "canonical", href: ROUTES.terms }],
  }),
  component: TermsPage,
});

function TermsPage() {
  const lastUpdated = "November 2, 2026";

  return (
    <div className="min-h-dvh bg-background text-foreground flex flex-col">
      <Navbar />

      <main className="flex-1 px-6 pb-24 pt-32 sm:pt-40">
        <div className="mx-auto max-w-4xl">
          <div className="mb-8">
            <Button asChild variant="ghost" size="sm" className="-ml-3 text-muted-foreground hover:text-foreground">
              <Link to="/">
                <ArrowLeft className="mr-2 size-4" />
                Back to Home
              </Link>
            </Button>
          </div>

          <div className="space-y-4 border-b border-border pb-8">
            <div className="inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
              <Scale className="size-3.5" /> Legal Terms & Agreements
            </div>
            <h1 className="text-3xl font-bold tracking-tight sm:text-4xl lg:text-5xl">
              Terms of Service
            </h1>
            <p className="text-sm text-muted-foreground">
              Last updated: {lastUpdated} · Please review carefully before using {APP_NAME}.
            </p>
          </div>

          <article className="prose prose-invert mt-10 max-w-none space-y-10 text-sm leading-relaxed text-muted-foreground">
            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground">1. Acceptance of Terms</h2>
              <p>
                By creating an account, accessing, or utilizing {APP_NAME} (the &quot;Service&quot;), provided by {COMPANY_NAME} (&quot;Company&quot;, &quot;we&quot;, &quot;our&quot;), you agree to be legally bound by these Terms of Service. If you are entering into these terms on behalf of a company or legal entity, you represent that you have the authority to bind such entity.
              </p>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <FileCheck className="size-4 text-primary" /> 2. Lawful Use & Capture Authorization
              </h2>
              <p>
                {APP_NAME} is designed exclusively for authorized network security analysis, forensic investigation, and defensive research. You explicitly agree that:
              </p>
              <ul className="list-disc pl-6 space-y-2">
                <li>
                  You possess valid, verified legal authority, ownership, or explicit organizational consent to inspect and upload all network packet captures (.pcap, .pcapng) submitted to the Service.
                </li>
                <li>
                  You will not upload intercepts collected in violation of applicable wiretapping, privacy, or communications intercept statutes.
                </li>
                <li>
                  You will not submit files containing malicious software designed to exploit vulnerabilities within our parsing and inspection engine.
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <ShieldAlert className="size-4 text-primary" /> 3. Account Security & Verification
              </h2>
              <p>
                To access capture upload and analysis capabilities, you must register an account and complete mandatory email verification.
              </p>
              <ul className="list-disc pl-6 space-y-2">
                <li>You agree to provide accurate, truthful registration information.</li>
                <li>You are responsible for maintaining the confidentiality of your authentication credentials and tokens.</li>
                <li>Passwords must comply with our security policy (minimum 10 characters, including uppercase, lowercase, numbers, and special symbols).</li>
                <li>You must notify us immediately upon discovering any unauthorized session activity or security incident.</li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <Cpu className="size-4 text-primary" /> 4. Storage Quotas & Abuse Prevention
              </h2>
              <p>
                To maintain platform stability and protect system compute resources:
              </p>
              <ul className="list-disc pl-6 space-y-2">
                <li>
                  <strong className="text-foreground">Per-User Quotas:</strong> Analyst accounts are subject to storage and count quotas (defaulting to 500 MB cumulative capture storage and a maximum of 10 active capture files). Uploads exceeding these limits are rejected with 409 Conflict until capacity is released.
                </li>
                <li>
                  <strong className="text-foreground">Rate Limiting:</strong> All authentication and resource endpoints enforce rate limits. Repeated violations or automated brute-force attempts will result in automated IP or account throttling.
                </li>
                <li>
                  <strong className="text-foreground">Stuck-Run Recovery:</strong> The platform enforces processing timeouts. Tasks exceeding maximum duration limits are automatically transitioned to failure state to liberate worker threads.
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground">5. Intellectual Property Rights</h2>
              <p>
                You retain all proprietary rights, title, and ownership in the raw network captures you upload to the Service.
              </p>
              <p>
                {COMPANY_NAME} and its licensors retain all intellectual property rights in the {APP_NAME} platform, including all user interfaces, deterministic detection rule engines, threat classifiers, documentation, and system software.
              </p>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <AlertCircle className="size-4 text-primary" /> 6. Beta Disclaimer & Limitation of Liability
              </h2>
              <p>
                {APP_NAME} is currently operating in public beta. The Service is provided on an &quot;AS IS&quot; and &quot;AS AVAILABLE&quot; basis without warranties of any kind, whether express or implied.
              </p>
              <p>
                While our 13 deterministic detection rules are engineered for rigorous detection fidelity, security detections are advisory. {COMPANY_NAME} does not warrant that threat detections will be error-free or that all network anomalies will be detected. In no event shall {COMPANY_NAME} be liable for any indirect, incidental, punitive, or consequential damages resulting from the use of or inability to use the Service.
              </p>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground">7. Termination</h2>
              <p>
                You may terminate your account at any time using the self-service account deletion functionality in Account Settings, which permanently removes your data from our database and cloud storage. We reserve the right to suspend or terminate accounts that breach these Terms or engage in abusive or unlawful behavior.
              </p>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <Mail className="size-4 text-primary" /> 8. Inquiries & Contact
              </h2>
              <p>
                For questions regarding these Terms of Service, please contact our legal and support team:
              </p>
              <div className="rounded-xl border border-border bg-card/60 p-4 font-mono text-xs text-foreground">
                <p>Velorix Technologies — Legal Department</p>
                <p className="mt-1">Email: legal@velorix.tech / support@velorix.tech</p>
              </div>
            </section>
          </article>
        </div>
      </main>

      <Footer />
    </div>
  );
}
