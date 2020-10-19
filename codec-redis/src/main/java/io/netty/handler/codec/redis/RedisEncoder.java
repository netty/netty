/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Encodes {@link RedisMessage} into bytes following
 * <a href="https://redis.io/topics/protocol">RESP (REdis Serialization Protocol)</a>.
 */
@UnstableApi
public class RedisEncoder extends MessageToMessageEncoder<RedisMessage> {

    private final RedisMessagePool messagePool;

    /**
     * Creates a new instance with default {@code messagePool}.
     */
    public RedisEncoder() {
        this(FixedRedisMessagePool.INSTANCE);
    }

    /**
     * Creates a new instance.
     * @param messagePool the predefined message pool.
     */
    public RedisEncoder(RedisMessagePool messagePool) {
        this.messagePool = ObjectUtil.checkNotNull(messagePool, "messagePool");
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, RedisMessage msg, List<Object> out) throws Exception {
        try {
            writeRedisMessage(ctx.alloc(), msg, out);
        } catch (CodecException e) {
            throw e;
        } catch (Exception e) {
            throw new CodecException(e);
        }
    }

    private void writeRedisMessage(ByteBufAllocator allocator, RedisMessage msg, List<Object> out) {
        if (msg instanceof InlineCommandRedisMessage) {
            writeInlineCommandMessage(allocator, (InlineCommandRedisMessage) msg, out);
        } else if (msg instanceof SimpleStringRedisMessage) {
            writeSimpleStringMessage(allocator, (SimpleStringRedisMessage) msg, out);
        } else if (msg instanceof ErrorRedisMessage) {
            writeErrorMessage(allocator, (ErrorRedisMessage) msg, out);
        } else if (msg instanceof IntegerRedisMessage) {
            writeIntegerMessage(allocator, (IntegerRedisMessage) msg, out);
        } else if (msg instanceof FullBulkStringRedisMessage) {
            writeFullBulkStringMessage(allocator, (FullBulkStringRedisMessage) msg, out);
        } else if (msg instanceof BulkStringRedisContent) {
            writeBulkStringContent(allocator, (BulkStringRedisContent) msg, out);
        } else if (msg instanceof BulkStringHeaderRedisMessage) {
            writeBulkStringHeader(allocator, (BulkStringHeaderRedisMessage) msg, out);
        } else if (msg instanceof ArrayHeaderRedisMessage) {
            writeArrayHeader(allocator, (ArrayHeaderRedisMessage) msg, out);
        } else if (msg instanceof ArrayRedisMessage) {
            writeArrayMessage(allocator, (ArrayRedisMessage) msg, out);
        } else {
            throw new CodecException("unknown message type: " + msg);
        }
    }

    private static void writeInlineCommandMessage(ByteBufAllocator allocator, InlineCommandRedisMessage msg,
                                                 List<Object> out) {
        writeString(allocator, RedisMessageType.INLINE_COMMAND, msg.content(), out);
    }

    private static void writeSimpleStringMessage(ByteBufAllocator allocator, SimpleStringRedisMessage msg,
                                                 List<Object> out) {
        writeString(allocator, RedisMessageType.SIMPLE_STRING, msg.content(), out);
    }

    private static void writeErrorMessage(ByteBufAllocator allocator, ErrorRedisMessage msg, List<Object> out) {
        writeString(allocator, RedisMessageType.ERROR, msg.content(), out);
    }

    private static void writeString(ByteBufAllocator allocator, RedisMessageType type, String content,
                                    List<Object> out) {
        ByteBuf buf = allocator.ioBuffer(type.length() + ByteBufUtil.utf8MaxBytes(content) +
                                         RedisConstants.EOL_LENGTH);
        type.writeTo(buf);
        ByteBufUtil.writeUtf8(buf, content);
        buf.writeShort(RedisConstants.EOL_SHORT);
        out.add(buf);
    }

    private void writeIntegerMessage(ByteBufAllocator allocator, IntegerRedisMessage msg, List<Object> out) {
        ByteBuf buf = allocator.ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.LONG_MAX_LENGTH +
                                         RedisConstants.EOL_LENGTH);
        RedisMessageType.INTEGER.writeTo(buf);
        buf.writeBytes(numberToBytes(msg.value()));
        buf.writeShort(RedisConstants.EOL_SHORT);
        out.add(buf);
    }

    private void writeBulkStringHeader(ByteBufAllocator allocator, BulkStringHeaderRedisMessage msg, List<Object> out) {
        final ByteBuf buf = allocator.ioBuffer(RedisConstants.TYPE_LENGTH +
                                        (msg.isNull() ? RedisConstants.NULL_LENGTH :
                                                        RedisConstants.LONG_MAX_LENGTH + RedisConstants.EOL_LENGTH));
        RedisMessageType.BULK_STRING.writeTo(buf);
        if (msg.isNull()) {
            buf.writeShort(RedisConstants.NULL_SHORT);
        } else {
            buf.writeBytes(numberToBytes(msg.bulkStringLength()));
            buf.writeShort(RedisConstants.EOL_SHORT);
        }
        out.add(buf);
    }

    private static void writeBulkStringContent(ByteBufAllocator allocator, BulkStringRedisContent msg,
                                               List<Object> out) {
        out.add(msg.content().retain());
        if (msg instanceof LastBulkStringRedisContent) {
            out.add(allocator.ioBuffer(RedisConstants.EOL_LENGTH).writeShort(RedisConstants.EOL_SHORT));
        }
    }

    private void writeFullBulkStringMessage(ByteBufAllocator allocator, FullBulkStringRedisMessage msg,
                                            List<Object> out) {
        if (msg.isNull()) {
            ByteBuf buf = allocator.ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.NULL_LENGTH +
                                             RedisConstants.EOL_LENGTH);
            RedisMessageType.BULK_STRING.writeTo(buf);
            buf.writeShort(RedisConstants.NULL_SHORT);
            buf.writeShort(RedisConstants.EOL_SHORT);
            out.add(buf);
        } else {
            ByteBuf headerBuf = allocator.ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.LONG_MAX_LENGTH +
                                                   RedisConstants.EOL_LENGTH);
            RedisMessageType.BULK_STRING.writeTo(headerBuf);
            headerBuf.writeBytes(numberToBytes(msg.content().readableBytes()));
            headerBuf.writeShort(RedisConstants.EOL_SHORT);
            out.add(headerBuf);
            out.add(msg.content().retain());
            out.add(allocator.ioBuffer(RedisConstants.EOL_LENGTH).writeShort(RedisConstants.EOL_SHORT));
        }
    }

    /**
     * Write array header only without body. Use this if you want to write arrays as streaming.
     */
    private void writeArrayHeader(ByteBufAllocator allocator, ArrayHeaderRedisMessage msg, List<Object> out) {
        writeArrayHeader(allocator, msg.isNull(), msg.length(), out);
    }

    /**
     * Write full constructed array message.
     */
    private void writeArrayMessage(ByteBufAllocator allocator, ArrayRedisMessage msg, List<Object> out) {
        if (msg.isNull()) {
            writeArrayHeader(allocator, msg.isNull(), RedisConstants.NULL_VALUE, out);
        } else {
            writeArrayHeader(allocator, msg.isNull(), msg.children().size(), out);
            for (RedisMessage child : msg.children()) {
                writeRedisMessage(allocator, child, out);
            }
        }
    }

    private void writeArrayHeader(ByteBufAllocator allocator, boolean isNull, long length, List<Object> out) {
        if (isNull) {
            final ByteBuf buf = allocator.ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.NULL_LENGTH +
                                                   RedisConstants.EOL_LENGTH);
            RedisMessageType.ARRAY_HEADER.writeTo(buf);
            buf.writeShort(RedisConstants.NULL_SHORT);
            buf.writeShort(RedisConstants.EOL_SHORT);
            out.add(buf);
        } else {
            final ByteBuf buf = allocator.ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.LONG_MAX_LENGTH +
                                                   RedisConstants.EOL_LENGTH);
            RedisMessageType.ARRAY_HEADER.writeTo(buf);
            buf.writeBytes(numberToBytes(length));
            buf.writeShort(RedisConstants.EOL_SHORT);
            out.add(buf);
        }
    }

    private byte[] numberToBytes(long value) {
        byte[] bytes = messagePool.getByteBufOfInteger(value);
        return bytes != null ? bytes : RedisCodecUtil.longToAsciiBytes(value);
    }
}
