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

package io.netty.handler.codec.httpx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.stream.Stream;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class DefaultDecodedHttpRequest extends DefaultHttpRequest implements DecodedHttpRequest {

    private DecoderResult decoderResult = DecoderResult.UNFINISHED;

    public DefaultDecodedHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
        super(httpVersion, method, uri);
    }

    @Override
    public DecoderResult getDecoderResult() {
        return decoderResult;
    }

    @Override
    public DecodedHttpRequest setDecoderResult(DecoderResult decoderResult) {
        if (decoderResult == null) {
            throw new NullPointerException("decoderResult");
        }
        this.decoderResult = decoderResult;
        return this;
    }

    @Override
    public DecodedHttpRequest setVersion(HttpVersion version) {
        return (DecodedHttpRequest) super.setVersion(version);
    }

    @Override
    public DecodedHttpRequest setMethod(HttpMethod method) {
        return (DecodedHttpRequest) super.setMethod(method);
    }

    @Override
    public DecodedHttpRequest setUri(String uri) {
        return (DecodedHttpRequest) super.setUri(uri);
    }

    @Override
    public DecodedHttpRequest setContent(ByteBuf content) {
        return (DecodedHttpRequest) super.setContent(content);
    }

    @Override
    public DecodedHttpRequest setContent(Stream<ByteBuf> content) {
        return (DecodedHttpRequest) super.setContent(content);
    }
}
