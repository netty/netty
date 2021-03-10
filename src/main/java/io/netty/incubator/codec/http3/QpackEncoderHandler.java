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

final class QpackEncoderHandler extends ByteToMessageDecoder {

    private final long maxTableCapacity;
    private boolean discard;

    QpackEncoderHandler(Long maxTableCapacity) {
        this.maxTableCapacity = maxTableCapacity == null ? 0 : maxTableCapacity;
    }

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

        // 4.3.1. Set Dynamic Table Capacity
        //
        //   0   1   2   3   4   5   6   7
        //+---+---+---+---+---+---+---+---+
        //| 0 | 0 | 1 |   Capacity (5+)   |
        //+---+---+---+-------------------+
        if ((b & 0b1110_0000) == 0b0010_0000) {
            // new capacity
            long length = QpackUtil.decodePrefixedInteger(in, 5);
            if (length < 0) {
                // Not enough readable bytes
                return;
            }

            if (length > maxTableCapacity) {
                discard = true;
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR,
                        "Dynamic table length '" + length + "' exceeds the configured maximal table capacity.", false);
            }
            // Do nothing for now
            // TODO: Adjust dynamic table
            return;
        }

        // 4.3.2. Insert With Name Reference
        //
        //      0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 1 | T |    Name Index (6+)    |
        //   +---+---+-----------------------+
        //   | H |     Value Length (7+)     |
        //   +---+---------------------------+
        //   |  Value String (Length bytes)  |
        //   +-------------------------------+
        if ((b & 0b1000_0000) == 0b1000_0000) {
            int readerIndex = in.readerIndex();
            // Just skip the first byte for now
            in.readerIndex(readerIndex + 1);

            long length = QpackUtil.decodePrefixedInteger(in, 7);
            if (length < 0) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            int stringLength = (int) length;
            if (in.readableBytes() < stringLength) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            in.skipBytes(stringLength);
            // TODO: Add to dynamic table
            return;
        }
        // 4.3.3. Insert With Literal Name
        //
        //      0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 0 | 1 | H | Name Length (5+)  |
        //   +---+---+---+-------------------+
        //   |  Name String (Length bytes)   |
        //   +---+---------------------------+
        //   | H |     Value Length (7+)     |
        //   +---+---------------------------+
        //   |  Value String (Length bytes)  |
        //   +-------------------------------+
        if ((b & 0b1100_0000) == 0b0100_0000) {
            int readerIndex = in.readerIndex();
            long nameLength = QpackUtil.decodePrefixedInteger(in, 5);
            if (nameLength < 0) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            if (in.readableBytes() < nameLength) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            in.skipBytes((int) nameLength);

            long valueLength = QpackUtil.decodePrefixedInteger(in, 7);
            if (valueLength < 0) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            if (in.readableBytes() < valueLength) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            in.skipBytes((int) valueLength);
            // TODO: Add to dynamic table
            return;
        }
        // 4.3.4. Duplicate
        //
        //      0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 0 | 0 | 0 |    Index (5+)     |
        //   +---+---+---+-------------------+
        if ((b & 0b1110_0000) == 0b0000_0000) {
            int readerIndex = in.readerIndex();
            long index = QpackUtil.decodePrefixedInteger(in, 5);
            if (index < 0) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            // Just ignore the index
            // TODO: Modify dynamic table.
            return;
        }

        discard = true;
        Http3CodecUtils.connectionError(ctx, Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR,
                "Unknown encoder instruction '" + b + "'.",  false);
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
