/*
 * Copyright 2021 The Netty Project
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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * Boolean of <a href="https://github.com/antirez/RESP3/blob/master/spec.md">RESP3</a>.
 */
@UnstableApi
public final class BooleanRedisMessage implements RedisMessage {

    private boolean value;

    public static final BooleanRedisMessage TRUE_BOOLEAN_INSTANCE = new BooleanRedisMessage(true);

    public static final BooleanRedisMessage FALSE_BOOLEAN_INSTANCE = new BooleanRedisMessage(false);

    /**
     * Creates a {@link BooleanRedisMessage} for the given {@code value}.
     *
     * @param value true or false.
     */
    private BooleanRedisMessage(boolean value) {
        this.value = value;
    }

    /**
     * Get boolean value of this {@link BooleanRedisMessage}.
     *
     * @return boolean value
     */
    public boolean value() {
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
