import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ErrorState, SkeletonText, StatusBadge } from "@/components/common";
import { usePacketDetail } from "@/hooks/useAnalysis";
import type { Packet } from "@/types/analysis";
import { formatEndpoint } from "@/utils/analysisFormat";
import { formatBytes, formatDateTime, formatNumber } from "@/utils/format";

interface PacketDetailSheetProps {
  analysisId: string;
  packet: Packet | null;
  onOpenChange: (open: boolean) => void;
}

interface Field {
  label: string;
  value: string;
}

function layer(title: string, fields: Field[]) {
  return { title, fields: fields.filter((field) => field.value !== "—") };
}

/** Layer-by-layer drill-down of one decoded frame. */
export function PacketDetailSheet({ analysisId, packet, onOpenChange }: PacketDetailSheetProps) {
  const { data, isLoading, error, refetch } = usePacketDetail(analysisId, packet?.id ?? null);

  const value = (input: string | number | null | undefined) =>
    input === null || input === undefined || input === "" ? "—" : String(input);

  const layers = data
    ? [
        layer("Ethernet layer", [
          { label: "Source MAC", value: value(data.sourceMac) },
          { label: "Destination MAC", value: value(data.destinationMac) },
          { label: "Ethernet type", value: value(data.networkProtocol) },
        ]),
        layer("IP layer", [
          { label: "Version", value: value(data.networkProtocol) },
          { label: "Source address", value: value(data.sourceIp) },
          { label: "Destination address", value: value(data.destinationIp) },
          { label: "TTL / hop limit", value: value(data.ttl) },
          {
            label: "Checksum",
            value: data.checksum === null ? "—" : `0x${data.checksum.toString(16)}`,
          },
        ]),
        layer("Transport layer", [
          { label: "Protocol", value: value(data.protocol) },
          { label: "Source port", value: value(data.sourcePort) },
          { label: "Destination port", value: value(data.destinationPort) },
          { label: "TCP flags", value: value(data.tcpFlags) },
          { label: "Sequence number", value: value(data.sequenceNumber) },
          { label: "Acknowledgement", value: value(data.acknowledgementNumber) },
          { label: "Window size", value: value(data.windowSize) },
        ]),
        layer("Application layer", [
          { label: "Decoded as", value: value(data.protocol) },
          { label: "Description", value: value(data.info) },
          { label: "Payload size", value: formatBytes(data.payloadSize, 0) },
        ]),
        layer("Frame", [
          { label: "Frame number", value: formatNumber(data.packetNumber) },
          { label: "Captured at", value: formatDateTime(data.timestamp) },
          { label: "Wire length", value: formatBytes(data.packetLength, 0) },
          { label: "Captured length", value: formatBytes(data.capturedLength, 0) },
          { label: "Decode status", value: data.malformed ? "Malformed" : "Fully decoded" },
        ]),
      ]
    : [];

  return (
    <Sheet open={Boolean(packet)} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle className="font-mono text-base">
            Frame #{packet ? formatNumber(packet.packetNumber) : ""}
          </SheetTitle>
          <SheetDescription>
            {packet
              ? `${formatEndpoint(packet.sourceIp, packet.sourcePort)} → ${formatEndpoint(packet.destinationIp, packet.destinationPort)}`
              : ""}
          </SheetDescription>
        </SheetHeader>

        <div className="space-y-5 px-4 pb-8">
          {packet && (
            <div className="flex flex-wrap gap-2">
              <StatusBadge status={packet.protocol} tone="info" />
              <StatusBadge
                status={packet.suspicious ? "SUSPICIOUS" : "CLEAN"}
                tone={packet.suspicious ? "danger" : "success"}
              />
              {packet.malformed && <StatusBadge status="MALFORMED" tone="warning" />}
            </div>
          )}

          {error && <ErrorState error={error} onRetry={() => void refetch()} />}
          {isLoading && <SkeletonText lines={8} />}

          {layers.map((group) => (
            <section
              key={group.title}
              className="rounded-2xl border border-border bg-surface/50 p-4"
            >
              <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                {group.title}
              </h3>
              <dl className="mt-3 grid gap-x-4 gap-y-2 sm:grid-cols-2">
                {group.fields.map((field) => (
                  <div key={field.label} className="min-w-0">
                    <dt className="text-xs text-muted-foreground">{field.label}</dt>
                    <dd className="truncate font-mono text-sm">{field.value}</dd>
                  </div>
                ))}
              </dl>
            </section>
          ))}

          {data && (
            <p className="text-xs text-muted-foreground">
              Raw bytes are not retained after decoding, so a hex dump is not available for stored
              frames.
            </p>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
