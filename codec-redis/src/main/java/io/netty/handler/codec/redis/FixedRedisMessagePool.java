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
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.internal.UnstableApi;

import java.util.HashMap;
import java.util.Map;

/**
 * A default fixed redis message pool.
 */
@UnstableApi
public final class FixedRedisMessagePool implements RedisMessagePool {

    public enum RedisReplyKey {
        OK, PONG, QUEUED
    }

    public enum RedisErrorKey {
        ERR("ERR"),
        ERR_IDX("ERR index out of range"),
        ERR_NOKEY("ERR no such key"),
        ERR_SAMEOBJ("ERR source and destination objects are the same"),
        ERR_SYNTAX("ERR syntax error"),
        BUSY("BUSY Redis is busy running a script. You can only call SCRIPT KILL or SHUTDOWN NOSAVE."),
        BUSYKEY("BUSYKEY Target key name already exists."),
        EXECABORT("EXECABORT Transaction discarded because of previous errors."),
        LOADING("LOADING Redis is loading the dataset in memory"),
        MASTERDOWN("MASTERDOWN Link with MASTER is down and slave-serve-stale-data is set to 'no'."),
        MISCONF("MISCONF Redis is configured to save RDB snapshots, but is currently not able to persist on disk. " +
            "Commands that may modify the data set are disabled. Please check Redis logs for details " +
            "about the error."),
        NOREPLICAS("NOREPLICAS Not enough good slaves to write."),
        NOSCRIPT("NOSCRIPT No matching script. Please use EVAL."),
        OOM("OOM command not allowed when used memory > 'maxmemory'."),
        READONLY("READONLY You can't write against a read only slave."),
        WRONGTYPE("WRONGTYPE Operation against a key holding the wrong kind of value"),
        NOT_AUTH("NOAUTH Authentication required.");

        private final String msg;

        RedisErrorKey(String msg) {
            this.msg = msg;
        }

        @Override
        public String toString() {
            return msg;
        }
    }

    private static final long MIN_CACHED_INTEGER_NUMBER = RedisConstants.NULL_VALUE; // inclusive
    private static final long MAX_CACHED_INTEGER_NUMBER = 128; // exclusive

    // cached integer size cannot larger than `int` range because of Collection.
    private static final int SIZE_CACHED_INTEGER_NUMBER = (int) (MAX_CACHED_INTEGER_NUMBER - MIN_CACHED_INTEGER_NUMBER);

    /**
     * A shared object for {@link FixedRedisMessagePool}.
     */
    public static final FixedRedisMessagePool INSTANCE = new FixedRedisMessagePool();

    // internal caches.
    private final Map<ByteBuf, SimpleStringRedisMessage> byteBufToSimpleStrings;
    private final Map<String, SimpleStringRedisMessage> stringToSimpleStrings;
    private final Map<RedisReplyKey, SimpleStringRedisMessage> keyToSimpleStrings;
    private final Map<ByteBuf, ErrorRedisMessage> byteBufToErrors;
    private final Map<String, ErrorRedisMessage> stringToErrors;
    private final Map<RedisErrorKey, ErrorRedisMessage> keyToErrors;
    private final Map<ByteBuf, IntegerRedisMessage> byteBufToIntegers;
    private final LongObjectMap<IntegerRedisMessage> longToIntegers;
    private final LongObjectMap<byte[]> longToByteBufs;

    /**
     * Creates a {@link FixedRedisMessagePool} instance.
     */
    private FixedRedisMessagePool() {
        keyToSimpleStrings = new HashMap<RedisReplyKey, SimpleStringRedisMessage>(RedisReplyKey.values().length, 1.0f);
        stringToSimpleStrings = new HashMap<String, SimpleStringRedisMessage>(RedisReplyKey.values().length, 1.0f);
        byteBufToSimpleStrings = new HashMap<ByteBuf, SimpleStringRedisMessage>(RedisReplyKey.values().length, 1.0f);
        for (RedisReplyKey value : RedisReplyKey.values()) {
            ByteBuf key = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(
                value.name().getBytes(CharsetUtil.UTF_8))).asReadOnly();
            SimpleStringRedisMessage message = new SimpleStringRedisMessage(new String(Unpooled.unreleasableBuffer(
                Unpooled.wrappedBuffer(value.name().getBytes(CharsetUtil.UTF_8))).array()));
            stringToSimpleStrings.put(value.name(), message);
            keyToSimpleStrings.put(value, message);
            byteBufToSimpleStrings.put(key, message);
        }

        keyToErrors = new HashMap<RedisErrorKey, ErrorRedisMessage>(RedisErrorKey.values().length, 1.0f);
        stringToErrors = new HashMap<String, ErrorRedisMessage>(RedisErrorKey.values().length, 1.0f);
        byteBufToErrors = new HashMap<ByteBuf, ErrorRedisMessage>(RedisErrorKey.values().length, 1.0f);
        for (RedisErrorKey value : RedisErrorKey.values()) {
            ByteBuf key = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(
                value.toString().getBytes(CharsetUtil.UTF_8))).asReadOnly();
            ErrorRedisMessage message = new ErrorRedisMessage(new String(Unpooled.unreleasableBuffer(
                Unpooled.wrappedBuffer(value.toString().getBytes(CharsetUtil.UTF_8))).array()));
            stringToErrors.put(value.toString(), message);
            keyToErrors.put(value, message);
            byteBufToErrors.put(key, message);
        }

        byteBufToIntegers = new HashMap<ByteBuf, IntegerRedisMessage>(SIZE_CACHED_INTEGER_NUMBER, 1.0f);
        longToIntegers = new LongObjectHashMap<IntegerRedisMessage>(SIZE_CACHED_INTEGER_NUMBER, 1.0f);
        longToByteBufs = new LongObjectHashMap<byte[]>(SIZE_CACHED_INTEGER_NUMBER, 1.0f);
        for (long value = MIN_CACHED_INTEGER_NUMBER; value < MAX_CACHED_INTEGER_NUMBER; value++) {
            byte[] keyBytes = RedisCodecUtil.longToAsciiBytes(value);
            ByteBuf keyByteBuf = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(keyBytes)).asReadOnly();
            IntegerRedisMessage cached = new IntegerRedisMessage(value);
            byteBufToIntegers.put(keyByteBuf, cached);
            longToIntegers.put(value, cached);
            longToByteBufs.put(value, keyBytes);
        }
    }

    @Override
    public SimpleStringRedisMessage getSimpleString(String content) {
        return stringToSimpleStrings.get(content);
    }

    /**
     * Returns {@link SimpleStringRedisMessage} for the given {@link RedisReplyKey}
     * or {@code null} if it does not exist.
     */
    public SimpleStringRedisMessage getSimpleString(RedisReplyKey key) {
        return keyToSimpleStrings.get(key);
    }

    @Override
    public SimpleStringRedisMessage getSimpleString(ByteBuf content) {
        return byteBufToSimpleStrings.get(content);
    }

    @Override
    public ErrorRedisMessage getError(String content) {
        return stringToErrors.get(content);
    }

    /**
     * Returns {@link ErrorRedisMessage} for the given {@link RedisErrorKey}
     * or {@code null} if it does not exist.
     */
    public ErrorRedisMessage getError(RedisErrorKey key) {
        return keyToErrors.get(key);
    }

    @Override
    public ErrorRedisMessage getError(ByteBuf content) {
        return byteBufToErrors.get(content);
    }

    @Override
    public IntegerRedisMessage getInteger(long value) {
        return longToIntegers.get(value);
    }

    @Override
    public IntegerRedisMessage getInteger(ByteBuf content) {
        return byteBufToIntegers.get(content);
    }

    @Override
    public byte[] getByteBufOfInteger(long value) {
        return longToByteBufs.get(value);
    }
}
