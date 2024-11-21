/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

    private final PcapWriteHandler pcapWriteHandler;

    /**
     * Reference declared so that we can use this as mutex in clean way.
     */
    private final OutputStream outputStream;

    /**
     * This uses {@link OutputStream} for writing Pcap data.
     *
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    PcapWriter(PcapWriteHandler pcapWriteHandler) throws IOException {
        this.pcapWriteHandler = pcapWriteHandler;
        outputStream = pcapWriteHandler.outputStream();

        // If OutputStream is not shared then we have to write Global Header.
        if (pcapWriteHandler.writePcapGlobalHeader() && !pcapWriteHandler.sharedOutputStream()) {
            PcapHeaders.writeGlobalHeader(pcapWriteHandler.outputStream());
        }
    }

    /**
     * Write Packet in Pcap OutputStream.
     *
     * @param packetHeaderBuf Packer Header {@link ByteBuf}
     * @param packet          Packet
     * @throws IOException If {@link OutputStream#write(byte[])} throws an exception
     */
    void writePacket(ByteBuf packetHeaderBuf, ByteBuf packet) throws IOException {
        if (pcapWriteHandler.state() == State.CLOSED) {
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

        if (pcapWriteHandler.sharedOutputStream()) {
            synchronized (outputStream) {
                packetHeaderBuf.readBytes(outputStream, packetHeaderBuf.readableBytes());
                packet.readBytes(outputStream, packet.readableBytes());
            }
        } else {
            packetHeaderBuf.readBytes(outputStream, packetHeaderBuf.readableBytes());
            packet.readBytes(outputStream, packet.readableBytes());
        }
    }

    @Override
    public String toString() {
        return "PcapWriter{" +
                "outputStream=" + outputStream +
                '}';
    }

    @Override
    public void close() throws IOException {
        if (pcapWriteHandler.state() == State.CLOSED) {
            logger.debug("PcapWriter is already closed");
        } else {
            if (pcapWriteHandler.sharedOutputStream()) {
                synchronized (outputStream) {
                    outputStream.flush();
                }
            } else {
                outputStream.flush();
                outputStream.close();
            }
            pcapWriteHandler.markClosed();
            logger.debug("PcapWriter is now closed");
        }
    }
}
