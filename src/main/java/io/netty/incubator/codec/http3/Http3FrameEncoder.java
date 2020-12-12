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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.Map;
import java.util.function.BiFunction;

import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;

final class Http3FrameEncoder extends ChannelOutboundHandlerAdapter {
    private final QpackEncoder qpackEncoder;

    Http3FrameEncoder(QpackEncoder qpackEncoder) {
        this.qpackEncoder = ObjectUtil.checkNotNull(qpackEncoder, "qpackEncoder");
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
        ByteBuf out = ctx.alloc().directBuffer();
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
        ByteBuf out = ctx.alloc().directBuffer();
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
}
