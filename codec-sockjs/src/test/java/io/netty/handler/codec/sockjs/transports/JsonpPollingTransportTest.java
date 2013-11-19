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

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.sockjs.SockJsTestUtil.verifyNoCacheHeaders;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.CloseFrame;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.protocol.HeartbeatFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class JsonpPollingTransportTest {

    @Test
    public void flushMessageFrame() {
        final FullHttpResponse response = writeFrame(new MessageFrame("a"));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        verifyNoCacheHeaders(response);
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("callback(\"a[\\\"a\\\"]\");\r\n"));
    }

    @Test
    public void flushOpenFrame() {
        final FullHttpResponse response = writeFrame(new OpenFrame());
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        verifyNoCacheHeaders(response);
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("callback(\"o\");\r\n"));
    }

    @Test
    public void flushCloseFrame() {
        final FullHttpResponse response = writeFrame(new CloseFrame(2000, "Oh no"));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        verifyNoCacheHeaders(response);
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("callback(\"c[2000,\\\"Oh no\\\"]\");\r\n"));
    }

    @Test
    public void flushHeartbeatFrame() {
        final FullHttpResponse response = writeFrame(new HeartbeatFrame());
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        verifyNoCacheHeaders(response);
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("callback(\"h\");\r\n"));
    }

    @Test
    public void flushNoCallbackSet() {
        final FullHttpResponse response = writeFrame(new HeartbeatFrame(), false);
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("\"callback\" parameter required"));
    }

    private static FullHttpResponse writeFrame(final Frame frame) {
        return writeFrame(frame, true);
    }

    private static FullHttpResponse writeFrame(final Frame frame, final boolean withCallback) {
        final String queryUrl = withCallback ? "/jsonp?c=callback" : "/jsonp";
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, queryUrl);
        final SockJsConfig config = SockJsConfig.withPrefix(queryUrl).cookiesNeeded().build();
        final JsonpPollingTransport jsonpPollingOutbound = new JsonpPollingTransport(config, request);
        final EmbeddedChannel ch = new EmbeddedChannel(jsonpPollingOutbound);
        ch.writeInbound(request);
        ch.writeOutbound(frame);
        final FullHttpResponse response =  (FullHttpResponse) ch.readOutbound();
        ch.finish();
        return response;
    }

}
