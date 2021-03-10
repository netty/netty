/*
 * Copyright 2021 The Netty Project
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

final class QpackDecoderHandler extends ByteToMessageDecoder {

    private boolean discard;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) {
            return;
        }
        if (discard) {
            in.skipBytes(in.readableBytes());
            return;
        }

        byte b = in.getByte(in.readerIndex());

        // 4.4.1. Section Acknowledgment
        //
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 |      Stream ID (7+)       |
        // +---+---------------------------+
        if ((b & 0b1000_0000) == 0b1000_0000) {
            // new capacity
            long streamId = QpackUtil.decodePrefixedInteger(in, 7);
            if (streamId < 0) {
                // Not enough readable bytes
                return;
            }
            // Do nothing for now
            return;
        }

        // 4.4.2. Stream Cancellation
        //
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 |     Stream ID (6+)    |
        // +---+---+-----------------------+
        if ((b & 0b1100_0000) == 0b0100_0000) {
            long streamId = QpackUtil.decodePrefixedInteger(in, 6);
            if (streamId < 0) {

                // Not enough readable bytes
                return;
            }
            // Do nothing for now
            return;
        }
        // 4.4.3. Insert Count Increment
        //
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 |     Increment (6+)    |
        // +---+---+-----------------------+
        if ((b & 0b1100_0000) == 0b0000_0000) {
            long increment = QpackUtil.decodePrefixedInteger(in, 6);
            if (increment <= 0) {
                discard = true;
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.QPACK_DECODER_STREAM_ERROR,
                        "Invalid increment '" + increment + "'.",  false);
                return;
            }
            return;
        }
        // TODO: Handle me
        in.skipBytes(in.readableBytes());
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();

        // QPACK streams should always be processed, no matter what the user is doing in terms of configuration
        // and AUTO_READ.
        Http3CodecUtils.readIfNoAutoRead(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof ChannelInputShutdownEvent) {
            // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
            Http3CodecUtils.criticalStreamClosed(ctx);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // See https://www.ietf.org/archive/id/draft-ietf-quic-qpack-19.html#section-4.2
        Http3CodecUtils.criticalStreamClosed(ctx);
        ctx.fireChannelInactive();
    }
}
