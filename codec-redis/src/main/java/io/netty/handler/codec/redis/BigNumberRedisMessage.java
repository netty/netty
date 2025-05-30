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

import io.netty.util.internal.UnstableApi;

import java.math.BigInteger;

/**
 * Big number of <a href="https://github.com/antirez/RESP3/blob/master/spec.md">RESP3</a>.
 */
@UnstableApi
public final class BigNumberRedisMessage extends AbstractNumberRedisMessage {

    /**
     * Creates a {@link BigNumberRedisMessage} for the given byte {@code content}.
     *
     * @param value the message content.
     */
    public BigNumberRedisMessage(byte[] value) {
        this(new String(value));
    }

    /**
     * Creates a {@link BigNumberRedisMessage} for the given string {@code content}.
     *
     * @param value the message content.
     */
    public BigNumberRedisMessage(String value) {
        this(new BigInteger(value));
    }

    /**
     * Creates a {@link BigNumberRedisMessage} for the given BigInteger {@code content}.
     *
     * @param value the message content.
     */
    public BigNumberRedisMessage(BigInteger value) {
        super(value);
    }

    /**
     * Get string represent the value of this {@link DoubleRedisMessage}.
     *
     * @return string value
     */
    public String value() {
        return value.toString();
    }
}
