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
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;

/**
 * A last chunk of Bulk Strings.
 */
public interface LastBulkStringRedisContent extends BulkStringRedisContent {

    /**
     * The 'end of content' marker in chunked encoding.
     */
    LastBulkStringRedisContent EMPTY_LAST_CONTENT = new LastBulkStringRedisContent() {

        @Override
        public ByteBuf content() {
            return Unpooled.EMPTY_BUFFER;
        }

        @Override
        public ByteBufHolder copy() {
            return this;
        }

        @Override
        public ByteBufHolder retain(int increment) {
            return this;
        }

        @Override
        public ByteBufHolder retain() {
            return this;
        }

        @Override
        public int refCnt() {
            return 1;
        }

        @Override
        public ByteBufHolder touch() {
            return this;
        }

        @Override
        public ByteBufHolder touch(Object hint) {
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
        public ByteBufHolder duplicate() {
            return this;
        }
    };
}
