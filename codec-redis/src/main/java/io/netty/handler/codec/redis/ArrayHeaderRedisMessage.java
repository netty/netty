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
 * Header of Redis Array Message.
 */
@UnstableApi
public class ArrayHeaderRedisMessage implements RedisMessage {

    private final long length;

    /**
     * Creates a {@link ArrayHeaderRedisMessage} for the given {@code length}.
     */
    public ArrayHeaderRedisMessage(long length) {
        if (length < RedisConstants.NULL_VALUE) {
            throw new RedisCodecException("length: " + length + " (expected: >= " + RedisConstants.NULL_VALUE + ")");
        }
        this.length = length;
    }

    /**
     * Get length of this array object.
     */
    public final long length() {
        return length;
    }

    /**
     * Returns whether the content of this message is {@code null}.
     *
     * @return indicates whether the content of this message is {@code null}.
     */
    public boolean isNull() {
        return length == RedisConstants.NULL_VALUE;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("length=")
                .append(length)
                .append(']').toString();
    }
}
