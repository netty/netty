/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;

/**
 * The last {@link HttpContent} which has trailing headers.
 */
public interface LastHttpContent extends HttpContent {

    /**
     * The 'end of content' marker in chunked encoding.
     */
    LastHttpContent EMPTY_LAST_CONTENT = new LastHttpContent() {

        @Override
        public ByteBuf content() {
            return Unpooled.EMPTY_BUFFER;
        }

        @Override
        public LastHttpContent copy() {
            return EMPTY_LAST_CONTENT;
        }

        @Override
        public LastHttpContent duplicate() {
            return this;
        }

        @Override
        public LastHttpContent replace(ByteBuf content) {
            return new DefaultLastHttpContent(content);
        }

        @Override
        public LastHttpContent retainedDuplicate() {
            return this;
        }

        @Override
        public HttpHeaders trailingHeaders() {
            return EmptyHttpHeaders.INSTANCE;
        }

        @Override
        public DecoderResult decoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        @Deprecated
        public DecoderResult getDecoderResult() {
            return decoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public int refCnt() {
            return 1;
        }

        @Override
        public LastHttpContent retain() {
            return this;
        }

        @Override
        public LastHttpContent retain(int increment) {
            return this;
        }

        @Override
        public LastHttpContent touch() {
            return this;
        }

        @Override
        public LastHttpContent touch(Object hint) {
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
            return "EmptyLastHttpContent";
        }
    };

    HttpHeaders trailingHeaders();

    @Override
    LastHttpContent copy();

    @Override
    LastHttpContent duplicate();

    @Override
    LastHttpContent retainedDuplicate();

    @Override
    LastHttpContent replace(ByteBuf content);

    @Override
    LastHttpContent retain(int increment);

    @Override
    LastHttpContent retain();

    @Override
    LastHttpContent touch();

    @Override
    LastHttpContent touch(Object hint);
}
