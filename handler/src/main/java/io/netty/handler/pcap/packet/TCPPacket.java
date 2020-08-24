package io.netty.handler.pcap.packet;

import io.netty.buffer.ByteBuf;

public final class TCPPacket {
    /**
     * Create TCP Packet
     *
     * @param byteBuf ByteBuf where Packet data will be set
     * @param payload Payload of this Packet
     * @param srcPort Source Port
     * @param dstPort Destination Port
     */
    public static ByteBuf createPacket(ByteBuf byteBuf, ByteBuf payload, int srcPort, int dstPort) {
        byteBuf.writeShort(dstPort); // Destination Port
        byteBuf.writeShort(srcPort); // Source Port
        byteBuf.writeInt(0);         // Sequence Number
        byteBuf.writeInt(0);         // Acknowledgment Number
        byteBuf.writeShort(5 << 12); // Flags
        byteBuf.writeShort(65535);   // Window Size
        byteBuf.writeShort(0x0001);  // Checksum
        byteBuf.writeShort(0);       // Urgent Pointer
        byteBuf.writeBytes(payload); //  Payload of Data
        return byteBuf;
    }
}
