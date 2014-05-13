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
package io.netty.handler.codec.sockjs.handler;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import org.junit.Test;

public class CorsOutboundHandlerTest {

    @Test
    public void flush() {
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsOutboundHandler());
        channel.attr(CorsInboundHandler.CORS).set(new CorsMetadata("xyz.com", "content-type"));
        boolean write = channel.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK));
        assertThat(write, is(true));

        final HttpResponse response = channel.readOutbound();
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_0));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("xyz.com"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), equalTo("content-type"));
        channel.finish();
    }

    @Test
    public void flushWithoutPriorOptionsRequest() {
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsOutboundHandler());
        channel.attr(CorsInboundHandler.CORS).set(new CorsMetadata());
        channel.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));

        final HttpResponse response = channel.readOutbound();
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        channel.finish();
    }

}
