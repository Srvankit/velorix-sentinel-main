/** Duration and throughput formatting shared by the inspection views. */

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || !Number.isFinite(ms) || ms < 0) return "—";
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 2 : 1)} s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  if (minutes < 60) return `${minutes}m ${rest}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

export function formatBitrate(bitsPerSecond: number): string {
  if (!Number.isFinite(bitsPerSecond) || bitsPerSecond <= 0) return "0 bps";
  const units = ["bps", "Kbps", "Mbps", "Gbps"];
  const exponent = Math.min(Math.floor(Math.log(bitsPerSecond) / Math.log(1000)), units.length - 1);
  return `${(bitsPerSecond / 1000 ** exponent).toFixed(exponent === 0 ? 0 : 2)} ${units[exponent]}`;
}

/** Renders an endpoint as `ip:port`, or just the address when no port applies. */
export function formatEndpoint(ip: string, port: number | null | undefined): string {
  return port === null || port === undefined ? ip : `${ip}:${port}`;
}
