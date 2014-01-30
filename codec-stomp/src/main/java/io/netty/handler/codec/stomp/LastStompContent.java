/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
 * The last {@link StompContent} which signals the end of the content batch
 * <p/>
 * Note, even when no content is emitted by the protocol, an
 * empty {@link LastStompContent} is issued to make the upstream parsing
 * easier.
 */
public interface LastStompContent extends StompContent {

    LastStompContent EMPTY_LAST_CONTENT = new LastStompContent() {
        @Override
        public ByteBuf content() {
            return Unpooled.EMPTY_BUFFER;
        }

        @Override
        public LastStompContent copy() {
            return EMPTY_LAST_CONTENT;
        }

        @Override
        public LastStompContent duplicate() {
            return this;
        }

        @Override
        public LastStompContent retain() {
            return this;
        }

        @Override
        public LastStompContent retain(int increment) {
            return this;
        }

        @Override
        public LastStompContent touch() {
            return this;
        }

        @Override
        public LastStompContent touch(Object hint) {
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
        public DecoderResult getDecoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            throw new UnsupportedOperationException("read only");
        }
    };

    @Override
    LastStompContent copy();

    @Override
    LastStompContent duplicate();

    @Override
    LastStompContent retain();

    @Override
    LastStompContent retain(int increment);

    @Override
    LastStompContent touch();

    @Override
    LastStompContent touch(Object hint);

}
