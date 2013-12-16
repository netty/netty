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
package io.netty.handler.codec.http.cors;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;

public class CorsHandlerTest {

    @Test
    public void nonCorsRequest() {
        final HttpResponse response = simpleRequest(CorsConfig.anyOrigin().build(), null);
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
    }

    @Test
    public void simpleRequestWithAnyOrigin() {
        final HttpResponse response = simpleRequest(CorsConfig.anyOrigin().build(), "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
    }

    @Test
    public void simpleRequestWithOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(CorsConfig.withOrigin(origin).build(), origin);
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin));
    }

    @Test
    public void preflightDeleteRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .allowedRequestMethods(HttpMethod.GET, HttpMethod.DELETE)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_ALLOW_METHODS), hasItems("GET", "DELETE"));
    }

    @Test
    public void preflightGetRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .allowedRequestMethods(HttpMethod.OPTIONS, HttpMethod.GET, HttpMethod.DELETE)
                .allowedRequestHeaders("content-type", "xheader1")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_ALLOW_METHODS), hasItems("OPTIONS", "GET"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_ALLOW_HEADERS), hasItems("content-type", "xheader1"));
    }

    @Test
    public void preflightRequestWithNullOrigin() {
        final String origin = "null";
        final CorsConfig config = CorsConfig.withOrigin(origin).allowNullOrigin().build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(equalTo("*")));
    }

    @Test
    public void preflightRequestAllowCredentials() {
        final String origin = "null";
        final CorsConfig config = CorsConfig.withOrigin(origin).allowCredentials().build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(equalTo("true")));
    }

    @Test
    public void preflightRequestDoNotAllowCredentials() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "");
        // the only valid value for Access-Control-Allow-Credentials is true.
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(false));
    }

    @Test
    public void simpleRequestCustomHeaders() {
        final CorsConfig config = CorsConfig.anyOrigin().exposeHeaders("custom1", "custom2").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777", "");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_EXPOSE_HEADERS), hasItems("custom1", "custom1"));
    }

    @Test
    public void simpleRequestAllowCredentials() {
        final CorsConfig config = CorsConfig.anyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777", "");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
    }

    @Test
    public void simpleRequestDoNotAllowCredentials() {
        final CorsConfig config = CorsConfig.anyOrigin().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777", "");
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(false));
    }

    @Test
    public void simpleRequestExposeHeaders() {
        final CorsConfig config = CorsConfig.anyOrigin().exposeHeaders("one", "two").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777", "");
        assertThat(response.headers().getAll(ACCESS_CONTROL_EXPOSE_HEADERS), hasItems("one", "two"));
    }

    private static HttpResponse simpleRequest(final CorsConfig config, final String origin) {
        return simpleRequest(config, origin, null);
    }

    private static HttpResponse simpleRequest(final CorsConfig config,
                                              final String origin,
                                              final String requestHeaders) {
        return simpleRequest(config, origin, requestHeaders, GET);
    }

    private static HttpResponse simpleRequest(final CorsConfig config,
                                              final String origin,
                                              final String requestHeaders,
                                              final HttpMethod method) {
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config), new EchoHandler());
        final FullHttpRequest httpRequest = createHttpRequest(method);
        if (origin != null) {
            httpRequest.headers().set(ORIGIN, origin);
        }
        if (requestHeaders != null) {
            httpRequest.headers().set(ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
        }
        channel.writeInbound(httpRequest);
        return (HttpResponse) channel.readOutbound();
    }

    private static HttpResponse preflightRequest(final CorsConfig config,
                                                 final String origin,
                                                 final String requestHeaders) {
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest httpRequest = createHttpRequest(OPTIONS);
        httpRequest.headers().set(ORIGIN, origin);
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_METHOD, httpRequest.getMethod().toString());
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
        channel.writeInbound(httpRequest);
        return (HttpResponse) channel.readOutbound();
    }

    private static FullHttpRequest createHttpRequest(HttpMethod method) {
        return new DefaultFullHttpRequest(HTTP_1_1, method, "/info");
    }

    private static class EchoHandler extends ChannelHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.writeAndFlush(new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK));
        }
    }

}
