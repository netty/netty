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

    private static final String[] DEFAULT_SIMPLE_STRINGS = {
            "OK",
            "PONG",
            "QUEUED",
    };

    private static final String[] DEFAULT_ERRORS = {
            "ERR",
            "ERR index out of range",
            "ERR no such key",
            "ERR source and destination objects are the same",
            "ERR syntax error",
            "BUSY Redis is busy running a script. You can only call SCRIPT KILL or SHUTDOWN NOSAVE.",
            "BUSYKEY Target key name already exists.",
            "EXECABORT Transaction discarded because of previous errors.",
            "LOADING Redis is loading the dataset in memory",
            "MASTERDOWN Link with MASTER is down and slave-serve-stale-data is set to 'no'.",
            "MISCONF Redis is configured to save RDB snapshots, but is currently not able to persist on disk. " +
            "Commands that may modify the data set are disabled. Please check Redis logs for details " +
            "about the error.",
            "NOAUTH Authentication required.",
            "NOREPLICAS Not enough good slaves to write.",
            "NOSCRIPT No matching script. Please use EVAL.",
            "OOM command not allowed when used memory > 'maxmemory'.",
            "READONLY You can't write against a read only slave.",
            "WRONGTYPE Operation against a key holding the wrong kind of value",
    };

    private static final long MIN_CACHED_INTEGER_NUMBER = RedisConstants.NULL_VALUE; // inclusive
    private static final long MAX_CACHED_INTEGER_NUMBER = 128; // exclusive

    // cached integer size cannot larger than `int` range because of Collection.
    private static final int SIZE_CACHED_INTEGER_NUMBER = (int) (MAX_CACHED_INTEGER_NUMBER - MIN_CACHED_INTEGER_NUMBER);

    /**
     * A shared object for {@link FixedRedisMessagePool}.
     */
    public static final FixedRedisMessagePool INSTANCE = new FixedRedisMessagePool();

    // internal caches.
    private Map<ByteBuf, SimpleStringRedisMessage> byteBufToSimpleStrings;
    private Map<String, SimpleStringRedisMessage> stringToSimpleStrings;
    private Map<ByteBuf, ErrorRedisMessage> byteBufToErrors;
    private Map<String, ErrorRedisMessage> stringToErrors;
    private Map<ByteBuf, IntegerRedisMessage> byteBufToIntegers;
    private LongObjectMap<IntegerRedisMessage> longToIntegers;
    private LongObjectMap<byte[]> longToByteBufs;

    /**
     * Creates a {@link FixedRedisMessagePool} instance.
     */
    private FixedRedisMessagePool() {
        byteBufToSimpleStrings = new HashMap<ByteBuf, SimpleStringRedisMessage>(DEFAULT_SIMPLE_STRINGS.length, 1.0f);
        stringToSimpleStrings = new HashMap<String, SimpleStringRedisMessage>(DEFAULT_SIMPLE_STRINGS.length, 1.0f);
        for (String message : DEFAULT_SIMPLE_STRINGS) {
            ByteBuf key = Unpooled.unmodifiableBuffer(
                    Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(message.getBytes(CharsetUtil.UTF_8))));
            SimpleStringRedisMessage cached = new SimpleStringRedisMessage(message);
            byteBufToSimpleStrings.put(key, cached);
            stringToSimpleStrings.put(message, cached);
        }

        byteBufToErrors = new HashMap<ByteBuf, ErrorRedisMessage>(DEFAULT_ERRORS.length, 1.0f);
        stringToErrors = new HashMap<String, ErrorRedisMessage>(DEFAULT_ERRORS.length, 1.0f);
        for (String message : DEFAULT_ERRORS) {
            ByteBuf key = Unpooled.unmodifiableBuffer(
                    Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(message.getBytes(CharsetUtil.UTF_8))));
            ErrorRedisMessage cached = new ErrorRedisMessage(message);
            byteBufToErrors.put(key, cached);
            stringToErrors.put(message, cached);
        }

        byteBufToIntegers = new HashMap<ByteBuf, IntegerRedisMessage>(SIZE_CACHED_INTEGER_NUMBER, 1.0f);
        longToIntegers = new LongObjectHashMap<IntegerRedisMessage>(SIZE_CACHED_INTEGER_NUMBER, 1.0f);
        longToByteBufs = new LongObjectHashMap<byte[]>(SIZE_CACHED_INTEGER_NUMBER, 1.0f);
        for (long value = MIN_CACHED_INTEGER_NUMBER; value < MAX_CACHED_INTEGER_NUMBER; value++) {
            byte[] keyBytes = RedisCodecUtil.longToAsciiBytes(value);
            ByteBuf keyByteBuf = Unpooled.unmodifiableBuffer(Unpooled.unreleasableBuffer(
                    Unpooled.wrappedBuffer(keyBytes)));
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

    @Override
    public SimpleStringRedisMessage getSimpleString(ByteBuf content) {
        return byteBufToSimpleStrings.get(content);
    }

    @Override
    public ErrorRedisMessage getError(String content) {
        return stringToErrors.get(content);
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
