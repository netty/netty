/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromiseAggregatorFactory;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.TypeSensitiveMessageEncoder;
import io.netty.util.internal.ObjectUtil;

/**
 * Encodes {@link RedisMessage} into bytes following
 * <a href="http://redis.io/topics/protocol">RESP (REdis Serialization Protocol)</a>.
 */
public class RedisEncoder extends TypeSensitiveMessageEncoder<RedisMessage> {

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
    protected void encode(ChannelHandlerContext ctx, RedisMessage msg, ChannelPromiseAggregatorFactory promiseFactory)
            throws Exception {
        try {
            writeRedisMessage(ctx, msg, promiseFactory);
        } catch (CodecException e) {
            throw e;
        } catch (Throwable cause) {
            throw new CodecException(cause);
        }
    }

    private void writeRedisMessage(ChannelHandlerContext ctx, RedisMessage msg,
                                   ChannelPromiseAggregatorFactory promiseFactory) {
        if (msg instanceof SimpleStringRedisMessage) {
            writeSimpleStringMessage(ctx, (SimpleStringRedisMessage) msg, promiseFactory);
        } else if (msg instanceof ErrorRedisMessage) {
            writeErrorMessage(ctx, (ErrorRedisMessage) msg, promiseFactory);
        } else if (msg instanceof IntegerRedisMessage) {
            writeIntegerMessage(ctx, (IntegerRedisMessage) msg, promiseFactory);
        } else if (msg instanceof FullBulkStringRedisMessage) {
            writeFullBulkStringMessage(ctx, (FullBulkStringRedisMessage) msg, promiseFactory);
        } else if (msg instanceof BulkStringRedisContent) {
            writeBulkStringContent(ctx, (BulkStringRedisContent) msg, promiseFactory);
        } else if (msg instanceof BulkStringHeaderRedisMessage) {
            writeBulkStringHeader(ctx, (BulkStringHeaderRedisMessage) msg, promiseFactory);
        } else if (msg instanceof ArrayHeaderRedisMessage) {
            writeArrayHeader(ctx, (ArrayHeaderRedisMessage) msg, promiseFactory);
        } else if (msg instanceof ArrayRedisMessage) {
            writeArrayMessage(ctx, (ArrayRedisMessage) msg, promiseFactory);
        } else {
            throw new CodecException("unknown message type: " + msg);
        }
    }

    private static void writeSimpleStringMessage(ChannelHandlerContext ctx, SimpleStringRedisMessage msg,
                                                 ChannelPromiseAggregatorFactory promiseFactory) {
        writeString(ctx, RedisMessageType.SIMPLE_STRING.value(), msg.content(), promiseFactory);
    }

    private static void writeErrorMessage(ChannelHandlerContext ctx, ErrorRedisMessage msg,
                                          ChannelPromiseAggregatorFactory promiseFactory) {
        writeString(ctx, RedisMessageType.ERROR.value(), msg.content(), promiseFactory);
    }

    private static void writeString(ChannelHandlerContext ctx, byte type, String content,
                                    ChannelPromiseAggregatorFactory promiseFactory) {
        ByteBuf buf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH + ByteBufUtil.utf8MaxBytes(content) +
                                           RedisConstants.EOL_LENGTH);
        buf.writeByte(type);
        ByteBufUtil.writeUtf8(buf, content);
        buf.writeShort(RedisConstants.EOL_SHORT);
        ctx.write(buf, promiseFactory.newPromise());
    }

    private void writeIntegerMessage(ChannelHandlerContext ctx, IntegerRedisMessage msg,
                                     ChannelPromiseAggregatorFactory promiseFactory) {
        ByteBuf buf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.LONG_MAX_LENGTH +
                                           RedisConstants.EOL_LENGTH);
        buf.writeByte(RedisMessageType.INTEGER.value());
        buf.writeBytes(numberToBytes(msg.value()));
        buf.writeShort(RedisConstants.EOL_SHORT);
        ctx.write(buf, promiseFactory.newPromise());
    }

    private void writeBulkStringHeader(ChannelHandlerContext ctx, BulkStringHeaderRedisMessage msg,
                                       ChannelPromiseAggregatorFactory promiseFactory) {
        final ByteBuf buf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH +
                                        (msg.isNull() ? RedisConstants.NULL_LENGTH :
                                                        RedisConstants.LONG_MAX_LENGTH + RedisConstants.EOL_LENGTH));
        buf.writeByte(RedisMessageType.BULK_STRING.value());
        if (msg.isNull()) {
            buf.writeShort(RedisConstants.NULL_SHORT);
        } else {
            buf.writeBytes(numberToBytes(msg.bulkStringLength()));
            buf.writeShort(RedisConstants.EOL_SHORT);
        }
        ctx.write(buf, promiseFactory.newPromise());
    }

    private static void writeBulkStringContent(ChannelHandlerContext ctx, BulkStringRedisContent msg,
                                               ChannelPromiseAggregatorFactory promiseFactory) {
        ctx.write(msg.content().retain(), promiseFactory.newPromise());
        if (msg instanceof LastBulkStringRedisContent) {
            ctx.write(ctx.alloc().ioBuffer(RedisConstants.EOL_LENGTH).writeShort(RedisConstants.EOL_SHORT),
                      promiseFactory.newPromise());
        }
    }

    private void writeFullBulkStringMessage(ChannelHandlerContext ctx, FullBulkStringRedisMessage msg,
                                            ChannelPromiseAggregatorFactory promiseFactory) {
        if (msg.isNull()) {
            ByteBuf buf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.NULL_LENGTH +
                                               RedisConstants.EOL_LENGTH);
            buf.writeByte(RedisMessageType.BULK_STRING.value());
            buf.writeShort(RedisConstants.NULL_SHORT);
            buf.writeShort(RedisConstants.EOL_SHORT);
            ctx.write(buf, promiseFactory.newPromise());
        } else {
            ByteBuf headerBuf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.LONG_MAX_LENGTH +
                                                     RedisConstants.EOL_LENGTH);
            headerBuf.writeByte(RedisMessageType.BULK_STRING.value());
            headerBuf.writeBytes(numberToBytes(msg.content().readableBytes()));
            headerBuf.writeShort(RedisConstants.EOL_SHORT);
            ctx.write(headerBuf, promiseFactory.newPromise());
            ctx.write(msg.content().retain(), promiseFactory.newPromise());
            ctx.write(ctx.alloc().ioBuffer(RedisConstants.EOL_LENGTH).writeShort(RedisConstants.EOL_SHORT),
                      promiseFactory.newPromise());
        }
    }

    /**
     * Write array header only without body. Use this if you want to write arrays as streaming.
     */
    private void writeArrayHeader(ChannelHandlerContext ctx, ArrayHeaderRedisMessage msg,
                                  ChannelPromiseAggregatorFactory promiseFactory) {
        writeArrayHeader(ctx, msg.isNull(), msg.length(), promiseFactory);
    }

    /**
     * Write full constructed array message.
     */
    private void writeArrayMessage(ChannelHandlerContext ctx, ArrayRedisMessage msg,
                                   ChannelPromiseAggregatorFactory promiseFactory) {
        if (msg.isNull()) {
            writeArrayHeader(ctx, msg.isNull(), RedisConstants.NULL_VALUE, promiseFactory);
        } else {
            writeArrayHeader(ctx, msg.isNull(), msg.children().size(), promiseFactory);
            for (RedisMessage child : msg.children()) {
                writeRedisMessage(ctx, child, promiseFactory);
            }
        }
    }

    private void writeArrayHeader(ChannelHandlerContext ctx, boolean isNull, long length,
                                  ChannelPromiseAggregatorFactory promiseFactory) {
        if (isNull) {
            final ByteBuf buf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.NULL_LENGTH +
                                                     RedisConstants.EOL_LENGTH);
            buf.writeByte(RedisMessageType.ARRAY_HEADER.value());
            buf.writeShort(RedisConstants.NULL_SHORT);
            buf.writeShort(RedisConstants.EOL_SHORT);
            ctx.write(buf, promiseFactory.newPromise());
        } else {
            final ByteBuf buf = ctx.alloc().ioBuffer(RedisConstants.TYPE_LENGTH + RedisConstants.LONG_MAX_LENGTH +
                                                     RedisConstants.EOL_LENGTH);
            buf.writeByte(RedisMessageType.ARRAY_HEADER.value());
            buf.writeBytes(numberToBytes(length));
            buf.writeShort(RedisConstants.EOL_SHORT);
            ctx.write(buf, promiseFactory.newPromise());
        }
    }

    private byte[] numberToBytes(long value) {
        byte[] bytes = messagePool.getByteBufOfInteger(value);
        return bytes != null ? bytes : RedisCodecUtil.longToAsciiBytes(value);
    }
}
