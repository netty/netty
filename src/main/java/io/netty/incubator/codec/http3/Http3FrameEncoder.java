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
import java.util.function.BiConsumer;

import static io.netty.incubator.codec.http3.Http3CodecUtils.numBytesForVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;

public final class Http3FrameEncoder extends ChannelOutboundHandlerAdapter {
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
        writeVariableLengthInteger(out, 0x0);
        writeVariableLengthInteger(out, frame.content().readableBytes());
        ByteBuf content = frame.content().retain();
        writeBufferToContext(ctx, Unpooled.wrappedUnmodifiableBuffer(out, content), promise);
    }

    private void writeHeadersFrame(
            ChannelHandlerContext ctx, Http3HeadersFrame frame, ChannelPromise promise) {
        writeDynamicFrame(ctx, 0x1, frame, (f, out) -> qpackEncoder.encodeHeaders(out, f.headers()), promise);
    }

    private static void writeCancelPushFrame(
            ChannelHandlerContext ctx, Http3CancelPushFrame frame, ChannelPromise promise) {
        writeFrameWithId(ctx, 0x3, frame.id(), promise);
    }

    private static void writeSettingsFrame(
            ChannelHandlerContext ctx, Http3SettingsFrame frame, ChannelPromise promise) {
        writeDynamicFrame(ctx, 0x4, frame, (f, out) -> {
            for (Map.Entry<Long, Long> e : f) {
                Long key = e.getKey();
                Long value = e.getValue();
                int keyLen = numBytesForVariableLengthInteger(key);
                int valueLen = numBytesForVariableLengthInteger(value);
                writeVariableLengthInteger(out, key, keyLen);
                writeVariableLengthInteger(out, value, valueLen);
            }
        }, promise);
    }

    private static <T extends Http3Frame> void writeDynamicFrame(ChannelHandlerContext ctx, long type, T frame,
                                                          BiConsumer<T, ByteBuf> writer, ChannelPromise promise) {
        ByteBuf out = ctx.alloc().directBuffer();
        int initialWriterIndex = out.writerIndex();
        // Move 16 bytes forward as this is the maximum amount we could ever need for the type + payload length.
        int payloadStartIndex = initialWriterIndex + 16;
        out.writerIndex(payloadStartIndex);

        writer.accept(frame, out);
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
        writeBufferToContext(ctx, out, promise);
    }

    private void writePushPromiseFrame(
            ChannelHandlerContext ctx, Http3PushPromiseFrame frame, ChannelPromise promise) {
        writeDynamicFrame(ctx, 0x5, frame, (f, out) -> {
            long id = f.id();
            writeVariableLengthInteger(out, id);
            qpackEncoder.encodeHeaders(out, f.headers());
        }, promise);
    }

    private static void writeGoAwayFrame(
            ChannelHandlerContext ctx, Http3GoAwayFrame frame, ChannelPromise promise) {
        writeFrameWithId(ctx, 0x7, frame.id(), promise);
    }

    private static void writeMaxPushIdFrame(
            ChannelHandlerContext ctx, Http3MaxPushIdFrame frame, ChannelPromise promise) {
        writeFrameWithId(ctx, 0xd, frame.id(), promise);
    }

    private static void writeFrameWithId(ChannelHandlerContext ctx, long type, long id, ChannelPromise promise) {
        ByteBuf out = ctx.alloc().directBuffer();
        writeVariableLengthInteger(out, type);
        writeVariableLengthInteger(out, numBytesForVariableLengthInteger(id));
        writeVariableLengthInteger(out, id);
        writeBufferToContext(ctx, out, promise);
    }

    private static void unsupported(ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    private static void writeBufferToContext(ChannelHandlerContext ctx, ByteBuf buffer, ChannelPromise promise) {
        // TODO: Maybe this should be wrapped in a QuicStreamFrame.
        ctx.write(buffer, promise);
    }
}
