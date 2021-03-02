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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.incubator.codec.quic.QuicStreamFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_DATA_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_HEADERS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_PROMISE_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.readVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;

/**
 * Decodes / encodes {@link Http3Frame}s.
 */
final class Http3FrameCodec extends ByteToMessageDecoder implements ChannelOutboundHandler {

    private final long maxHeaderListSize;
    private final QpackDecoder qpackDecoder;
    private final QpackEncoder qpackEncoder;

    private long type = -1;
    private int payLoadLength = -1;

    static Supplier<Http3FrameCodec> newSupplier(QpackDecoder qpackDecoder,
                                                 long maxHeaderListSize, QpackEncoder qpackEncoder) {
        ObjectUtil.checkNotNull(qpackDecoder, "qpackDecoder");
        ObjectUtil.checkNotNull(qpackEncoder, "qpackEncoder");

        // QPACK decoder and encoder are shared between streams in a connection.
        return () ->  new Http3FrameCodec(qpackDecoder, maxHeaderListSize, qpackEncoder);
    }

    Http3FrameCodec(QpackDecoder qpackDecoder, long maxHeaderListSize, QpackEncoder qpackEncoder) {
        this.qpackDecoder = ObjectUtil.checkNotNull(qpackDecoder, "qpackDecoder");
        this.maxHeaderListSize = ObjectUtil.checkPositive(maxHeaderListSize, "maxHeaderListSize");
        this.qpackEncoder = ObjectUtil.checkNotNull(qpackEncoder, "qpackEncoder");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buffer;
        if (msg instanceof QuicStreamFrame) {
            QuicStreamFrame streamFrame = (QuicStreamFrame) msg;
            buffer = streamFrame.content().retain();
            streamFrame.release();
        } else {
            buffer = (ByteBuf) msg;
        }
        super.channelRead(ctx, buffer);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) {
            return;
        }
        if (type == -1) {
            int typeLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            if (in.readableBytes() < typeLen) {
                return;
            }
            type = readVariableLengthInteger(in, typeLen);
            if (Http3CodecUtils.isReservedHttp2FrameType(type)) {
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                        "Reserved type for HTTP/2 received.", true);
                return;
            }
            if (!in.isReadable()) {
                return;
            }
        }
        if (payLoadLength == -1) {
            int payloadLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            assert payloadLen <= 8;
            if (in.readableBytes() < payloadLen) {
                return;
            }
            long len = readVariableLengthInteger(in, payloadLen);
            if (len > Integer.MAX_VALUE) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_EXCESSIVE_LOAD,
                        "Received an invalid frame len.", true);
                return;
            }
            payLoadLength = (int) len;
        }
        int read = decodeFrame(ctx, type, payLoadLength, in, out);
        if (read >= 0) {
            if (read == payLoadLength) {
                type = -1;
                payLoadLength = -1;
            } else {
                payLoadLength -= read;
            }
        }
    }

    private static int skipBytes(ByteBuf in, int payLoadLength) {
        in.skipBytes(payLoadLength);
        return payLoadLength;
    }

    private int decodeFrame(ChannelHandlerContext ctx, long longType, int payLoadLength, ByteBuf in, List<Object> out) {
        if (longType > Integer.MAX_VALUE && !Http3CodecUtils.isReservedFrameType(longType)) {
            return skipBytes(in, payLoadLength);
        }
        int type  = (int) longType;
        // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-11.2.1
        switch (type) {
            case HTTP3_DATA_FRAME_TYPE:
                // DATA
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.1
                int readable = in.readableBytes();
                if (readable == 0 && payLoadLength > 0) {
                    return 0;
                }
                int length = Math.min(readable, payLoadLength);
                out.add(new DefaultHttp3DataFrame(in.readRetainedSlice(length)));
                return length;
            case HTTP3_HEADERS_FRAME_TYPE:
                // HEADERS
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.2

                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        // Let's use the maxHeaderListSize as a limit as this is this is the decompressed amounts of
                        // bytes which means the once we decompressed the headers we will be bigger then the actual
                        // payload size now.
                        maxHeaderListSize, Http3ErrorCode.H3_EXCESSIVE_LOAD)) {
                    return 0;
                }
                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                if (decodeHeaders(ctx, headersFrame.headers(), in.readSlice(payLoadLength))) {
                    out.add(headersFrame);
                }
                return payLoadLength;
            case HTTP3_CANCEL_PUSH_FRAME_TYPE:
                // CANCEL_PUSH
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.3
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        HTTP3_CANCEL_PUSH_FRAME_MAX_LEN, Http3ErrorCode.H3_FRAME_ERROR)) {
                    return 0;
                }
                int pushIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                out.add(new DefaultHttp3CancelPushFrame(readVariableLengthInteger(in, pushIdLen)));
                return payLoadLength;
            case HTTP3_SETTINGS_FRAME_TYPE:
                // SETTINGS
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4

                // Use 256 as this gives space for 16 maximal size encoder and 128 minimal size encoded settings.
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength, HTTP3_SETTINGS_FRAME_MAX_LEN,
                        Http3ErrorCode.H3_EXCESSIVE_LOAD)) {
                    return 0;
                }
                Http3SettingsFrame settingsFrame = decodeSettings(ctx, in, payLoadLength);
                if (settingsFrame != null) {
                    out.add(settingsFrame);
                }
                return payLoadLength;
            case HTTP3_PUSH_PROMISE_FRAME_TYPE:
                // PUSH_PROMISE
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.5
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        // Let's use the maxHeaderListSize as a limit as this is this is the decompressed amounts of
                        // bytes which means the once we decompressed the headers we will be bigger then the actual
                        // payload size now.
                        Math.max(maxHeaderListSize, maxHeaderListSize + 8), Http3ErrorCode.H3_EXCESSIVE_LOAD)) {
                    return 0;
                }
                int pushPromiseIdLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(
                        readVariableLengthInteger(in, pushPromiseIdLen));
                if (decodeHeaders(ctx, pushPromiseFrame.headers(),
                        in.readSlice(payLoadLength - pushPromiseIdLen))) {
                    out.add(pushPromiseFrame);
                }
                return payLoadLength;
            case HTTP3_GO_AWAY_FRAME_TYPE:
                // GO_AWAY
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.6
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        HTTP3_GO_AWAY_FRAME_MAX_LEN, Http3ErrorCode.H3_FRAME_ERROR)) {
                    return 0;
                }
                int idLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                out.add(new DefaultHttp3GoAwayFrame(readVariableLengthInteger(in, idLen)));
                return payLoadLength;
            case HTTP3_MAX_PUSH_ID_FRAME_TYPE:
                // MAX_PUSH_ID
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.7
                if (!enforceMaxPayloadLength(ctx, in, type, payLoadLength,
                        HTTP3_MAX_PUSH_ID_FRAME_MAX_LEN, Http3ErrorCode.H3_FRAME_ERROR)) {
                    return 0;
                }
                int pidLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
                out.add(new DefaultHttp3MaxPushIdFrame(readVariableLengthInteger(in, pidLen)));
                return payLoadLength;
            default:
                if (!Http3CodecUtils.isReservedFrameType(longType)) {
                    return skipBytes(in, payLoadLength);
                }
                // Handling reserved frame types
                // https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                if (in.readableBytes() < payLoadLength) {
                    return 0;
                }
                out.add(new DefaultHttp3UnknownFrame(longType, in.readRetainedSlice(payLoadLength)));
                return payLoadLength;
        }
    }

    private static boolean enforceMaxPayloadLength(
            ChannelHandlerContext ctx, ByteBuf in, int type, int payLoadLength,
            long maxPayLoadLength, Http3ErrorCode error) {
        if (payLoadLength > maxPayLoadLength) {
            Http3CodecUtils.connectionError(ctx, error,
                    "Received an invalid frame len " + payLoadLength + " for frame of type " + type + '.', true);
            return false;
        }
        return in.readableBytes() >= payLoadLength;
    }

    private static Http3SettingsFrame decodeSettings(ChannelHandlerContext ctx, ByteBuf in, int payLoadLength) {
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        while (payLoadLength > 0) {
            int keyLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            long key = readVariableLengthInteger(in, keyLen);
            if (Http3CodecUtils.isReservedHttp2Setting(key)) {
                // This must be treated as a connection error
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4.1
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_SETTINGS_ERROR,
                        "Received a settings key that is reserved for HTTP/2.", true);
                return null;
            }
            payLoadLength -= keyLen;
            int valueLen = numBytesForVariableLengthInteger(in.getByte(in.readerIndex()));
            long value = readVariableLengthInteger(in, valueLen);
            payLoadLength -= valueLen;

            if (settingsFrame.put(key, value) != null) {
                // This must be treated as a connection error
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_SETTINGS_ERROR,
                        "Received a duplicate settings key.", true);
                return null;
            }
        }
        return settingsFrame;
    }

    /**
     * Decode the header block into header fields.
     * <p>
     * This method assumes the entire header block is contained in {@code in}.
     */
    private boolean decodeHeaders(ChannelHandlerContext ctx, Http3Headers headers, ByteBuf in) {
        try {
            Http3HeadersSink sink = new Http3HeadersSink(headers, maxHeaderListSize, true);
            qpackDecoder.decode(in, sink);
            // Throws exception if detected any problem so far
            sink.finish();
        } catch (Http3Exception e) {
            Http3CodecUtils.connectionError(ctx, e, true);
            return false;
        } catch (QpackException e) {
            // Must be treated as a connection error.
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                    "Decompression of header block failed.", true);
            return false;
        } catch (Http3HeadersValidationException e) {
            ctx.fireExceptionCaught(e);
            // We should close the stream.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1.3
            ctx.close();
            return false;
        }
        return true;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        try {
            if (msg instanceof Http3DataFrame) {
                writeDataFrame(ctx, (Http3DataFrame) msg, promise);
            } else if (msg instanceof Http3HeadersFrame) {
                writeHeadersFrame(ctx, (Http3HeadersFrame) msg, promise);
            } else if (msg instanceof Http3CancelPushFrame) {
                writeCancelPushFrame(ctx, (Http3CancelPushFrame) msg, promise);
            } else if (msg instanceof Http3SettingsFrame) {
                writeSettingsFrame(ctx, (Http3SettingsFrame) msg, promise);
            } else if (msg instanceof Http3PushPromiseFrame) {
                writePushPromiseFrame(ctx, (Http3PushPromiseFrame) msg, promise);
            } else if (msg instanceof Http3GoAwayFrame) {
                writeGoAwayFrame(ctx, (Http3GoAwayFrame) msg, promise);
            } else if (msg instanceof Http3MaxPushIdFrame) {
                writeMaxPushIdFrame(ctx, (Http3MaxPushIdFrame) msg, promise);
            } else if (msg instanceof Http3UnknownFrame) {
                writeUnknownFrame(ctx, (Http3UnknownFrame) msg, promise);
            } else {
                unsupported(promise);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private static void writeDataFrame(
            ChannelHandlerContext ctx, Http3DataFrame frame, ChannelPromise promise) {
        ByteBuf out = ctx.alloc().directBuffer(16);
        writeVariableLengthInteger(out, frame.type());
        writeVariableLengthInteger(out, frame.content().readableBytes());
        ByteBuf content = frame.content().retain();
        ctx.write(Unpooled.wrappedUnmodifiableBuffer(out, content), promise);
    }

    private void writeHeadersFrame(
            ChannelHandlerContext ctx, Http3HeadersFrame frame, ChannelPromise promise) {
        writeDynamicFrame(ctx, frame.type(), frame, (f, out) -> {
            qpackEncoder.encodeHeaders(out, f.headers());
            return true;
        }, promise);
    }

    private static void writeCancelPushFrame(
            ChannelHandlerContext ctx, Http3CancelPushFrame frame, ChannelPromise promise) {
        writeFrameWithId(ctx, frame.type(), frame.id(), promise);
    }

    private static void writeSettingsFrame(
            ChannelHandlerContext ctx, Http3SettingsFrame frame, ChannelPromise promise) {
        writeDynamicFrame(ctx, frame.type(), frame, (f, out) -> {
            for (Map.Entry<Long, Long> e : f) {
                Long key = e.getKey();
                if (Http3CodecUtils.isReservedHttp2Setting(key)) {
                    Http3Exception exception = new Http3Exception(Http3ErrorCode.H3_SETTINGS_ERROR,
                            "Received a settings key that is reserved for HTTP/2.");
                    promise.setFailure(exception);
                    // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
                    Http3CodecUtils.connectionError(ctx, exception, false);
                    return false;
                }
                Long value = e.getValue();
                int keyLen = numBytesForVariableLengthInteger(key);
                int valueLen = numBytesForVariableLengthInteger(value);
                writeVariableLengthInteger(out, key, keyLen);
                writeVariableLengthInteger(out, value, valueLen);
            }
            return true;
        }, promise);
    }

    private static <T extends Http3Frame> void writeDynamicFrame(ChannelHandlerContext ctx, long type, T frame,
                                                                 BiFunction<T, ByteBuf, Boolean> writer,
                                                                 ChannelPromise promise) {
        ByteBuf out = ctx.alloc().directBuffer();
        int initialWriterIndex = out.writerIndex();
        // Move 16 bytes forward as this is the maximum amount we could ever need for the type + payload length.
        int payloadStartIndex = initialWriterIndex + 16;
        out.writerIndex(payloadStartIndex);

        if (writer.apply(frame, out)) {
            int finalWriterIndex = out.writerIndex();
            int payloadLength = finalWriterIndex - payloadStartIndex;
            int len = numBytesForVariableLengthInteger(payloadLength);
            out.writerIndex(payloadStartIndex - len);
            writeVariableLengthInteger(out, payloadLength, len);

            int typeLength = numBytesForVariableLengthInteger(type);
            int startIndex = payloadStartIndex - len - typeLength;
            out.writerIndex(startIndex);
            writeVariableLengthInteger(out, type, typeLength);

            out.setIndex(startIndex, finalWriterIndex);
            ctx.write(out, promise);
        } else {
            // We failed to encode, lets release the buffer so we dont leak.
            out.release();
        }
    }

    private void writePushPromiseFrame(
            ChannelHandlerContext ctx, Http3PushPromiseFrame frame, ChannelPromise promise) {
        writeDynamicFrame(ctx, frame.type(), frame, (f, out) -> {
            long id = f.id();
            writeVariableLengthInteger(out, id);
            qpackEncoder.encodeHeaders(out, f.headers());
            return true;
        }, promise);
    }

    private static void writeGoAwayFrame(
            ChannelHandlerContext ctx, Http3GoAwayFrame frame, ChannelPromise promise) {
        writeFrameWithId(ctx, frame.type(), frame.id(), promise);
    }

    private static void writeMaxPushIdFrame(
            ChannelHandlerContext ctx, Http3MaxPushIdFrame frame, ChannelPromise promise) {
        writeFrameWithId(ctx, frame.type(), frame.id(), promise);
    }

    private static void writeFrameWithId(ChannelHandlerContext ctx, long type, long id, ChannelPromise promise) {
        ByteBuf out = ctx.alloc().directBuffer(24);
        writeVariableLengthInteger(out, type);
        writeVariableLengthInteger(out, numBytesForVariableLengthInteger(id));
        writeVariableLengthInteger(out, id);
        ctx.write(out, promise);
    }

    private static void writeUnknownFrame(
            ChannelHandlerContext ctx, Http3UnknownFrame frame, ChannelPromise promise) {
        long type = frame.type();
        if (Http3CodecUtils.isReservedHttp2FrameType(type)) {
            Http3Exception exception = new Http3Exception(Http3ErrorCode.H3_FRAME_UNEXPECTED,
                    "Reserved type for HTTP/2 send.");
            promise.setFailure(exception);
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
            Http3CodecUtils.connectionError(ctx, exception, false);
            return;
        }
        if (!Http3CodecUtils.isReservedFrameType(type)) {
            Http3Exception exception = new Http3Exception(Http3ErrorCode.H3_FRAME_UNEXPECTED,
                    "Non reserved type for HTTP/3 send.");
            promise.setFailure(exception);
            return;
        }
        ByteBuf out = ctx.alloc().directBuffer();
        writeVariableLengthInteger(out, type);
        writeVariableLengthInteger(out, frame.content().readableBytes());
        ByteBuf content = frame.content().retain();
        ctx.write(Unpooled.wrappedUnmodifiableBuffer(out, content), promise);
    }

    private static void unsupported(ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
