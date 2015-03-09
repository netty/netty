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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;

import java.util.List;

public final class WebSocketExtensionTestUtil {

    public static HttpRequest newUpgradeRequest(String ext) {
        HttpRequest req = ReferenceCountUtil.releaseLater(new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/chat"));

        req.headers().set(HttpHeaderNames.HOST, "server.example.com");
        req.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        req.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        req.headers().set(HttpHeaderNames.ORIGIN, "http://example.com");
        if (ext != null) {
            req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, ext);
        }

        return req;
    }

    public static HttpResponse newUpgradeResponse(String ext) {
        HttpResponse res = ReferenceCountUtil.releaseLater(new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));

        res.headers().set(HttpHeaderNames.HOST, "server.example.com");
        res.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        res.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        res.headers().set(HttpHeaderNames.ORIGIN, "http://example.com");
        if (ext != null) {
            res.headers().set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, ext);
        }

        return res;
    }

    public static WebSocketExtensionData webSocketExtensionDataEqual(String name) {
        EasyMock.reportMatcher(new WebSocketExtensionDataMatcher(name));
        return null;
    }

    public static class WebSocketExtensionDataMatcher implements IArgumentMatcher {

        private final String name;

        public WebSocketExtensionDataMatcher(String name) {
            this.name = name;
        }

        @Override
        public void appendTo(StringBuffer buf) {
            buf.append("WebSocketExtensionData with name=").append(name);
        }

        @Override
        public boolean matches(Object o) {
            return o instanceof WebSocketExtensionData &&
                    name.equals(((WebSocketExtensionData) o).name());
        }
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
