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
package io.netty.handler.codec.sockjs.handlers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.Charset;

import org.junit.Test;

public class CorsInboundHandlerTest {

    @Test
    public void preflightRequestWithOrigin() {
        final String origin = "xyz.com";
        final String corsHeaders = "content-type, xheader1";
        preflightRequest(origin, corsHeaders);
    }

    @Test
    public void preflightRequestWithNullOrigin() {
        final String origin = "null";
        final String expectedOrigin = "*";
        final String corsHeaders = "content-type, xheader1";
        preflightRequest(origin, expectedOrigin, corsHeaders);
    }

    public void preflightRequest(final String origin, final String corsHeaders) {
        preflightRequest(origin, origin, corsHeaders);
    }

    public void preflightRequest(final String origin, final String expectedOrigin, final String corsHeaders) {
        final FullHttpRequest httpRequest = createHttpRequest(HttpMethod.OPTIONS);

        httpRequest.headers().set(HttpHeaders.Names.ORIGIN, origin);
        httpRequest.headers().set(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS, corsHeaders);
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsInboundHandler());
        channel.writeInbound(httpRequest);

        final HttpResponse response = (HttpResponse) channel.readOutbound();
        final HttpHeaders headers = response.headers();
        assertThat(headers.get(HttpHeaders.Names.CONTENT_TYPE), is("text/plain; charset=UTF-8"));
        assertThat(headers.get(HttpHeaders.Names.CACHE_CONTROL), is("max-age=31536000, public"));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN), is(expectedOrigin));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE), is("31536000"));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS), is("OPTIONS, GET"));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS), is("Content-Type"));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(headers.get(HttpHeaders.Names.EXPIRES), is("dummy"));
        assertThat(headers.get(HttpHeaders.Names.SET_COOKIE), is("JSESSIONID=dummy;path=/"));
        channel.finish();
    }

    @Test
    public void verifyChannelAttributesNotPreflightRequestDefaults() {
        final HttpRequest httpRequest = createHttpRequest(HttpMethod.GET);
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsInboundHandler());
        channel.writeInbound(httpRequest);
        final CorsMetadata corsMetadata = channel.attr(CorsInboundHandler.CORS).get();
        assertThat(corsMetadata.origin(), is("*"));
        assertThat(corsMetadata.hasHeaders(), is(false));
        assertThat((HttpRequest) channel.readInbound(), equalTo(httpRequest));
        channel.finish();
    }

    @Test
    public void verifyChannelAttributesNotPreflightRequest() {
        final HttpRequest httpRequest = createHttpRequest(HttpMethod.GET);
        httpRequest.headers().set(HttpHeaders.Names.ORIGIN, "example.se");
        httpRequest.headers().set(HttpHeaders.Names.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsInboundHandler());
        channel.writeInbound(httpRequest);
        final CorsMetadata corsMetadata = channel.attr(CorsInboundHandler.CORS).get();
        assertThat(corsMetadata.origin(), is("example.se"));
        assertThat(corsMetadata.headers(), is("content-type"));
        assertThat((HttpRequest) channel.readInbound(), equalTo(httpRequest));
        channel.finish();
    }

    private FullHttpRequest createHttpRequest(HttpMethod method) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                method, "/info",
                Unpooled.copiedBuffer("", Charset.defaultCharset()));
    }

}
