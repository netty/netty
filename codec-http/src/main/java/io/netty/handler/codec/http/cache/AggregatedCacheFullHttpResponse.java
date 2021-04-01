/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

final class AggregatedCacheFullHttpResponse implements FullHttpResponse {

    private final HttpMessage message;
    private final CompositeByteBuf content;
    private HttpHeaders trailingHeaders;

    AggregatedCacheFullHttpResponse(HttpMessage message, CompositeByteBuf content, HttpHeaders trailingHeaders) {
        this.message = message;
        this.content = content;
        this.trailingHeaders = trailingHeaders;
    }

    @Override
    public HttpHeaders trailingHeaders() {
        HttpHeaders trailingHeaders = this.trailingHeaders;
        if (trailingHeaders == null) {
            return EmptyHttpHeaders.INSTANCE;
        } else {
            return trailingHeaders;
        }
    }

    void setTrailingHeaders(HttpHeaders trailingHeaders) {
        this.trailingHeaders = trailingHeaders;
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return message.protocolVersion();
    }

    @Override
    public HttpVersion protocolVersion() {
        return message.protocolVersion();
    }

    @Override
    public FullHttpResponse setProtocolVersion(HttpVersion version) {
        message.setProtocolVersion(version);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return message.headers();
    }

    @Override
    public DecoderResult decoderResult() {
        return message.decoderResult();
    }

    @Override
    public DecoderResult getDecoderResult() {
        return message.decoderResult();
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        message.setDecoderResult(result);
    }

    @Override
    public CompositeByteBuf content() {
        return content;
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public FullHttpResponse retain() {
        content.retain();
        return this;
    }

    @Override
    public FullHttpResponse retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public FullHttpResponse touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public FullHttpResponse touch() {
        content.touch();
        return this;
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
    }

    @Override
    public FullHttpResponse copy() {
        return replace(content().copy());
    }

    @Override
    public FullHttpResponse duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public FullHttpResponse retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public FullHttpResponse replace(ByteBuf content) {
        DefaultFullHttpResponse dup = new DefaultFullHttpResponse(getProtocolVersion(), getStatus(), content,
                                                                  headers().copy(), trailingHeaders().copy());
        dup.setDecoderResult(decoderResult());
        return dup;
    }

    @Override
    public FullHttpResponse setStatus(HttpResponseStatus status) {
        ((HttpResponse) message).setStatus(status);
        return this;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return ((HttpResponse) message).status();
    }

    @Override
    public HttpResponseStatus status() {
        return getStatus();
    }
}
