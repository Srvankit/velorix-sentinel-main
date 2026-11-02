import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, Shield, Lock, FileText, Database, Trash2, Mail } from "lucide-react";

import { Footer, Navbar } from "@/components/landing";
import { Button } from "@/components/ui/button";
import { APP_NAME, COMPANY_NAME, ROUTES } from "@/utils/constants";

const TITLE = `Privacy Policy · ${APP_NAME}`;
const DESCRIPTION = `Privacy Policy and data governance practices for ${APP_NAME} by ${COMPANY_NAME}.`;

export const Route = createFileRoute("/privacy")({
  head: () => ({
    meta: [
      { title: TITLE },
      { name: "description", content: DESCRIPTION },
      { property: "og:title", content: TITLE },
      { property: "og:description", content: DESCRIPTION },
      { property: "og:url", content: ROUTES.privacy },
    ],
    links: [{ rel: "canonical", href: ROUTES.privacy }],
  }),
  component: PrivacyPage,
});

function PrivacyPage() {
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
              <Shield className="size-3.5" /> Data Protection & Privacy
            </div>
            <h1 className="text-3xl font-bold tracking-tight sm:text-4xl lg:text-5xl">
              Privacy Policy
            </h1>
            <p className="text-sm text-muted-foreground">
              Last updated: {lastUpdated} · Effective immediately for all {APP_NAME} users.
            </p>
          </div>

          <article className="prose prose-invert mt-10 max-w-none space-y-10 text-sm leading-relaxed text-muted-foreground">
            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground">1. Introduction</h2>
              <p>
                {COMPANY_NAME} (&quot;we&quot;, &quot;our&quot;, or &quot;us&quot;) operates {APP_NAME}, a deep packet inspection and network threat detection platform. We are committed to safeguarding your privacy and ensuring transparent data governance.
              </p>
              <p>
                This Privacy Policy describes how we collect, process, store, and protect information when you visit our website, register an analyst account, or upload network packet capture files (.pcap, .pcapng) for automated threat analysis.
              </p>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <FileText className="size-4 text-primary" /> 2. Information We Collect
              </h2>
              <p>We collect only the minimum information necessary to provide and secure our threat detection services:</p>
              <ul className="list-disc pl-6 space-y-2">
                <li>
                  <strong className="text-foreground">Account Information:</strong> When you register, we collect your name, email address, and an encrypted hash of your password (stored using BCrypt). If you authenticate using OAuth providers (such as GitHub or Google), we receive your verified email and unique provider identifier.
                </li>
                <li>
                  <strong className="text-foreground">Network Capture Files (PCAP / PCAPNG):</strong> When you submit files for inspection, we store the binary capture data and extract packet metadata (including IP headers, transport protocols, port numbers, packet counts, frame timestamps, and protocol payload signatures).
                </li>
                <li>
                  <strong className="text-foreground">Security & Operational Logs:</strong> For fraud prevention and rate limiting, our reverse proxies record request timestamps, client IP addresses verified via trusted reverse proxy verification, and security events (such as token rotation and password resets).
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground">3. How We Use Your Information</h2>
              <p>We process your data exclusively for legitimate operational and security purposes:</p>
              <ul className="list-disc pl-6 space-y-2">
                <li>Executing deterministic rule-based threat detection algorithms against submitted captures.</li>
                <li>Generating interactive analyst reports, packet decodes, protocol distributions, and risk scores.</li>
                <li>Enforcing per-user capture storage quotas and account verification safeguards.</li>
                <li>Defending system infrastructure against credential stuffing, brute-force attacks, and CPU-DoS abuse.</li>
                <li>Sending critical transactional communications, such as account verification emails and password rotation alerts.</li>
              </ul>
              <p>
                We do <strong className="text-foreground">not</strong> sell your personal data or submitted packet captures to third parties, advertising networks, or data brokers.
              </p>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <Lock className="size-4 text-primary" /> 4. Data Storage, Security & Isolation
              </h2>
              <p>
                All packet captures and user artifacts are stored in private cloud object storage with strict tenant isolation and owner-scoped access control.
              </p>
              <ul className="list-disc pl-6 space-y-2">
                <li>
                  <strong className="text-foreground">Tenant Isolation:</strong> Every upload is tagged with the authenticated user&apos;s unique identifier. Access control checks enforce ownership on every read, download, analysis, or deletion request.
                </li>
                <li>
                  <strong className="text-foreground">Encryption:</strong> All data in transit is protected using modern TLS 1.3 encryption. Authentication tokens use signed JWTs and cryptographically secure random session tokens.
                </li>
                <li>
                  <strong className="text-foreground">Integrity Verification:</strong> Capture files are verified using SHA-256 cryptographic digests upon upload to detect tampering or transmission corruption.
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <Trash2 className="size-4 text-primary" /> 5. Data Retention & Right to Erasure
              </h2>
              <p>
                You maintain complete control over your data lifecycle:
              </p>
              <ul className="list-disc pl-6 space-y-2">
                <li>
                  <strong className="text-foreground">Capture Deletion:</strong> You may delete individual capture files at any time via the web interface or API, which immediately releases your storage quota.
                </li>
                <li>
                  <strong className="text-foreground">Account Self-Service Deletion:</strong> In accordance with GDPR and CCPA &quot;Right to Erasure&quot; principles, you can permanently delete your account directly from Account Settings. Account deletion triggers a complete cascade: permanently purging all user records, authentication tokens, analysis reports, and physical binary capture files from cloud storage.
                </li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <Database className="size-4 text-primary" /> 6. Third-Party Subprocessors
              </h2>
              <p>
                We partner with vetted infrastructure providers to deliver {APP_NAME}:
              </p>
              <ul className="list-disc pl-6 space-y-2">
                <li>Cloud hosting and compute providers (for application execution and container orchestration).</li>
                <li>Object storage providers (for tenant-isolated, durable PCAP storage).</li>
                <li>Transactional email service providers (solely for dispatching verification and security notices).</li>
              </ul>
            </section>

            <section className="space-y-3">
              <h2 className="text-lg font-semibold text-foreground flex items-center gap-2">
                <Mail className="size-4 text-primary" /> 7. Contact Us
              </h2>
              <p>
                If you have questions regarding this Privacy Policy or wish to exercise your data protection rights, please contact our privacy and security team:
              </p>
              <div className="rounded-xl border border-border bg-card/60 p-4 font-mono text-xs text-foreground">
                <p>Velorix Technologies — Privacy & Security Office</p>
                <p className="mt-1">Email: privacy@velorix.tech / support@velorix.tech</p>
              </div>
            </section>
          </article>
        </div>
      </main>

      <Footer />
    </div>
  );
}
