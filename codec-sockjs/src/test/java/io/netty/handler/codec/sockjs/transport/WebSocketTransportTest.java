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
package io.netty.handler.codec.sockjs.transport;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeaders.Names.ALLOW;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.UPGRADE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_PLAIN;
import static io.netty.handler.codec.sockjs.util.HttpRequestBuilder.wsUpgradeRequest;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.assertChannelFinished;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.assertWebSocketUpgradeResponse;
import static io.netty.handler.codec.sockjs.util.TestChannels.webSocketChannel;
import static io.netty.handler.codec.sockjs.util.HttpUtil.decode;
import static io.netty.handler.codec.sockjs.util.HttpUtil.decodeFullHttpResponse;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class WebSocketTransportTest {

    @Test
    public void upgradeRequest() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);
        ch.writeInbound(wsUpgradeRequest("/websocket", WebSocketVersion.V13).build());
        assertWebSocketUpgradeResponse(ch);
        assertChannelFinished(ch);
    }

    @Test
    public void invalidHttpMethod() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);
        ch.writeInbound(new DefaultFullHttpRequest(HTTP_1_1, POST, "dummy"));
        final HttpResponse response = decode(ch);
        assertThat(response.status(), is(METHOD_NOT_ALLOWED));
        assertThat(response.headers().getAndConvert(ALLOW), is(GET.toString()));
        assertChannelFinished(ch);
    }

    @Test
    public void nonUpgradeRequest() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);
        ch.writeInbound(new DefaultFullHttpRequest(HTTP_1_1, GET, "/websocket"));

        final FullHttpResponse response = decodeFullHttpResponse(ch);
        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), is(CONTENT_TYPE_PLAIN));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("Can \"Upgrade\" only to \"WebSocket\"."));
        response.release();
        assertChannelFinished(ch);
    }

    @Test
    public void invalidConnectionHeader() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);

        ch.writeInbound(wsUpgradeRequest("/websocket", WebSocketVersion.V13)
                .header(UPGRADE, Values.WEBSOCKET)
                .header(CONNECTION, Values.CLOSE)
                .build());

        final FullHttpResponse response = decodeFullHttpResponse(ch);
        assertThat(response.status(), is(BAD_REQUEST));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("\"Connection\" must be \"Upgrade\"."));
        response.release();
        assertChannelFinished(ch);
    }

    @Test
    public void invalidJsonInWebSocketFrame() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);
        ch.writeInbound(wsUpgradeRequest("/websocket", WebSocketVersion.V13).build());
        assertWebSocketUpgradeResponse(ch);

        ch.writeInbound(new TextWebSocketFrame("[invalidJson"));
        assertThat(ch.isOpen(), is(false));
        assertChannelFinished(ch);
    }

    @Test
    public void firefox602ConnectionHeader() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);

        ch.writeInbound(wsUpgradeRequest("/websocket", WebSocketVersion.V08)
                .header(CONNECTION, "keep-alive, Upgrade")
                .build());
        assertWebSocketUpgradeResponse(ch);
        assertChannelFinished(ch);
    }

    @Test
    public void headersSanity() throws Exception {
        sendUpgradeAndAssertHeaders(WebSocketVersion.V07);
        sendUpgradeAndAssertHeaders(WebSocketVersion.V08);
        sendUpgradeAndAssertHeaders(WebSocketVersion.V13);
    }

    private static void sendUpgradeAndAssertHeaders(final WebSocketVersion version) throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = webSocketChannel(config);
        ch.writeInbound(wsUpgradeRequest("/websocket", version).build());
        final HttpResponse response = decode(ch);
        assertThat(response.status(), is(HttpResponseStatus.SWITCHING_PROTOCOLS));
        assertThat(response.headers().getAndConvert(CONNECTION), equalTo("upgrade"));
        assertThat(response.headers().getAndConvert(UPGRADE), equalTo("websocket"));
        assertThat(response.headers().get(CONTENT_LENGTH), is(nullValue()));
        assertChannelFinished(ch);
    }

}
