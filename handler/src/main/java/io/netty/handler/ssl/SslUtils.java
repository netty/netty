/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ReadOnlyByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;

/**
 * Constants for SSL packets.
 */
final class SslUtils {

    /**
     * change cipher spec
     */
    public static final int SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC = 20;

    /**
     * alert
     */
    public static final int SSL_CONTENT_TYPE_ALERT = 21;

    /**
     * handshake
     */
    public static final int SSL_CONTENT_TYPE_HANDSHAKE = 22;

    /**
     * application data
     */
    public static final int SSL_CONTENT_TYPE_APPLICATION_DATA = 23;

    /**
     * the length of the ssl record header (in bytes)
     */
    public static final int SSL_RECORD_HEADER_LENGTH = 5;

    /**
     * Return how much bytes can be read out of the encrypted data. Be aware that this method will not increase
     * the readerIndex of the given {@link ByteBuf}.
     *
     * @param   buffer
     *                  The {@link ByteBuf} to read from. Be aware that it must have at least
     *                  {@link #SSL_RECORD_HEADER_LENGTH} bytes to read,
     *                  otherwise it will throw an {@link IllegalArgumentException}.
     * @return length
     *                  The length of the encrypted packet that is included in the buffer. This will
     *                  return {@code -1} if the given {@link ByteBuf} is not encrypted at all.
     * @throws IllegalArgumentException
     *                  Is thrown if the given {@link ByteBuf} has not at least {@link #SSL_RECORD_HEADER_LENGTH}
     *                  bytes to read.
     */
    static int getEncryptedPacketLength(ByteBuf buffer, int offset) {
        int packetLength = 0;

        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (buffer.getUnsignedByte(offset)) {
            case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
            case SSL_CONTENT_TYPE_ALERT:
            case SSL_CONTENT_TYPE_HANDSHAKE:
            case SSL_CONTENT_TYPE_APPLICATION_DATA:
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                tls = false;
        }

        if (tls) {
            // SSLv3 or TLS - Check ProtocolVersion
            int majorVersion = buffer.getUnsignedByte(offset + 1);
            if (majorVersion == 3) {
                // SSLv3 or TLS
                packetLength = buffer.getUnsignedShort(offset + 3) + SSL_RECORD_HEADER_LENGTH;
                if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false;
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            int headerLength = (buffer.getUnsignedByte(offset) & 0x80) != 0 ? 2 : 3;
            int majorVersion = buffer.getUnsignedByte(offset + headerLength + 1);
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                if (headerLength == 2) {
                    packetLength = (buffer.getShort(offset) & 0x7FFF) + 2;
                } else {
                    packetLength = (buffer.getShort(offset) & 0x3FFF) + 3;
                }
                if (packetLength <= headerLength) {
                    return -1;
                }
            } else {
                return -1;
            }
        }
        return packetLength;
    }

    static void notifyHandshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
        // We have may haven written some parts of data before an exception was thrown so ensure we always flush.
        // See https://github.com/netty/netty/issues/3900#issuecomment-172481830
        ctx.flush();
        ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
        ctx.close();
    }

    /**
     * Fills the {@link ByteBuf} with zero bytes.
     */
    static void zeroout(ByteBuf buffer) {
        if (!(buffer instanceof ReadOnlyByteBuf)) {
            buffer.setZero(0, buffer.capacity());
        }
    }

    /**
     * Fills the {@link ByteBuf} with zero bytes and releases it.
     */
    static void zerooutAndRelease(ByteBuf buffer) {
        zeroout(buffer);
        buffer.release();
    }

    /**
     * Same as {@link Base64#encode(ByteBuf, boolean)} but allows the use of a custom {@link ByteBufAllocator}.
     *
     * @see Base64#encode(ByteBuf, boolean)
     */
    static ByteBuf toBase64(ByteBufAllocator allocator, ByteBuf src) {
        ByteBuf dst = Base64.encode(src, src.readerIndex(),
                src.readableBytes(), true, Base64Dialect.STANDARD, allocator);
        src.readerIndex(src.writerIndex());
        return dst;
    }

    private SslUtils() {
    }
}
