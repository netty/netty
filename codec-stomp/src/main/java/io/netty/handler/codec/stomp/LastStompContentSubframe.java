/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;

/**
 * The last {@link StompContentSubframe} which signals the end of the content batch
 * <p/>
 * Note, even when no content is emitted by the protocol, an
 * empty {@link LastStompContentSubframe} is issued to make the upstream parsing
 * easier.
 */
public interface LastStompContentSubframe extends StompContentSubframe {

    LastStompContentSubframe EMPTY_LAST_CONTENT = new LastStompContentSubframe() {
        @Override
        public ByteBuf content() {
            return Unpooled.EMPTY_BUFFER;
        }

        @Override
        public LastStompContentSubframe copy() {
            return EMPTY_LAST_CONTENT;
        }

        @Override
        public LastStompContentSubframe duplicate() {
            return this;
        }

        @Override
        public LastStompContentSubframe retainedDuplicate() {
            return this;
        }

        @Override
        public LastStompContentSubframe replace(ByteBuf content) {
            return new DefaultLastStompContentSubframe(content);
        }

        @Override
        public LastStompContentSubframe retain() {
            return this;
        }

        @Override
        public LastStompContentSubframe retain(int increment) {
            return this;
        }

        @Override
        public LastStompContentSubframe touch() {
            return this;
        }

        @Override
        public LastStompContentSubframe touch(Object hint) {
            return this;
        }

        @Override
        public int refCnt() {
            return 1;
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
        public DecoderResult decoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            throw new UnsupportedOperationException("read only");
        }
    };

    @Override
    LastStompContentSubframe copy();

    @Override
    LastStompContentSubframe duplicate();

    @Override
    LastStompContentSubframe retainedDuplicate();

    @Override
    LastStompContentSubframe replace(ByteBuf content);

    @Override
    LastStompContentSubframe retain();

    @Override
    LastStompContentSubframe retain(int increment);

    @Override
    LastStompContentSubframe touch();

    @Override
    LastStompContentSubframe touch(Object hint);
}
