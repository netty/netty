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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * Integers of <a href="http://redis.io/topics/protocol">RESP</a>.
 */
@UnstableApi
public final class IntegerRedisMessage implements RedisMessage {

    private final long value;

    /**
     * Creates a {@link IntegerRedisMessage} for the given {@code content}.
     *
     * @param value the message content.
     */
    public IntegerRedisMessage(long value) {
        this.value = value;
    }

    /**
     * Get long value of this {@link IntegerRedisMessage}.
     *
     * @return long value
     */
    public long value() {
        return value;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("value=")
                .append(value)
                .append(']').toString();
    }
}
