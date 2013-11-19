/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.sockjs.transports;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.UPGRADE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY1;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY2;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_LOCATION;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class WebSocketHAProxyHandshakerTest {

    @Test
    public void isHAProxyRequest() {
        assertThat(WebSocketHAProxyHandshaker.isHAProxyReqeust(wsUpgradeRequest()), is(true));
        assertThat(WebSocketHAProxyHandshaker.isHAProxyReqeust(wsUpgradeRequestWithBody()), is(false));
    }

    @Test
    public void haProxyUpgradeRequest() throws Exception {
        final WebSocketHAProxyHandshaker handshaker =  new WebSocketHAProxyHandshaker("ws://localhost/websocket",
                null,
                65536);
        final FullHttpResponse response = handshaker.newHandshakeResponse(wsUpgradeRequest(), null);
        assertThat(response.getStatus(), is(HttpResponseStatus.SWITCHING_PROTOCOLS));
        assertThat(response.headers().get(CONNECTION), equalTo("Upgrade"));
        assertThat(response.headers().get(UPGRADE), equalTo("WebSocket"));
        assertThat(response.headers().get(SEC_WEBSOCKET_LOCATION), equalTo("ws://localhost/websocket"));
        assertThat(response.headers().get(SEC_WEBSOCKET_ORIGIN), equalTo("http://example.com"));
        assertThat(response.headers().get(CONTENT_LENGTH), is(nullValue()));

        ByteBuf content = Unpooled.copiedBuffer("^n:ds[4U", CharsetUtil.US_ASCII);
        final ByteBuf key = handshaker.calculateLastKey(content);
        assertThat(key.toString(CharsetUtil.US_ASCII), equalTo("8jKS'y:G*Co,Wxa-"));
    }

    private static FullHttpRequest wsUpgradeRequest() {
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/websocket");
        request.headers().set(HOST, "server.test.com");
        request.headers().set(UPGRADE, WEBSOCKET.toLowerCase());
        request.headers().set(CONNECTION, "Upgrade");
        request.headers().set(SEC_WEBSOCKET_KEY1, "4 @1  46546xW%0l 1 5");
        request.headers().set(SEC_WEBSOCKET_KEY2, "12998 5 Y3 1  .P00");
        request.headers().set(ORIGIN, "http://example.com");
        return request;
    }

    private static FullHttpRequest wsUpgradeRequestWithBody() {
        final FullHttpRequest request = wsUpgradeRequest();
        request.content().writeBytes(Unpooled.copiedBuffer("^n:ds[4U", CharsetUtil.US_ASCII));
        return request;
    }

}
