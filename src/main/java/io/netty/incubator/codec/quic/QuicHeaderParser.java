/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

import static io.netty.incubator.codec.quic.Quiche.allocateNativeOrder;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Parses the QUIC packet header and notifies a callback once parsing was successful.
 *
 * Once the parser is not needed anymore the user needs to call {@link #close()} to ensure all resources are
 * released. Failed to do so may lead to memory leaks.
 *
 * This class can be used for advanced use-cases. Usually you want to just use {@link QuicClientCodecBuilder} or
 * {@link QuicServerCodecBuilder}.
 */
public final class QuicHeaderParser implements AutoCloseable {
    private final int maxTokenLength;
    private final int localConnectionIdLength;
    private final ByteBuf versionBuffer;
    private final ByteBuf typeBuffer;
    private final ByteBuf scidLenBuffer;
    private final ByteBuf scidBuffer;
    private final ByteBuf dcidLenBuffer;
    private final ByteBuf dcidBuffer;
    private final ByteBuf tokenBuffer;
    private final ByteBuf tokenLenBuffer;
    private boolean closed;

    public QuicHeaderParser(int maxTokenLength, int localConnectionIdLength) {
        Quic.ensureAvailability();
        this.maxTokenLength = checkPositiveOrZero(maxTokenLength, "maxTokenLength");
        this.localConnectionIdLength = checkPositiveOrZero(localConnectionIdLength, "localConnectionIdLength");
        versionBuffer = allocateNativeOrder(Integer.BYTES);
        typeBuffer = allocateNativeOrder(Byte.BYTES);
        scidBuffer = allocateNativeOrder(Quiche.QUICHE_MAX_CONN_ID_LEN);
        scidLenBuffer = allocateNativeOrder(Integer.BYTES);
        dcidBuffer = allocateNativeOrder(Quiche.QUICHE_MAX_CONN_ID_LEN);
        dcidLenBuffer = allocateNativeOrder(Integer.BYTES);
        tokenLenBuffer = allocateNativeOrder(Integer.BYTES);
        tokenBuffer = allocateNativeOrder(maxTokenLength);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            versionBuffer.release();
            typeBuffer.release();
            scidBuffer.release();
            scidLenBuffer.release();
            dcidBuffer.release();
            dcidLenBuffer.release();
            tokenLenBuffer.release();
            tokenBuffer.release();
        }
    }

    /**
     * Parses a QUIC packet and extract the header values out of it. This method takes no ownership of the packet itself
     * which means the caller of this method is expected to call {@link ByteBuf#release()} once the packet is not needed
     * anymore.
     *
     * @param sender        the sender of the packet. This is directly passed to the {@link QuicHeaderProcessor} once
     *                      parsing was successful.
     * @param recipient     the recipient of the packet.This is directly passed to the {@link QuicHeaderProcessor} once
     *                      parsing was successful.
     * @param packet        raw QUIC packet itself. The ownership of the packet is not transferred. This is directly
     *                      passed to the {@link QuicHeaderProcessor} once parsing was successful.
     * @param callback      the {@link QuicHeaderProcessor} that is called once a QUIC packet could be parsed and all
     *                      the header values be extracted.
     * @throws Exception    thrown if we couldn't parse the header or if the {@link QuicHeaderProcessor} throws an
     *                      exception.
     */
    public void parse(InetSocketAddress sender,
                      InetSocketAddress recipient, ByteBuf packet, QuicHeaderProcessor callback) throws Exception {
        if (closed) {
            throw new IllegalStateException("QuicHeaderParser is already closed");
        }
        long contentAddress = Quiche.memoryAddress(packet) + packet.readerIndex();
        int contentReadable = packet.readableBytes();

        // Ret various len values so quiche_header_info can make use of these.
        scidLenBuffer.setInt(0, Quiche.QUICHE_MAX_CONN_ID_LEN);
        dcidLenBuffer.setInt(0, Quiche.QUICHE_MAX_CONN_ID_LEN);
        tokenLenBuffer.setInt(0, maxTokenLength);

        int res = Quiche.quiche_header_info(contentAddress, contentReadable, localConnectionIdLength,
                Quiche.memoryAddress(versionBuffer), Quiche.memoryAddress(typeBuffer),
                Quiche.memoryAddress(scidBuffer), Quiche.memoryAddress(scidLenBuffer),
                Quiche.memoryAddress(dcidBuffer), Quiche.memoryAddress(dcidLenBuffer),
                Quiche.memoryAddress(tokenBuffer), Quiche.memoryAddress(tokenLenBuffer));
        if (res >= 0) {
            int version = versionBuffer.getInt(0);
            byte type = typeBuffer.getByte(0);
            int scidLen = scidLenBuffer.getInt(0);
            int dcidLen = dcidLenBuffer.getInt(0);
            int tokenLen = tokenLenBuffer.getInt(0);

            callback.process(sender, recipient, packet, QuicPacketType.of(type), version,
                    scidBuffer.setIndex(0, scidLen),
                    dcidBuffer.setIndex(0, dcidLen),
                    tokenBuffer.setIndex(0, tokenLen));
        } else {
            throw Quiche.newException(res);
        }
    }

    /**
     * Called when a QUIC packet and its header could be parsed.
     */
    public interface QuicHeaderProcessor {

        /**
         * Called when a QUIC packet header was parsed.
         *
         * @param sender        the sender of the QUIC packet.
         * @param recipient     the recipient of the QUIC packet.
         * @param packet        the raw QUIC packet. The ownership is not transferred, which means you will need to call
         *                      {@link ByteBuf#retain()} on it if you want to keep a reference after this method
         *                      returns.
         * @param type          the type of the packet.
         * @param version       the version of the packet.
         * @param scid          the source connection id. The ownership is not transferred and its generally not allowed
         *                      to hold any references to this buffer outside of the method as it will be re-used.
         * @param dcid          the destination connection id. The ownership is not transferred and its generally not
         *                      allowed to hold any references to this buffer outside of the method as it will be
         *                      re-used.
         * @param token         the token.The ownership is not transferred and its generally not allowed
         *                      to hold any references to this buffer outside of the method as it will be re-used.
         * @throws Exception    throws if an error happens during processing.
         */
        void process(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf packet,
                     QuicPacketType type, int version, ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception;
    }
}
