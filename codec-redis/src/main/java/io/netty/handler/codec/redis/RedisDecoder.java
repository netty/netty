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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes the Redis protocol into {@link RedisMessage} objects following
 * <a href="http://redis.io/topics/protocol">RESP (REdis Serialization Protocol)</a>.
 *
 * {@link RedisMessage} parts can be aggregated to {@link RedisMessage} using
 * {@link RedisArrayAggregator} or processed directly.
 */
@UnstableApi
public final class RedisDecoder extends ByteToMessageDecoder {

    private final ToPositiveLongProcessor toPositiveLongProcessor = new ToPositiveLongProcessor();

    private final int maxInlineMessageLength;
    private final RedisMessagePool messagePool;

    // current decoding states
    private State state = State.DECODE_TYPE;
    private RedisMessageType type;
    private int remainingBulkLength;

    private enum State {
        DECODE_TYPE,
        DECODE_INLINE, // SIMPLE_STRING, ERROR, INTEGER
        DECODE_LENGTH, // BULK_STRING, ARRAY_HEADER
        DECODE_BULK_STRING_EOL,
        DECODE_BULK_STRING_CONTENT,
    }

    /**
     * Creates a new instance with default {@code maxInlineMessageLength} and {@code messageaPool}.
     */
    public RedisDecoder() {
        // 1024 * 64 is max inline length of current Redis server implementation.
        this(1024 * 64, FixedRedisMessagePool.INSTANCE);
    }

    /**
     * Creates a new instance.
     * @param maxInlineMessageLength the maximum length of inline message.
     * @param messagePool the predefined message pool.
     */
    public RedisDecoder(int maxInlineMessageLength, RedisMessagePool messagePool) {
        if (maxInlineMessageLength <= 0 || maxInlineMessageLength > RedisConstants.REDIS_MESSAGE_MAX_LENGTH) {
            throw new RedisCodecException("maxInlineMessageLength: " + maxInlineMessageLength +
                                          " (expected: <= " + RedisConstants.REDIS_MESSAGE_MAX_LENGTH + ")");
        }
        this.maxInlineMessageLength = maxInlineMessageLength;
        this.messagePool = messagePool;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            for (;;) {
                switch (state) {
                case DECODE_TYPE:
                    if (!decodeType(in)) {
                        return;
                    }
                    break;
                case DECODE_INLINE:
                    if (!decodeInline(in, out)) {
                        return;
                    }
                    break;
                case DECODE_LENGTH:
                    if (!decodeLength(in, out)) {
                        return;
                    }
                    break;
                case DECODE_BULK_STRING_EOL:
                    if (!decodeBulkStringEndOfLine(in, out)) {
                        return;
                    }
                    break;
                case DECODE_BULK_STRING_CONTENT:
                    if (!decodeBulkStringContent(in, out)) {
                        return;
                    }
                    break;
                default:
                    throw new RedisCodecException("Unknown state: " + state);
                }
            }
        } catch (RedisCodecException e) {
            resetDecoder();
            throw e;
        } catch (Exception e) {
            resetDecoder();
            throw new RedisCodecException(e);
        }
    }

    private void resetDecoder() {
        state = State.DECODE_TYPE;
        remainingBulkLength = 0;
    }

    private boolean decodeType(ByteBuf in) throws Exception {
        if (!in.isReadable()) {
            return false;
        }
        type = RedisMessageType.valueOf(in.readByte());
        state = type.isInline() ? State.DECODE_INLINE : State.DECODE_LENGTH;
        return true;
    }

    private boolean decodeInline(ByteBuf in, List<Object> out) throws Exception {
        ByteBuf lineBytes = readLine(in);
        if (lineBytes == null) {
            if (in.readableBytes() > maxInlineMessageLength) {
                throw new RedisCodecException("length: " + in.readableBytes() +
                                              " (expected: <= " + maxInlineMessageLength + ")");
            }
            return false;
        }
        out.add(newInlineRedisMessage(type, lineBytes));
        resetDecoder();
        return true;
    }

    private boolean decodeLength(ByteBuf in, List<Object> out) throws Exception {
        ByteBuf lineByteBuf = readLine(in);
        if (lineByteBuf == null) {
            return false;
        }
        final long length = parseRedisNumber(lineByteBuf);
        if (length < RedisConstants.NULL_VALUE) {
            throw new RedisCodecException("length: " + length + " (expected: >= " + RedisConstants.NULL_VALUE + ")");
        }
        switch (type) {
        case ARRAY_HEADER:
            out.add(new ArrayHeaderRedisMessage(length));
            resetDecoder();
            return true;
        case BULK_STRING:
            if (length > RedisConstants.REDIS_MESSAGE_MAX_LENGTH) {
                throw new RedisCodecException("length: " + length + " (expected: <= " +
                                              RedisConstants.REDIS_MESSAGE_MAX_LENGTH + ")");
            }
            remainingBulkLength = (int) length; // range(int) is already checked.
            return decodeBulkString(in, out);
        default:
            throw new RedisCodecException("bad type: " + type);
        }
    }

    private boolean decodeBulkString(ByteBuf in, List<Object> out) throws Exception {
        switch (remainingBulkLength) {
        case RedisConstants.NULL_VALUE: // $-1\r\n
            out.add(FullBulkStringRedisMessage.NULL_INSTANCE);
            resetDecoder();
            return true;
        case 0:
            state = State.DECODE_BULK_STRING_EOL;
            return decodeBulkStringEndOfLine(in, out);
        default: // expectedBulkLength is always positive.
            out.add(new BulkStringHeaderRedisMessage(remainingBulkLength));
            state = State.DECODE_BULK_STRING_CONTENT;
            return decodeBulkStringContent(in, out);
        }
    }

    // $0\r\n <here> \r\n
    private boolean decodeBulkStringEndOfLine(ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < RedisConstants.EOL_LENGTH) {
            return false;
        }
        readEndOfLine(in);
        out.add(FullBulkStringRedisMessage.EMPTY_INSTANCE);
        resetDecoder();
        return true;
    }

    // ${expectedBulkLength}\r\n <here> {data...}\r\n
    private boolean decodeBulkStringContent(ByteBuf in, List<Object> out) throws Exception {
        final int readableBytes = in.readableBytes();
        if (readableBytes == 0) {
            return false;
        }

        // if this is last frame.
        if (readableBytes >= remainingBulkLength + RedisConstants.EOL_LENGTH) {
            ByteBuf content = in.readSlice(remainingBulkLength);
            readEndOfLine(in);
            // Only call retain after readEndOfLine(...) as the method may throw an exception.
            out.add(new DefaultLastBulkStringRedisContent(content.retain()));
            resetDecoder();
            return true;
        }

        // chunked write.
        int toRead = Math.min(remainingBulkLength, readableBytes);
        remainingBulkLength -= toRead;
        out.add(new DefaultBulkStringRedisContent(in.readSlice(toRead).retain()));
        return true;
    }

    private static void readEndOfLine(final ByteBuf in) {
        final short delim = in.readShort();
        if (RedisConstants.EOL_SHORT == delim) {
            return;
        }
        final byte[] bytes = RedisCodecUtil.shortToBytes(delim);
        throw new RedisCodecException("delimiter: [" + bytes[0] + "," + bytes[1] + "] (expected: \\r\\n)");
    }

    private RedisMessage newInlineRedisMessage(RedisMessageType messageType, ByteBuf content) {
        switch (messageType) {
        case SIMPLE_STRING: {
            SimpleStringRedisMessage cached = messagePool.getSimpleString(content);
            return cached != null ? cached : new SimpleStringRedisMessage(content.toString(CharsetUtil.UTF_8));
        }
        case ERROR: {
            ErrorRedisMessage cached = messagePool.getError(content);
            return cached != null ? cached : new ErrorRedisMessage(content.toString(CharsetUtil.UTF_8));
        }
        case INTEGER: {
            IntegerRedisMessage cached = messagePool.getInteger(content);
            return cached != null ? cached : new IntegerRedisMessage(parseRedisNumber(content));
        }
        default:
            throw new RedisCodecException("bad type: " + messageType);
        }
    }

    private static ByteBuf readLine(ByteBuf in) {
        if (!in.isReadable(RedisConstants.EOL_LENGTH)) {
            return null;
        }
        final int lfIndex = in.forEachByte(ByteProcessor.FIND_LF);
        if (lfIndex < 0) {
            return null;
        }
        ByteBuf data = in.readSlice(lfIndex - in.readerIndex() - 1); // `-1` is for CR
        readEndOfLine(in); // validate CR LF
        return data;
    }

    private long parseRedisNumber(ByteBuf byteBuf) {
        final int readableBytes = byteBuf.readableBytes();
        final boolean negative = readableBytes > 0 && byteBuf.getByte(byteBuf.readerIndex()) == '-';
        final int extraOneByteForNegative = negative ? 1 : 0;
        if (readableBytes <= extraOneByteForNegative) {
            throw new RedisCodecException("no number to parse: " + byteBuf.toString(CharsetUtil.US_ASCII));
        }
        if (readableBytes > RedisConstants.POSITIVE_LONG_MAX_LENGTH + extraOneByteForNegative) {
            throw new RedisCodecException("too many characters to be a valid RESP Integer: " +
                                          byteBuf.toString(CharsetUtil.US_ASCII));
        }
        if (negative) {
            return -parsePositiveNumber(byteBuf.skipBytes(extraOneByteForNegative));
        }
        return parsePositiveNumber(byteBuf);
    }

    private long parsePositiveNumber(ByteBuf byteBuf) {
        toPositiveLongProcessor.reset();
        byteBuf.forEachByte(toPositiveLongProcessor);
        return toPositiveLongProcessor.content();
    }

    private static final class ToPositiveLongProcessor implements ByteProcessor {
        private long result;

        @Override
        public boolean process(byte value) throws Exception {
            if (value < '0' || value > '9') {
                throw new RedisCodecException("bad byte in number: " + value);
            }
            result = result * 10 + (value - '0');
            return true;
        }

        public long content() {
            return result;
        }

        public void reset() {
            result = 0;
        }
    }
}
