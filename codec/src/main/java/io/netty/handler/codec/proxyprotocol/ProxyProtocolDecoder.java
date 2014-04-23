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
package io.netty.handler.codec.proxyprotocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Decodes a proxy protocol header.
 *
 * @see <a href="http://haproxy.1wt.eu/download/1.5/doc/proxy-protocol.txt">Proxy Protocol Specification</a>
 */
public class ProxyProtocolDecoder extends ByteToMessageDecoder {

    /**
     * Maximum possible length of a proxy protocol header.
     */
    private static final int v1MaxLength = 108;
    private static final int v2MaxLength = 271;

    /**
     * Version 1 header delimiter is always '\r\n' per spec.
     */
    private static final int delimLength = 2;

    /**
     * Binary header prefix.
     */
    private static final byte[] binaryPrefix = new byte[]{
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x00,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x51,
            (byte) 0x55,
            (byte) 0x49,
            (byte) 0x54,
            (byte) 0x0A
    };

    /**
     * Binary header prefix length.
     */
    private static final int binaryPrefixLength = binaryPrefix.length;

    /**
     * True if we're discarding input because we're already over maxLength.
     */
    private boolean discarding;

    /**
     * Number of discarded bytes.
     */
    private int discardedBytes;

    /**
     * True if we're finished decoding the proxy protocol header
     */
    private boolean finished;

    /**
     * Protocol specification version
     */
    private int version = -1;

    /**
     * Creates a new decoder.
     */
    public ProxyProtocolDecoder() {
        super();
        setSingleDecode(true);
    }

    /**
     * Returns the proxy protocol specification version in the buffer if the version is found.
     * Returns -1 if no version was found in the buffer.
     */
    private static int findVersion(final ByteBuf buffer) {
        final int n = buffer.readableBytes();
        if (n < 13) {
            return -1;
        }

        int idx = buffer.readerIndex();

        for (int i = 0; i < binaryPrefixLength; i++) {
            final byte b = buffer.getByte(idx + i);
            if (b != binaryPrefix[i]) {
                return 1;
            }
        }

        return buffer.getByte(idx + binaryPrefixLength);
    }

    /**
     * Returns the index in the buffer of the end of header if found.
     * Returns -1 if no end of header was found in the buffer.
     */
    private static int findEndOfHeader(final ByteBuf buffer) {
        final int n = buffer.readableBytes();
        if (n < 16) {
            return -1;
        }

        int offset = buffer.readerIndex() + 15;
        int totalHeaderBytes = 16 + buffer.getByte(offset);
        if (n >= totalHeaderBytes) {
            return totalHeaderBytes;
        } else {
            return -1;
        }
    }

    /**
     * Returns the index in the buffer of the end of line found.
     * Returns -1 if no end of line was found in the buffer.
     */
    private static int findEndOfLine(final ByteBuf buffer) {
        final int n = buffer.writerIndex();
        for (int i = buffer.readerIndex(); i < n; i++) {
            final byte b = buffer.getByte(i);
            if (b == '\r' && i < n - 1 && buffer.getByte(i + 1) == '\n') {
                return i;  // \r\n
            }
        }
        return -1;  // Not found.
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        if (finished) {
            ctx.pipeline().remove(this);
        }
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

        // determine the specification version
        if (version == -1) {
            if ((version = findVersion(in)) == -1) {
                return;
            }
        }

        ByteBuf decoded;

        if (version == 1) {
            decoded = decodeLine(ctx, in);
        } else {
            decoded = decodeStruct(ctx, in);
        }

        if (decoded != null) {
            finished = true;
            try {
                if (version == 1) {
                    out.add(ProxyProtocolMessage.decodeHeader(decoded.toString(CharsetUtil.US_ASCII)));
                } else {
                    out.add(ProxyProtocolMessage.decodeHeader(decoded));
                }
            } catch (ProxyProtocolException e) {
                fail(ctx, null, e);
            }
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     * Based on code from <a href="https://github.com/netty/netty/blob/a8af57742334d2962bba108a0773878a3a4af346/codec/
     * src/main/java/io/netty/handler/codec/LineBasedFrameDecoder.java#L88">LineBasedFrameDecoder</a>.
     *
     * @param ctx     the {@link ChannelHandlerContext} which this {@link ProxyProtocolDecoder} belongs to
     * @param buffer  the {@link ByteBuf} from which to read data
     * @return frame  the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                be created.
     */
    protected ByteBuf decodeStruct(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        final int eoh = findEndOfHeader(buffer);
        if (!discarding) {
            if (eoh >= 0) {
                final ByteBuf frame;
                final int length = eoh - buffer.readerIndex();

                if (length > v2MaxLength) {
                    buffer.readerIndex(eoh);
                    failOverLimit(ctx, length);
                    return null;
                }

                frame = buffer.readSlice(length);

                return frame;
            } else {
                final int length = buffer.readableBytes();
                if (length > v2MaxLength) {
                    discardedBytes = length;
                    buffer.readerIndex(buffer.writerIndex());
                    discarding = true;
                    failOverLimit(ctx, "over " + discardedBytes);
                }
                return null;
            }
        } else {
            if (eoh >= 0) {
                final int length = discardedBytes + eoh - buffer.readerIndex();
                buffer.readerIndex(eoh);
                discardedBytes = 0;
                discarding = false;
            } else {
                discardedBytes = buffer.readableBytes();
                buffer.readerIndex(buffer.writerIndex());
            }
            return null;
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     * Based on code from <a href="https://github.com/netty/netty/blob/a8af57742334d2962bba108a0773878a3a4af346/codec/
     * src/main/java/io/netty/handler/codec/LineBasedFrameDecoder.java#L88">LineBasedFrameDecoder</a>.
     *
     * @param ctx     the {@link ChannelHandlerContext} which this {@link ProxyProtocolDecoder} belongs to
     * @param buffer  the {@link ByteBuf} from which to read data
     * @return frame  the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                be created.
     */
    protected ByteBuf decodeLine(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        final int eol = findEndOfLine(buffer);
        if (!discarding) {
            if (eol >= 0) {
                final ByteBuf frame;
                final int length = eol - buffer.readerIndex();

                if (length > v1MaxLength) {
                    buffer.readerIndex(eol + delimLength);
                    failOverLimit(ctx, length);
                    return null;
                }

                frame = buffer.readSlice(length);
                buffer.skipBytes(delimLength);

                return frame;
            } else {
                final int length = buffer.readableBytes();
                if (length > v1MaxLength) {
                    discardedBytes = length;
                    buffer.readerIndex(buffer.writerIndex());
                    discarding = true;
                    failOverLimit(ctx, "over " + discardedBytes);
                }
                return null;
            }
        } else {
            if (eol >= 0) {
                final int length = discardedBytes + eol - buffer.readerIndex();
                final int delimLength = buffer.getByte(eol) == '\r' ? 2 : 1;
                buffer.readerIndex(eol + delimLength);
                discardedBytes = 0;
                discarding = false;
            } else {
                discardedBytes = buffer.readableBytes();
                buffer.readerIndex(buffer.writerIndex());
            }
            return null;
        }
    }

    private void failOverLimit(final ChannelHandlerContext ctx, int length) {
        failOverLimit(ctx, String.valueOf(length));
    }

    private void failOverLimit(final ChannelHandlerContext ctx, String length) {
        int maxLength = version == 1 ? v1MaxLength : v2MaxLength;
        fail(ctx, "header length (" + length + ") exceeds the allowed maximum (" + maxLength + ")", null);
    }

    private void fail(final ChannelHandlerContext ctx, String errMsg, Throwable t) {
        finished = true;
        ctx.close(); // drop connection immediately per spec
        ProxyProtocolException ppex;
        if (errMsg != null && t != null) {
            ppex = new ProxyProtocolException(errMsg, t);
        } else if (errMsg != null) {
            ppex = new ProxyProtocolException(errMsg);
        } else if (t != null) {
            ppex = new ProxyProtocolException(t);
        } else {
            ppex = new ProxyProtocolException();
        }
        ctx.fireExceptionCaught(ppex);
    }

}
