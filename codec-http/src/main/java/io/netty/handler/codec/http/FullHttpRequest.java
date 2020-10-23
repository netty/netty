/*
 * Copyright 2013 The Netty Project
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

/**
 * Combine the {@link HttpRequest} and {@link FullHttpMessage}, so the request is a <i>complete</i> HTTP
 * request.
 */
public interface FullHttpRequest extends HttpRequest, FullHttpMessage {
    @Override
    FullHttpRequest copy();

    @Override
    FullHttpRequest duplicate();

    @Override
    FullHttpRequest retainedDuplicate();

    @Override
    FullHttpRequest replace(ByteBuf content);

    @Override
    FullHttpRequest retain(int increment);

    @Override
    FullHttpRequest retain();

    @Override
    FullHttpRequest touch();

    @Override
    FullHttpRequest touch(Object hint);

    @Override
    FullHttpRequest setProtocolVersion(HttpVersion version);

    @Override
    FullHttpRequest setMethod(HttpMethod method);

    @Override
    FullHttpRequest setUri(String uri);
}
