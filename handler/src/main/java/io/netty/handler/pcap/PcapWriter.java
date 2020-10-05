/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

final class PcapWriter implements Closeable {

    /**
     * Logger
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PcapWriter.class);

    /**
     * {@link OutputStream} where we'll write Pcap data.
     */
    private final OutputStream outputStream;

    /**
     * Set to {@code true} if {@link #outputStream} is closed.
     */
    private boolean isClosed;

    /**
     * This uses {@link OutputStream} for writing Pcap.
     * Pcap Global Header is not written on construction.
     */
    PcapWriter(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    /**
     * This uses {@link OutputStream} for writing Pcap.
     * Pcap Global Header is also written on construction.
     *
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    PcapWriter(OutputStream outputStream, ByteBuf byteBuf) throws IOException {
        this.outputStream = outputStream;

        PcapHeaders.writeGlobalHeader(byteBuf);
        byteBuf.readBytes(outputStream, byteBuf.readableBytes());
    }

    /**
     * Write Packet in Pcap OutputStream.
     *
     * @param packetHeaderBuf Packer Header {@link ByteBuf}
     * @param packet          Packet
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    void writePacket(ByteBuf packetHeaderBuf, ByteBuf packet) throws IOException {
        if (isClosed) {
            logger.debug("Pcap Write attempted on closed PcapWriter");
        }

        long timestamp = System.currentTimeMillis();

        PcapHeaders.writePacketHeader(
                packetHeaderBuf,
                (int) (timestamp / 1000L),
                (int) (timestamp % 1000L * 1000L),
                packet.readableBytes(),
                packet.readableBytes()
        );

        packetHeaderBuf.readBytes(outputStream, packetHeaderBuf.readableBytes());
        packet.readBytes(outputStream, packet.readableBytes());
    }

    @Override
    public void close() throws IOException {
        if (isClosed) {
            logger.debug("PcapWriter is already closed");
        } else {
            isClosed = true;
            outputStream.flush();
            outputStream.close();
            logger.debug("PcapWriter is now closed");
        }
    }
}
