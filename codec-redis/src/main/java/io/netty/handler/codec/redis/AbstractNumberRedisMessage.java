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

import java.math.BigInteger;

/**
 * Abstract class for Number type message.
 */
@UnstableApi
public abstract class AbstractNumberRedisMessage implements RedisMessage {

    protected final Number value;

    /**
     * For create a {@link IntegerRedisMessage} the given int {@code value}.
     *
     * @param value the message content.
     */
    AbstractNumberRedisMessage(long value) {
        this.value = value;
    }

    /**
     * For create a {@link DoubleRedisMessage} the given double {@code value}.
     *
     * @param value the message content.
     */
    AbstractNumberRedisMessage(double value) {
        this.value = value;
    }

    /**
     * For create a {@link BigNumberRedisMessage} the given BigInteger {@code value}.
     *
     * @param value the message content.
     */
    AbstractNumberRedisMessage(BigInteger value) {
        this.value = value;
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
