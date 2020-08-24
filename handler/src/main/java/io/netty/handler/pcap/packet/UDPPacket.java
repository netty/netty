package io.netty.handler.pcap.packet;

import io.netty.buffer.ByteBuf;

public final class UDPPacket {

    /**
     * Create UDP Packet
     *
     * @param byteBuf ByteBuf where Packet data will be set
     * @param payload Payload of this Packet
     * @param srcPort Source Port
     * @param dstPort Destination Port
     */
    public static ByteBuf createPacket(ByteBuf byteBuf, ByteBuf payload, int srcPort, int dstPort) {
        byteBuf.writeShort(srcPort); // Source Port
        byteBuf.writeShort(dstPort); // Destination Port
        byteBuf.writeShort(8 + payload.readableBytes()); // UDP Header Length + Payload Length
        byteBuf.writeShort(0x0001);  // Checksum
        byteBuf.writeBytes(payload); //  Payload of Data
        return byteBuf;
    }
}
