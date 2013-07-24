/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;


final class ComposedLastHttpContent implements LastHttpContent {
    private final HttpHeaders trailingHeaders;
    private DecoderResult result;

    ComposedLastHttpContent(HttpHeaders trailingHeaders) {
        this.trailingHeaders = trailingHeaders;
    }
    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeaders;
    }

    @Override
    public LastHttpContent copy() {
        LastHttpContent content = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        content.trailingHeaders().set(trailingHeaders());
        return content;
    }

    @Override
    public LastHttpContent retain(int increment) {
        return this;
    }

    @Override
    public LastHttpContent retain() {
        return this;
    }

    @Override
    public HttpContent duplicate() {
        return copy();
    }

    @Override
    public ByteBuf content() {
        return Unpooled.EMPTY_BUFFER;
    }

    @Override
    public DecoderResult getDecoderResult() {
        return result;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        this.result = result;
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
}
