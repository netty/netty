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

/**
 * Combination of a {@link HttpResponse} and {@link FullHttpMessage}.
 * So it represent a <i>complete</i> http response.
 */
public interface FullHttpResponse extends HttpResponse, FullHttpMessage {
    @Override
    FullHttpResponse copy();

    @Override
    FullHttpResponse duplicate();

    @Override
    FullHttpResponse retainedDuplicate();

    @Override
    FullHttpResponse replace(ByteBuf content);

    @Override
    FullHttpResponse retain(int increment);

    @Override
    FullHttpResponse retain();

    @Override
    FullHttpResponse touch();

    @Override
    FullHttpResponse touch(Object hint);

    @Override
    FullHttpResponse setProtocolVersion(HttpVersion version);

    @Override
    FullHttpResponse setStatus(HttpResponseStatus status);
}
