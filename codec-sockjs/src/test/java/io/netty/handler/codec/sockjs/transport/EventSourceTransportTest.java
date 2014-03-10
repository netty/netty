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

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.sockjs.SockJsTestUtil.verifyNoCacheHeaders;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;

import org.junit.Test;

public class EventSourceTransportTest {

    @Test
    public void write() {
        final EmbeddedChannel ch = newEventSourceChannel();
        ch.writeOutbound(new OpenFrame());

        final HttpResponse response = ch.readOutbound();
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        assertThat(response.headers().get(CONTENT_TYPE), equalTo(EventSourceTransport.CONTENT_TYPE_EVENT_STREAM));
        verifyNoCacheHeaders(response);

        final DefaultHttpContent newLinePrelude = ch.readOutbound();
        assertThat(newLinePrelude.content().toString(UTF_8), equalTo("\r\n"));
        final DefaultHttpContent data = ch.readOutbound();
        assertThat(data.content().toString(UTF_8), equalTo("data: o\r\n\r\n"));
    }

    private static EmbeddedChannel newEventSourceChannel() {
        return newStreamingChannel(SockJsConfig.withPrefix("/test").cookiesNeeded().build());
    }

    private static EmbeddedChannel newStreamingChannel(final SockJsConfig config) {
        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, Transports.Type.EVENTSOURCE.path());
        final EventSourceTransport transport = new EventSourceTransport(config, request);
        return new EmbeddedChannel(transport);
    }

}
