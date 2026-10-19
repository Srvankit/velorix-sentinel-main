import { Globe2 } from "lucide-react";

import { AwaitingFeed } from "./AwaitingFeed";
import type { ThreatMapPoint } from "@/types/intel";
import { cn } from "@/lib/utils";

interface ThreatMapProps {
  points: ThreatMapPoint[];
  countryFilter: string;
  onCountryFilterChange: (value: string) => void;
}

/**
 * Global threat map. The graticule/landmass is a stylised SVG projection —
 * pins render only from real telemetry, never synthesised.
 */
export function ThreatMap({ points, countryFilter, onCountryFilterChange }: ThreatMapProps) {
  const visible = points.filter((point) =>
    countryFilter.trim()
      ? point.countryName.toLowerCase().includes(countryFilter.trim().toLowerCase())
      : true,
  );

  return (
    <div className="space-y-4">
      <div className="relative overflow-hidden rounded-2xl border border-border bg-[radial-gradient(circle_at_50%_35%,color-mix(in_oklab,var(--primary)_10%,transparent),transparent_65%)]">
        <svg
          viewBox="0 0 360 180"
          role="img"
          aria-label="World threat map"
          className="h-[280px] w-full sm:h-[380px]"
        >
          <defs>
            <pattern id="intel-grid" width="15" height="15" patternUnits="userSpaceOnUse">
              <path
                d="M15 0 L0 0 0 15"
                fill="none"
                stroke="currentColor"
                strokeWidth="0.3"
                className="text-border"
              />
            </pattern>
          </defs>
          <rect width="360" height="180" fill="url(#intel-grid)" />
          {[30, 60, 90, 120, 150].map((y) => (
            <line
              key={y}
              x1="0"
              y1={y}
              x2="360"
              y2={y}
              className="stroke-border/60"
              strokeWidth="0.4"
            />
          ))}
          {visible.map((point) => {
            const x = ((point.longitude + 180) / 360) * 360;
            const y = ((90 - point.latitude) / 180) * 180;
            return (
              <g key={point.countryCode}>
                <circle cx={x} cy={y} r={3} className="fill-primary/70" />
                <circle cx={x} cy={y} r={7} className="fill-primary/20">
                  <animate attributeName="r" values="4;10;4" dur="2.6s" repeatCount="indefinite" />
                  <animate
                    attributeName="opacity"
                    values="0.5;0;0.5"
                    dur="2.6s"
                    repeatCount="indefinite"
                  />
                </circle>
              </g>
            );
          })}
        </svg>

        {visible.length === 0 && (
          <div className="pointer-events-none absolute inset-0 grid place-items-center p-6">
            <div className="glass-panel flex max-w-sm flex-col items-center gap-2 p-6 text-center">
              <Globe2 className="size-6 text-primary" aria-hidden="true" />
              <p className="text-sm font-semibold">No live telemetry connected.</p>
              <p className="text-xs text-muted-foreground">
                Attack origins and threat density render here once an intelligence feed streams
                geolocated events.
              </p>
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <label className="flex w-full max-w-xs items-center gap-2 rounded-lg border border-border bg-card/40 px-3 py-2 text-xs">
          <span className="text-muted-foreground">Country</span>
          <input
            value={countryFilter}
            onChange={(event) => onCountryFilterChange(event.target.value)}
            placeholder="Filter by country…"
            className="w-full bg-transparent outline-none placeholder:text-muted-foreground"
          />
        </label>
        <p className={cn("text-xs text-muted-foreground")}>
          {visible.length} origin{visible.length === 1 ? "" : "s"} plotted
        </p>
      </div>

      <AwaitingFeed
        label="No live telemetry connected."
        detail="Threat density, attack origins and country breakdowns activate with the live intelligence feed."
      />
    </div>
  );
}
