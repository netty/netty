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
import static io.netty.handler.codec.sockjs.SockJsTestUtil.assertCORSHeaders;
import static io.netty.handler.codec.sockjs.SockJsTestUtil.verifyNoCacheHeaders;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.handler.codec.sockjs.protocol.PreludeFrame;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class XhrStreamingTransportTest {

    @Test
    public void messageReceived() {
        final EmbeddedChannel ch = newStreamingChannel();
        ch.writeOutbound(new OpenFrame());

        final HttpResponse response = (HttpResponse) ch.readOutbound();
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        assertThat(response.headers().get(CONTENT_TYPE), equalTo(Transports.CONTENT_TYPE_JAVASCRIPT));
        assertThat(response.headers().get(TRANSFER_ENCODING), equalTo(CHUNKED));
        assertCORSHeaders(response, "*");
        verifyNoCacheHeaders(response);

        final DefaultHttpContent prelude = (DefaultHttpContent) ch.readOutbound();
        assertThat(prelude.content().readableBytes(), is(PreludeFrame.CONTENT_SIZE + 1));
        prelude.content().readBytes(Unpooled.buffer(PreludeFrame.CONTENT_SIZE + 1));

        final DefaultHttpContent openResponse = (DefaultHttpContent) ch.readOutbound();
        assertThat(openResponse.content().toString(CharsetUtil.UTF_8), equalTo("o\n"));
        ch.finish();
    }

    private EmbeddedChannel newStreamingChannel() {
        return newStreamingChannel(SockJsConfig.withPrefix("/test").cookiesNeeded().build());
    }

    private EmbeddedChannel newStreamingChannel(final SockJsConfig config) {
        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/xhr-streaming");
        final XhrStreamingTransport transport = new XhrStreamingTransport(config, request);
        final EmbeddedChannel ch = new EmbeddedChannel(transport);
        return ch;
    }

}
