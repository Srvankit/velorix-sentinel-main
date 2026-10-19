import { ShieldCheck } from "lucide-react";

import { cn } from "@/lib/utils";
import { APP_NAME } from "@/utils/constants";

interface LogoProps {
  className?: string;
  showWordmark?: boolean;
  size?: "sm" | "md" | "lg";
}

const sizeMap = {
  sm: { box: "size-8", icon: "size-4", text: "text-base" },
  md: { box: "size-10", icon: "size-5", text: "text-lg" },
  lg: { box: "size-12", icon: "size-6", text: "text-2xl" },
} as const;

export function Logo({ className, showWordmark = true, size = "md" }: LogoProps) {
  const scale = sizeMap[size];

  return (
    <span className={cn("inline-flex items-center gap-3", className)}>
      <span
        className={cn(
          "grid place-items-center rounded-xl bg-gradient-brand text-primary-foreground shadow-lg",
          scale.box,
        )}
      >
        <ShieldCheck className={scale.icon} aria-hidden="true" />
      </span>
      {showWordmark && (
        <span className={cn("font-display font-semibold tracking-tight", scale.text)}>
          Velorix<span className="text-gradient-brand"> Sentinel</span>
        </span>
      )}
      <span className="sr-only">{APP_NAME}</span>
    </span>
  );
}
