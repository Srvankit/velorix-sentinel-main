import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { formatNumber } from "@/utils/format";
import { CHART_COLORS, SEVERITY_COLORS } from "./chart-tokens";

/** Shared chart chrome so every visualisation reads identically. */
const AXIS = {
  stroke: "var(--muted-foreground)",
  fontSize: 11,
  tickLine: false,
  axisLine: false,
} as const;

const TOOLTIP_STYLE = {
  background: "var(--popover)",
  border: "1px solid var(--border)",
  borderRadius: "0.75rem",
  fontSize: "12px",
  color: "var(--popover-foreground)",
} as const;

function shortDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" }).format(date);
}

interface SeriesChartProps<T> {
  data: T[];
  xKey: keyof T & string;
  series: Array<{ key: keyof T & string; label: string; color: string }>;
  height?: number;
}

/** Stacked/overlaid area chart for time series (threat trend, packet volume). */
export function TimeSeriesAreaChart<T extends object>({
  data,
  xKey,
  series,
  height = 240,
}: SeriesChartProps<T>) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
        <defs>
          {series.map((item) => (
            <linearGradient key={item.key} id={`grad-${item.key}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={item.color} stopOpacity={0.45} />
              <stop offset="100%" stopColor={item.color} stopOpacity={0.02} />
            </linearGradient>
          ))}
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
        <XAxis dataKey={xKey} tickFormatter={shortDate} {...AXIS} />
        <YAxis tickFormatter={(value: number) => formatNumber(value)} width={56} {...AXIS} />
        <Tooltip
          contentStyle={TOOLTIP_STYLE}
          labelFormatter={(label: string) => shortDate(label)}
          formatter={(value: number, name: string) => [formatNumber(value), name]}
        />
        {series.length > 1 && <Legend wrapperStyle={{ fontSize: 11 }} />}
        {series.map((item) => (
          <Area
            key={item.key}
            type="monotone"
            dataKey={item.key}
            name={item.label}
            stroke={item.color}
            strokeWidth={2}
            fill={`url(#grad-${item.key})`}
          />
        ))}
      </AreaChart>
    </ResponsiveContainer>
  );
}

interface CategoryChartProps {
  data: Array<{ label: string; count: number }>;
  height?: number;
  layout?: "horizontal" | "vertical";
  colors?: string[];
}

/** Categorical bar chart (protocols, top talkers). */
export function CategoryBarChart({
  data,
  height = 240,
  layout = "vertical",
  colors = CHART_COLORS,
}: CategoryChartProps) {
  const vertical = layout === "vertical";
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart
        data={data}
        layout={vertical ? "vertical" : "horizontal"}
        margin={{ top: 4, right: 12, bottom: 0, left: vertical ? 8 : -12 }}
      >
        <CartesianGrid
          strokeDasharray="3 3"
          stroke="var(--border)"
          horizontal={!vertical}
          vertical={vertical}
        />
        {vertical ? (
          <>
            <XAxis type="number" tickFormatter={(value: number) => formatNumber(value)} {...AXIS} />
            <YAxis type="category" dataKey="label" width={116} {...AXIS} />
          </>
        ) : (
          <>
            <XAxis dataKey="label" {...AXIS} />
            <YAxis tickFormatter={(value: number) => formatNumber(value)} width={56} {...AXIS} />
          </>
        )}
        <Tooltip
          cursor={{ fill: "var(--muted)", opacity: 0.25 }}
          contentStyle={TOOLTIP_STYLE}
          formatter={(value: number) => [formatNumber(value), "Count"]}
        />
        <Bar dataKey="count" radius={vertical ? [0, 6, 6, 0] : [6, 6, 0, 0]} maxBarSize={26}>
          {data.map((entry, index) => (
            <Cell key={entry.label} fill={colors[index % colors.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

interface DonutChartProps {
  data: Array<{ label: string; count: number }>;
  height?: number;
  colorFor?: (label: string, index: number) => string;
}

/** Donut chart used for the severity breakdown. */
export function DistributionDonutChart({ data, height = 240, colorFor }: DonutChartProps) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie
          data={data}
          dataKey="count"
          nameKey="label"
          innerRadius="58%"
          outerRadius="82%"
          paddingAngle={2}
          stroke="var(--background)"
        >
          {data.map((entry, index) => (
            <Cell
              key={entry.label}
              fill={
                colorFor?.(entry.label, index) ??
                SEVERITY_COLORS[entry.label] ??
                CHART_COLORS[index % CHART_COLORS.length]
              }
            />
          ))}
        </Pie>
        <Legend wrapperStyle={{ fontSize: 11 }} />
        <Tooltip
          contentStyle={TOOLTIP_STYLE}
          formatter={(value: number, name: string) => [formatNumber(value), name]}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}
