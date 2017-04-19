/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;

public final class HttpUtil {

    private HttpUtil() {
    }

    public static HttpResponse decode(final EmbeddedChannel channel) throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new HttpObjectAggregator(8192), new HttpResponseDecoder());
        ch.writeInbound(channel.readOutbound());
        final HttpResponse response = ch.readInbound();
        ch.finish();
        return response;
    }

    public static FullHttpResponse decodeFullResponse(final EmbeddedChannel channel) throws Exception {
        final HttpResponse response = decode(channel);
        final ByteBuf content = channel.readOutbound();
        final DefaultFullHttpResponse fullResponse = new DefaultFullHttpResponse(response.protocolVersion(),
                    response.status(), content);
        fullResponse.headers().add(response.headers());
        return fullResponse;
    }

    public static FullHttpResponse decodeFullHttpResponse(final EmbeddedChannel channel) throws Exception {
        final EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(channel.readOutbound());
        final HttpResponse response = ch.readInbound();
        final HttpContent content = ch.readInbound();
        final DefaultFullHttpResponse fullResponse = new DefaultFullHttpResponse(response.protocolVersion(),
                response.status(), content.content());
        fullResponse.headers().add(response.headers());
        return fullResponse;
    }

}
