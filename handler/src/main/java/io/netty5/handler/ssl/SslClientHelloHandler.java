/*
 * Copyright 2017 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferUtil;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.TooLongFrameException;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.ObjectUtil;
import io.netty5.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ByteToMessageDecoder} which allows to be notified once a full {@code ClientHello} was received.
 */
public abstract class SslClientHelloHandler<T> extends ByteToMessageDecoder {
    /**
     * The maximum length of client hello message as defined by
     * <a href="https://www.rfc-editor.org/rfc/rfc5246#section-6.2.1">RFC5246</a>.
     */
    public static final int MAX_CLIENT_HELLO_LENGTH = 0xFFFFFF;

    private static final Logger logger = LoggerFactory.getLogger(SslClientHelloHandler.class);

    private final int maxClientHelloLength;
    private boolean handshakeFailed;
    private boolean suppressRead;
    private ReadBufferAllocator pendingReadBufferAllocator;
    private Buffer handshakeBuffer;

    public SslClientHelloHandler() {
        this(MAX_CLIENT_HELLO_LENGTH);
    }

    protected SslClientHelloHandler(int maxClientHelloLength) {
        // 16MB is the maximum as per RFC:
        // See https://www.rfc-editor.org/rfc/rfc5246#section-6.2.1
        this.maxClientHelloLength =
                ObjectUtil.checkInRange(maxClientHelloLength, 0, MAX_CLIENT_HELLO_LENGTH, "maxClientHelloLength");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        // TODO It ought to be possible to simplify this by using split() to grab the handshakes,
        //  and avoid awkward copying and offsets book-keeping.
        if (!suppressRead && !handshakeFailed) {
            try {
                int readerIndex = in.readerOffset();
                int readableBytes = in.readableBytes();
                int handshakeLength = -1;

                // Check if we have enough data to determine the record type and length.
                while (readableBytes >= SslUtils.SSL_RECORD_HEADER_LENGTH) {
                    final int contentType = in.getUnsignedByte(readerIndex);
                    switch (contentType) {
                        case SslUtils.SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
                            // fall-through
                        case SslUtils.SSL_CONTENT_TYPE_ALERT:
                            final int len = SslUtils.getEncryptedPacketLength(in, readerIndex);

                            // Not an SSL/TLS packet
                            if (len == SslUtils.NOT_ENCRYPTED) {
                                handshakeFailed = true;
                                NotSslRecordException e = new NotSslRecordException(
                                        "not an SSL/TLS record: " + BufferUtil.hexDump(in));
                                in.skipReadableBytes(in.readableBytes());
                                ctx.fireChannelInboundEvent(new SniCompletionEvent(e));
                                ctx.fireChannelInboundEvent(new SslHandshakeCompletionEvent(e));
                                throw e;
                            }
                            if (len == SslUtils.NOT_ENOUGH_DATA) {
                                // Not enough data
                                return;
                            }
                            // No ClientHello
                            select(ctx, null);
                            return;
                        case SslUtils.SSL_CONTENT_TYPE_HANDSHAKE:
                            final int majorVersion = in.getUnsignedByte(readerIndex + 1);
                            // SSLv3 or TLS
                            if (majorVersion == 3) {
                                int packetLength = in.getUnsignedShort(readerIndex + 3) +
                                        SslUtils.SSL_RECORD_HEADER_LENGTH;

                                if (readableBytes < packetLength) {
                                    // client hello incomplete; try again to decode once more data is ready.
                                    return;
                                }
                                if (packetLength == SslUtils.SSL_RECORD_HEADER_LENGTH) {
                                    select(ctx, null);
                                    return;
                                }

                                final int endOffset = readerIndex + packetLength;

                                // Let's check if we already parsed the handshake length or not.
                                if (handshakeLength == -1) {
                                    if (readerIndex + 4 > endOffset) {
                                        // Need more data to read HandshakeType and handshakeLength (4 bytes)
                                        return;
                                    }

                                    final int handshakeType = in.getUnsignedByte(readerIndex +
                                            SslUtils.SSL_RECORD_HEADER_LENGTH);

                                    // Check if this is a clientHello(1)
                                    // See https://tools.ietf.org/html/rfc5246#section-7.4
                                    if (handshakeType != 1) {
                                        select(ctx, null);
                                        return;
                                    }

                                    // Read the length of the handshake as it may arrive in fragments
                                    // See https://tools.ietf.org/html/rfc5246#section-7.4
                                    handshakeLength = in.getUnsignedMedium(readerIndex +
                                            SslUtils.SSL_RECORD_HEADER_LENGTH + 1);

                                    if (handshakeLength > maxClientHelloLength && maxClientHelloLength != 0) {
                                        TooLongFrameException e = new TooLongFrameException(
                                                "ClientHello length exceeds " + maxClientHelloLength +
                                                        ": " + handshakeLength);
                                        in.skipReadableBytes(in.readableBytes());
                                        ctx.fireChannelInboundEvent(new SniCompletionEvent(e));
                                        SslUtils.handleHandshakeFailure(ctx, null, null, e, true);
                                        throw e;
                                    }
                                    // Consume handshakeType and handshakeLength (this sums up as 4 bytes)
                                    readerIndex += 4;
                                    packetLength -= 4;

                                    if (handshakeLength + 4 + SslUtils.SSL_RECORD_HEADER_LENGTH <= packetLength) {
                                        // We have everything we need in one packet.
                                        // Skip the record header
                                        readerIndex += SslUtils.SSL_RECORD_HEADER_LENGTH;
                                        in.readerOffset(readerIndex);
                                        select(ctx, in.readSplit(handshakeLength));
                                        return;
                                    } else {
                                        if (handshakeBuffer == null) {
                                            handshakeBuffer = ctx.bufferAllocator().allocate(handshakeLength);
                                        } else {
                                            // Reset the buffer offsets, so we can aggregate into it again.
                                            handshakeBuffer.resetOffsets();
                                        }
                                    }
                                }

                                // Combine the encapsulated data in one buffer but not include the SSL_RECORD_HEADER
                                int hsLen = packetLength - SslUtils.SSL_RECORD_HEADER_LENGTH;
                                in.copyInto(readerIndex + SslUtils.SSL_RECORD_HEADER_LENGTH,
                                            handshakeBuffer, handshakeBuffer.writerOffset(), hsLen);
                                handshakeBuffer.skipWritableBytes(hsLen);
                                readerIndex += packetLength;
                                readableBytes -= packetLength;
                                if (handshakeLength <= handshakeBuffer.readableBytes()) {
                                    Buffer clientHello = handshakeBuffer.readerOffset(0).writerOffset(handshakeLength);
                                    handshakeBuffer = null;

                                    select(ctx, clientHello);
                                    return;
                                }
                                break;
                            }
                            // fall-through
                        default:
                            // not tls, ssl or application data
                            select(ctx, null);
                            return;
                    }
                }
            } catch (NotSslRecordException e) {
                // Just rethrow as in this case we also closed the channel and this is consistent with SslHandler.
                throw e;
            } catch (TooLongFrameException e) {
                // Just rethrow as in this case we also closed the channel
                throw e;
            } catch (Exception e) {
                // unexpected encoding, ignore sni and use default
                if (logger.isDebugEnabled()) {
                    logger.debug("Unexpected client hello packet: " + BufferUtil.hexDump(in), e);
                }
                select(ctx, null);
            }
        }
    }

    private void releaseHandshakeBuffer() {
        Resource.dispose(handshakeBuffer);
        handshakeBuffer = null;
    }

    private void select(final ChannelHandlerContext ctx, Buffer clientHello) {
        final Future<T> future;
        try {
            future = lookup(ctx, clientHello);
            if (future.isDone()) {
                Resource.dispose(clientHello); // Future is completed. We can dispose it immediately.
                onLookupComplete(ctx, future);
            } else {
                suppressRead = true;
                future.addListener(f -> {
                    Resource.dispose(clientHello); // Delay disposing until the future completes.
                    try {
                        suppressRead = false;
                        try {
                            onLookupComplete(ctx, f);
                        } catch (DecoderException err) {
                            ctx.fireChannelExceptionCaught(err);
                        } catch (Exception cause) {
                            ctx.fireChannelExceptionCaught(new DecoderException(cause));
                        } catch (Throwable cause) {
                            ctx.fireChannelExceptionCaught(cause);
                        }
                    } finally {
                        ReadBufferAllocator pendingReadBufferAllocator = this.pendingReadBufferAllocator;
                        if (pendingReadBufferAllocator != null) {
                            this.pendingReadBufferAllocator = null;
                            ctx.read(pendingReadBufferAllocator);
                        }
                    }
                });
            }
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        releaseHandshakeBuffer();

        super.handlerRemoved0(ctx);
    }

    /**
     * Kicks off a lookup for the given {@code ClientHello} and returns a {@link Future} which in turn will
     * notify the {@link #onLookupComplete(ChannelHandlerContext, Future)} on completion.
     *
     * See https://tools.ietf.org/html/rfc5246#section-7.4.1.2
     *
     * <pre>
     * struct {
     *    ProtocolVersion client_version;
     *    Random random;
     *    SessionID session_id;
     *    CipherSuite cipher_suites<2..2^16-2>;
     *    CompressionMethod compression_methods<1..2^8-1>;
     *    select (extensions_present) {
     *        case false:
     *            struct {};
     *        case true:
     *            Extension extensions<0..2^16-1>;
     *    };
     * } ClientHello;
     * </pre>
     *
     * @see #onLookupComplete(ChannelHandlerContext, Future)
     */
    protected abstract Future<T> lookup(ChannelHandlerContext ctx, Buffer clientHello) throws Exception;

    /**
     * Called upon completion of the {@link #lookup(ChannelHandlerContext, Buffer)} {@link Future}.
     *
     * @see #lookup(ChannelHandlerContext, Buffer)
     */
    protected abstract void onLookupComplete(ChannelHandlerContext ctx, Future<? extends T> future) throws Exception;

    @Override
    public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
        if (suppressRead) {
            pendingReadBufferAllocator = readBufferAllocator;
        } else {
            ctx.read(readBufferAllocator);
        }
    }
}
