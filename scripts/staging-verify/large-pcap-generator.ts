/**
 * Streaming generator for valid synthetic PCAP capture files.
 * Generates valid PCAP headers and IPv4/TCP frames up to the requested byte size.
 * Uses Node stream buffers so memory consumption remains under 2 MB regardless of file size.
 */

import fs from "node:fs";
import path from "node:path";

export interface GeneratedPcapResult {
  filePath: string;
  sizeBytes: number;
  packetCount: number;
}

/**
 * Builds standard 24-byte PCAP Global Header.
 * Magic: 0xa1b2c3d4 (standard pcap)
 * Major: 2, Minor: 4
 * Snaplen: 65535
 * LinkType: 1 (Ethernet)
 */
export function buildPcapGlobalHeader(): Buffer {
  const buf = Buffer.alloc(24);
  buf.writeUInt32BE(0xa1b2c3d4, 0); // Magic (big-endian standard)
  buf.writeUInt16BE(2, 4); // Major version
  buf.writeUInt16BE(4, 6); // Minor version
  buf.writeInt32BE(0, 8); // Thiszone
  buf.writeUInt32BE(0, 12); // Sigfigs
  buf.writeUInt32BE(65535, 16); // Snaplen
  buf.writeUInt32BE(1, 20); // LinkType (1 = Ethernet)
  return buf;
}

/**
 * Builds a single standard 16-byte PCAP packet record header followed by an Ethernet + IPv4 + TCP frame.
 */
export function buildEthernetTcpFrame(
  seq: number,
  srcIp: [number, number, number, number] = [192, 168, 1, 100],
  dstIp: [number, number, number, number] = [10, 0, 0, 1],
  payloadSize = 1024,
): Buffer {
  const ethHeaderLen = 14;
  const ipHeaderLen = 20;
  const tcpHeaderLen = 20;
  const frameLen = ethHeaderLen + ipHeaderLen + tcpHeaderLen + payloadSize;

  // 16-byte PCAP Packet Header
  const pcapHeader = Buffer.alloc(16);
  const sec = 1710000000 + Math.floor(seq / 1000);
  const usec = (seq % 1000) * 1000;
  pcapHeader.writeUInt32BE(sec, 0);
  pcapHeader.writeUInt32BE(usec, 4);
  pcapHeader.writeUInt32BE(frameLen, 8); // Captured length
  pcapHeader.writeUInt32BE(frameLen, 12); // Original length

  // Packet data buffer
  const packet = Buffer.alloc(frameLen);

  // 1. Ethernet Header (14 bytes)
  packet.set([0x00, 0x11, 0x22, 0x33, 0x44, 0x55], 0); // Dst MAC
  packet.set([0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb], 6); // Src MAC
  packet.writeUInt16BE(0x0800, 12); // EtherType = IPv4

  // 2. IPv4 Header (20 bytes)
  const ipOffset = 14;
  packet.writeUInt8(0x45, ipOffset); // Version 4, IHL 5
  packet.writeUInt8(0x00, ipOffset + 1); // DSCP/ECN
  packet.writeUInt16BE(ipHeaderLen + tcpHeaderLen + payloadSize, ipOffset + 2); // Total length
  packet.writeUInt16BE(seq & 0xffff, ipOffset + 4); // Identification
  packet.writeUInt16BE(0x4000, ipOffset + 6); // Flags: Don't Fragment
  packet.writeUInt8(64, ipOffset + 8); // TTL
  packet.writeUInt8(6, ipOffset + 9); // Protocol = TCP
  packet.writeUInt16BE(0x0000, ipOffset + 10); // Checksum placeholder
  packet.set(srcIp, ipOffset + 12); // Source IP
  packet.set(dstIp, ipOffset + 16); // Dest IP

  // 3. TCP Header (20 bytes)
  const tcpOffset = ipOffset + 20;
  packet.writeUInt16BE(40000 + (seq % 10000), tcpOffset); // Src Port
  packet.writeUInt16BE(80, tcpOffset + 2); // Dst Port
  packet.writeUInt32BE(1000000 + seq * 1000, tcpOffset + 4); // Seq num
  packet.writeUInt32BE(0, tcpOffset + 8); // Ack num
  packet.writeUInt16BE(0x5018, tcpOffset + 12); // Data offset 5 (20B), Flags: PSH + ACK (0x18)
  packet.writeUInt16BE(8192, tcpOffset + 14); // Window size
  packet.writeUInt16BE(0x0000, tcpOffset + 16); // Checksum
  packet.writeUInt16BE(0x0000, tcpOffset + 18); // Urgent pointer

  // 4. Payload (synthetic bytes)
  const payloadOffset = tcpOffset + 20;
  for (let i = 0; i < payloadSize; i++) {
    packet.writeUInt8((i + seq) % 256, payloadOffset + i);
  }

  return Buffer.concat([pcapHeader, packet]);
}

/**
 * Streams a valid synthetic PCAP capture file to disk.
 */
export async function generatePcapFile(
  targetPath: string,
  targetBytes: number,
  payloadSizePerFrame = 1400,
): Promise<GeneratedPcapResult> {
  const dir = path.dirname(targetPath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  return new Promise((resolve, reject) => {
    const stream = fs.createWriteStream(targetPath);
    const globalHeader = buildPcapGlobalHeader();
    stream.write(globalHeader);

    let bytesWritten = globalHeader.length;
    let packetCount = 0;

    function writeChunk() {
      let ok = true;
      while (bytesWritten < targetBytes && ok) {
        packetCount++;
        const remaining = targetBytes - bytesWritten;
        const currentPayload = Math.min(payloadSizePerFrame, Math.max(16, remaining - 50));
        const frame = buildEthernetTcpFrame(
          packetCount,
          [192, 168, 1, 100],
          [10, 0, 0, 1],
          currentPayload,
        );
        bytesWritten += frame.length;
        ok = stream.write(frame);
      }

      if (bytesWritten >= targetBytes) {
        stream.end(() => {
          resolve({
            filePath: targetPath,
            sizeBytes: bytesWritten,
            packetCount,
          });
        });
      } else {
        stream.once("drain", writeChunk);
      }
    }

    stream.on("error", reject);
    writeChunk();
  });
}

/**
 * Generates a valid ~98 MB PCAP file for Large PCAP verification (under 100 MB limit).
 */
export async function generateValidLargePcap(
  outputDir: string,
  targetBytes = 98 * 1024 * 1024,
): Promise<GeneratedPcapResult> {
  const targetPath = path.join(outputDir, "synthetic_large_98mb.pcap");
  return generatePcapFile(targetPath, targetBytes);
}

/**
 * Generates an oversized ~101 MB PCAP file to verify 413 Payload Too Large rejection.
 */
export async function generateOverLimitPcap(
  outputDir: string,
  targetBytes = 101 * 1024 * 1024,
): Promise<GeneratedPcapResult> {
  const targetPath = path.join(outputDir, "synthetic_overlimit_101mb.pcap");
  return generatePcapFile(targetPath, targetBytes);
}
