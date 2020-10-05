/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.internal.StringUtil;

/**
 * {@link DefaultHttp2TranslatedHttpContent} contains {@code streamId} from last (endOfStream) {@link Http2DataFrame}.
 */
public class DefaultHttp2TranslatedLastHttpContent extends DefaultHttp2TranslatedHttpContent
        implements LastHttpContent {

    public DefaultHttp2TranslatedLastHttpContent(int streamId) {
        super(Unpooled.buffer(0), streamId);
    }

    public DefaultHttp2TranslatedLastHttpContent(ByteBuf content, int streamId) {
        super(content, streamId);
    }

    public int getStreamId() {
        return super.getStreamId();
    }

    @Override
    public LastHttpContent copy() {
        return replace(content().copy());
    }

    @Override
    public LastHttpContent duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public LastHttpContent retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public LastHttpContent replace(ByteBuf content) {
        return new DefaultHttp2TranslatedLastHttpContent(content, super.getStreamId());
    }

    @Override
    public LastHttpContent retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public LastHttpContent retain() {
        super.retain();
        return this;
    }

    @Override
    public LastHttpContent touch() {
        super.touch();
        return this;
    }

    @Override
    public LastHttpContent touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return EmptyHttpHeaders.INSTANCE;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(streamId: " + super.getStreamId() + ", data: " + content() +
                ", decoderResult: " + decoderResult() + ')';
    }
}
