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

public interface DecodedHttpRequest extends DecodedHttpMessage, HttpRequest {
    @Override
    DecodedHttpRequest setVersion(HttpVersion version);

    @Override
    DecodedHttpRequest setDecoderResult(DecoderResult result);

    @Override
    DecodedHttpRequest setMethod(HttpMethod method);

    @Override
    DecodedHttpRequest setUri(String uri);

    @Override
    DecodedHttpRequest setContent(ByteBuf content);

    @Override
    DecodedHttpRequest setContent(Stream<ByteBuf> content);
}
