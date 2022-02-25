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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Resource;
import io.netty5.channel.ChannelPipeline;

/**
 * An HTTP chunk which is used for HTTP chunked transfer-encoding.
 * {@link HttpObjectDecoder} generates {@link HttpContent} after
 * {@link HttpMessage} when the content is large or the encoding of the content
 * is 'chunked.  If you prefer not to receive {@link HttpContent} in your handler,
 * place {@link HttpObjectAggregator} after {@link HttpObjectDecoder} in the
 * {@link ChannelPipeline}.
 */
public interface HttpContent<R extends HttpContent<R>> extends HttpObject, Resource<R> {

    /**
     * Returns the {@link Buffer} representing the payload of the HTTP message.
     *
     * @return The {@link Buffer} representing the payload of the HTTP message.
     */
    Buffer payload();
}
