import { Link } from "@tanstack/react-router";
import { Github, Linkedin, Mail, Twitter } from "lucide-react";

import { Logo } from "@/components/common";
import { APP_NAME, APP_TAGLINE, COMPANY_NAME } from "@/utils/constants";

const columns = [
  {
    title: "Product",
    links: ["Features", "Platform Workflow", "AI Analysis", "Pricing", "Changelog"],
  },
  { title: "Solutions", links: ["Financial Services", "Telecom", "Energy", "Public Sector"] },
  { title: "Resources", links: ["Documentation", "API Reference", "Threat Research", "Status"] },
  { title: "Company", links: ["About", "Careers", "Security", "Contact"] },
];

const socials = [
  { icon: Twitter, label: "Twitter" },
  { icon: Linkedin, label: "LinkedIn" },
  { icon: Github, label: "GitHub" },
  { icon: Mail, label: "Email" },
];

export function Footer() {
  return (
    <footer id="contact" className="border-t border-border bg-surface/50 px-6 py-16">
      <div className="mx-auto w-full max-w-7xl">
        <div className="grid gap-12 lg:grid-cols-[1.4fr_2.6fr]">
          <div className="space-y-4">
            <Logo />
            <p className="max-w-xs text-sm text-muted-foreground">{APP_TAGLINE}</p>
            <p className="text-sm text-muted-foreground">
              Built by <span className="font-medium text-foreground">{COMPANY_NAME}</span>
            </p>
            <div className="flex gap-2 pt-2">
              {socials.map((s) => (
                <a
                  key={s.label}
                  href="#"
                  aria-label={s.label}
                  className="focus-ring grid size-9 place-items-center rounded-lg border border-border text-muted-foreground transition-colors hover:border-primary/50 hover:text-primary"
                >
                  <s.icon className="size-4" aria-hidden="true" />
                </a>
              ))}
            </div>
          </div>

          <div className="grid gap-8 sm:grid-cols-4">
            {columns.map((col) => (
              <div key={col.title}>
                <h3 className="text-sm font-semibold">{col.title}</h3>
                <ul className="mt-4 space-y-2.5">
                  {col.links.map((l) => (
                    <li key={l}>
                      <a
                        href="#"
                        className="text-sm text-muted-foreground transition-colors hover:text-primary"
                      >
                        {l}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-14 flex flex-col gap-4 border-t border-border pt-8 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <p>
            © {new Date().getFullYear()} {COMPANY_NAME}. {APP_NAME} is a {COMPANY_NAME} product.
          </p>
          <div className="flex flex-wrap gap-6">
            <Link to="/login" className="transition-colors hover:text-primary">
              Sign in
            </Link>
            <Link to="/register" className="transition-colors hover:text-primary">
              Create account
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
