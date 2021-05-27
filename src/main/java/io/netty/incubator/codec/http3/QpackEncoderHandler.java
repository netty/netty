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
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AsciiString;

import java.util.List;

import static io.netty.incubator.codec.http3.Http3CodecUtils.connectionError;
import static io.netty.incubator.codec.http3.Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR;
import static io.netty.incubator.codec.http3.QpackUtil.MAX_UNSIGNED_INT;
import static io.netty.incubator.codec.http3.QpackUtil.decodePrefixedIntegerAsInt;
import static io.netty.util.internal.ObjectUtil.checkInRange;

final class QpackEncoderHandler extends ByteToMessageDecoder {

    private final QpackHuffmanDecoder huffmanDecoder;
    private final QpackDecoder qpackDecoder;
    private boolean discard;

    QpackEncoderHandler(Long maxTableCapacity, QpackDecoder qpackDecoder) {
        checkInRange(maxTableCapacity == null ? 0 : maxTableCapacity, 0, MAX_UNSIGNED_INT, "maxTableCapacity");
        huffmanDecoder = new QpackHuffmanDecoder();
        this.qpackDecoder = qpackDecoder;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> __) throws Exception {
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
            long capacity = QpackUtil.decodePrefixedInteger(in, 5);
            if (capacity < 0) {
                // Not enough readable bytes
                return;
            }

            try {
                qpackDecoder.setDynamicTableCapacity(capacity);
            } catch (QpackException e) {
                handleDecodeFailure(ctx, e, "setDynamicTableCapacity failed.");
            }
            return;
        }

        final QpackAttributes qpackAttributes = Http3.getQpackAttributes(ctx.channel().parent());
        assert qpackAttributes != null;
        if (!qpackAttributes.dynamicTableDisabled() && !qpackAttributes.decoderStreamAvailable()) {
            // We need the decoder stream to update the decoder with these instructions.
            return;
        }
        final QuicStreamChannel decoderStream = qpackAttributes.decoderStream();

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
            // T == 1 implies static table index.
            // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-with-name-reference
            final boolean isStaticTableIndex = QpackUtil.firstByteEquals(in, (byte) 0b1100_0000);
            final int nameIdx = decodePrefixedIntegerAsInt(in, 6);
            if (nameIdx < 0) {
                // Not enough readable bytes
                return;
            }

            CharSequence value = decodeLiteralValue(in);
            if (value == null) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            try {
                qpackDecoder.insertWithNameReference(decoderStream, isStaticTableIndex, nameIdx,
                        value);
            } catch (QpackException e) {
                handleDecodeFailure(ctx, e, "insertWithNameReference failed.");
            }
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
            final boolean nameHuffEncoded = QpackUtil.firstByteEquals(in, (byte) 0b0110_0000);
            int nameLength = decodePrefixedIntegerAsInt(in, 5);
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

            CharSequence name = decodeStringLiteral(in, nameHuffEncoded, nameLength);
            CharSequence value = decodeLiteralValue(in);
            if (value == null) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            try {
                qpackDecoder.insertLiteral(decoderStream, name, value);
            } catch (QpackException e) {
                handleDecodeFailure(ctx, e, "insertLiteral failed.");
            }
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
            int index = decodePrefixedIntegerAsInt(in, 5);
            if (index < 0) {
                // Reset readerIndex
                in.readerIndex(readerIndex);
                // Not enough readable bytes
                return;
            }
            try {
                qpackDecoder.duplicate(decoderStream, index);
            } catch (QpackException e) {
                handleDecodeFailure(ctx, e, "duplicate failed.");
            }
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

    private void handleDecodeFailure(ChannelHandlerContext ctx, QpackException cause, String message) {
        discard = true;
        connectionError(ctx, new Http3Exception(QPACK_ENCODER_STREAM_ERROR, message, cause), true);
    }

    private CharSequence decodeLiteralValue(ByteBuf in) throws QpackException {
        final boolean valueHuffEncoded = QpackUtil.firstByteEquals(in, (byte) 0b1000_0000);
        int valueLength = decodePrefixedIntegerAsInt(in, 7);
        if (valueLength < 0 || in.readableBytes() < valueLength) {
            // Not enough readable bytes
            return null;
        }

        return decodeStringLiteral(in, valueHuffEncoded, valueLength);
    }

    private CharSequence decodeStringLiteral(ByteBuf in, boolean huffmanEncoded, int length)
            throws QpackException {
        if (huffmanEncoded) {
            return huffmanDecoder.decode(in, length);
        }
        byte[] buf = new byte[length];
        in.readBytes(buf);
        return new AsciiString(buf, false);
    }
}
