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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class DefaultDecodedHttpResponse extends DefaultHttpResponse implements DecodedHttpResponse {

    private DecoderResult decoderResult = DecoderResult.UNFINISHED;

    public DefaultDecodedHttpResponse(HttpVersion version, HttpResponseStatus status) {
        super(version, status);
    }

    @Override
    public DecoderResult getDecoderResult() {
        return decoderResult;
    }

    @Override
    public DecodedHttpResponse setDecoderResult(DecoderResult decoderResult) {
        if (decoderResult == null) {
            throw new NullPointerException("decoderResult");
        }
        this.decoderResult = decoderResult;
        return this;
    }

    @Override
    public DecodedHttpResponse setVersion(HttpVersion version) {
        return (DecodedHttpResponse) super.setVersion(version);
    }

    @Override
    public DecodedHttpResponse setStatus(HttpResponseStatus status) {
        return (DecodedHttpResponse) super.setStatus(status);
    }

    @Override
    public DecodedHttpResponse setContent(ByteBuf content) {
        return (DecodedHttpResponse) super.setContent(content);
    }

    @Override
    public DecodedHttpResponse setContent(Stream<ByteBuf> content) {
        return (DecodedHttpResponse) super.setContent(content);
    }
}
