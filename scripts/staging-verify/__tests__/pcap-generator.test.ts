/**
 * Unit tests for synthetic PCAP generator.
 */

import { describe, expect, it } from "bun:test";
import fs from "node:fs";
import path from "node:path";
import {
  buildEthernetTcpFrame,
  buildPcapGlobalHeader,
  generatePcapFile,
} from "../large-pcap-generator.ts";

describe("Synthetic PCAP Generator", () => {
  it("generates standard 24-byte PCAP global header with magic 0xa1b2c3d4", () => {
    const header = buildPcapGlobalHeader();
    expect(header.length).toBe(24);

    // Magic number: 0xa1b2c3d4 (big-endian)
    expect(header.readUInt32BE(0)).toBe(0xa1b2c3d4);
    // Version: 2.4
    expect(header.readUInt16BE(4)).toBe(2);
    expect(header.readUInt16BE(6)).toBe(4);
    // Snaplen: 65535
    expect(header.readUInt32BE(16)).toBe(65535);
    // Network: 1 (Ethernet)
    expect(header.readUInt32BE(20)).toBe(1);
  });

  it("generates valid IPv4 TCP frame with 16-byte packet header", () => {
    // payloadSize = 100
    const packet = buildEthernetTcpFrame(1, [192, 168, 1, 100], [10, 0, 0, 1], 100);
    // 16 byte packet header + 14 byte Ethernet + 20 byte IPv4 + 20 byte TCP + 100 byte payload = 170 bytes
    expect(packet.length).toBe(170);

    // PCAP packet header
    const caplen = packet.readUInt32BE(8);
    const origlen = packet.readUInt32BE(12);
    expect(caplen).toBe(154);
    expect(origlen).toBe(154);

    // Ethernet header: EtherType IPv4 is 0x0800 at offset 16 + 12 = 28
    expect(packet.readUInt16BE(28)).toBe(0x0800);

    // IPv4 header starts at offset 30
    // Version & IHL: 0x45 (IPv4, 5 words)
    expect(packet[30]).toBe(0x45);
    // Protocol: 6 (TCP) at offset 30 + 9 = 39
    expect(packet[39]).toBe(6);

    // TCP header starts at offset 50
    // Src port at offset 50
    expect(packet.readUInt16BE(50)).toBe(40001);
    // Dst port at offset 52
    expect(packet.readUInt16BE(52)).toBe(80);
  });

  it("generates a synthetic PCAP of specified size", async () => {
    const testDir = path.resolve("scripts/staging-verify/scratch");
    await fs.promises.mkdir(testDir, { recursive: true });
    const testFile = path.join(testDir, "test_small.pcap");

    try {
      // 50 KB file
      const result = await generatePcapFile(testFile, 50 * 1024, 1024);
      expect(fs.existsSync(testFile)).toBe(true);
      expect(result.sizeBytes).toBeGreaterThanOrEqual(50 * 1024);
      expect(result.packetCount).toBeGreaterThan(0);

      // Verify file starts with PCAP magic
      const fd = await fs.promises.open(testFile, "r");
      const magicBuf = Buffer.alloc(4);
      await fd.read(magicBuf, 0, 4, 0);
      await fd.close();
      expect(magicBuf.readUInt32BE(0)).toBe(0xa1b2c3d4);
    } finally {
      if (fs.existsSync(testFile)) {
        await fs.promises.unlink(testFile).catch(() => {});
      }
    }
  });
});
