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

import io.netty.util.internal.UnstableApi;

import java.util.Collections;
import java.util.List;

/**
 * Arrays of <a href="https://redis.io/topics/protocol">RESP</a>.
 */
@UnstableApi
public class ArrayRedisMessage extends AbstractCollectionRedisMessage {

    private ArrayRedisMessage() {
        super(Collections.emptyList());
    }

    /**
     * Creates a {@link ArrayRedisMessage} for the given {@code content}.
     *
     * @param children the children.
     */
    public ArrayRedisMessage(List<RedisMessage> children) {
        // do not retain here. children are already retained when created.
        super(children);
    }

    /**
     * Get children of this Arrays. It can be null or empty.
     *
     * @return list of {@link RedisMessage}s.
     */
    @Override
    public final List<RedisMessage> children() {
        return (List<RedisMessage>) children;
    }

    /**
     * A predefined null array instance for {@link ArrayRedisMessage}.
     */
    public static final ArrayRedisMessage NULL_INSTANCE = new ArrayRedisMessage() {
        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public ArrayRedisMessage retain() {
            return this;
        }

        @Override
        public ArrayRedisMessage retain(int increment) {
            return this;
        }

        @Override
        public ArrayRedisMessage touch() {
            return this;
        }

        @Override
        public ArrayRedisMessage touch(Object hint) {
            return this;
        }

        @Override
        public boolean release() {
            return false;
        }

        @Override
        public boolean release(int decrement) {
            return false;
        }

        @Override
        public String toString() {
            return "NullArrayRedisMessage";
        }
    };

    /**
     * A predefined empty array instance for {@link ArrayRedisMessage}.
     */
    public static final ArrayRedisMessage EMPTY_INSTANCE = new ArrayRedisMessage() {

        @Override
        public ArrayRedisMessage retain() {
            return this;
        }

        @Override
        public ArrayRedisMessage retain(int increment) {
            return this;
        }

        @Override
        public ArrayRedisMessage touch() {
            return this;
        }

        @Override
        public ArrayRedisMessage touch(Object hint) {
            return this;
        }

        @Override
        public boolean release() {
            return false;
        }

        @Override
        public boolean release(int decrement) {
            return false;
        }

        @Override
        public String toString() {
            return "EmptyArrayRedisMessage";
        }
    };

}
