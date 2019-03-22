/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http.websocketx.extensions;

import java.util.List;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.mockito.ArgumentMatcher;

import static org.mockito.Mockito.argThat;

public final class WebSocketExtensionTestUtil {

    public static HttpRequest newUpgradeRequest(String ext) {
        HttpRequest req = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/chat");

        req.headers().set(HttpHeaderNames.HOST, "server.example.com");
        req.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET.toString().toLowerCase());
        req.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        req.headers().set(HttpHeaderNames.ORIGIN, "http://example.com");
        if (ext != null) {
            req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, ext);
        }

        return req;
    }

    public static HttpResponse newUpgradeResponse(String ext) {
        HttpResponse res = new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);

        res.headers().set(HttpHeaderNames.HOST, "server.example.com");
        res.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET.toString().toLowerCase());
        res.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        res.headers().set(HttpHeaderNames.ORIGIN, "http://example.com");
        if (ext != null) {
            res.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, ext);
        }

        return res;
    }

    static final class WebSocketExtensionDataMatcher implements ArgumentMatcher<WebSocketExtensionData> {

        private final String name;

        WebSocketExtensionDataMatcher(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(WebSocketExtensionData data) {
            return data != null && name.equals(data.name());
        }
    }

    static WebSocketExtensionData webSocketExtensionDataMatcher(String text) {
        return argThat(new WebSocketExtensionDataMatcher(text));
    }

    private WebSocketExtensionTestUtil() {
        // unused
    }

    static class DummyEncoder extends WebSocketExtensionEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, WebSocketFrame msg,
                List<Object> out) throws Exception {
            // unused
        }
    }

    static class DummyDecoder extends WebSocketExtensionDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg,
                List<Object> out) throws Exception {
            // unused
        }
    }

    static class Dummy2Encoder extends WebSocketExtensionEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, WebSocketFrame msg,
                List<Object> out) throws Exception {
            // unused
        }
    }

    static class Dummy2Decoder extends WebSocketExtensionDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg,
                List<Object> out) throws Exception {
            // unused
        }
    }
}
