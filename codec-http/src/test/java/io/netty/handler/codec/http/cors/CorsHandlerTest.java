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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Callable;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

public class CorsHandlerTest {

    @Test
    public void nonCorsRequest() {
        final HttpResponse response = simpleRequest(CorsConfig.withAnyOrigin().build(), null);
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
    }

    @Test
    public void simpleRequestWithAnyOrigin() {
        final HttpResponse response = simpleRequest(CorsConfig.withAnyOrigin().build(), "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
    }

    @Test
    public void simpleRequestWithOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(CorsConfig.withOrigin(origin).build(), origin);
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
    }

    @Test
    public void simpleRequestWithOrigins() {
        final String origin1 = "http://localhost:8888";
        final String origin2 = "https://localhost:8888";
        final String[] origins = {origin1, origin2};
        final HttpResponse response1 = simpleRequest(CorsConfig.withOrigins(origins).build(), origin1);
        assertThat(response1.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin1));
        assertThat(response1.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        final HttpResponse response2 = simpleRequest(CorsConfig.withOrigins(origins).build(), origin2);
        assertThat(response2.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin2));
        assertThat(response2.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
    }

    @Test
    public void simpleRequestWithNoMatchingOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(CorsConfig.withOrigins("https://localhost:8888").build(), origin);
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
    }

    @Test
    public void preflightDeleteRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .allowedRequestMethods(GET, DELETE)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_ALLOW_METHODS), hasItems("GET", "DELETE"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightGetRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .allowedRequestMethods(OPTIONS, GET, DELETE)
                .allowedRequestHeaders("content-type", "xheader1")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_ALLOW_METHODS), hasItems("OPTIONS", "GET"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_ALLOW_HEADERS), hasItems("content-type", "xheader1"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightRequestWithDefaultHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(CONTENT_LENGTH), is("0"));
        assertThat(response.headers().get(DATE), is(notNullValue()));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightRequestWithCustomHeader() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", "somevalue")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get("CustomHeader"), equalTo("somevalue"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", "value1", "value2")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().getAll("CustomHeader"), hasItems("value1", "value2"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightRequestWithCustomHeadersIterable() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", Arrays.asList("value1", "value2"))
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().getAll("CustomHeader"), hasItems("value1", "value2"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightRequestWithValueGenerator() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("GenHeader", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "generatedValue";
                    }
                }).build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get("GenHeader"), equalTo("generatedValue"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void preflightRequestWithNullOrigin() {
        final String origin = "null";
        final CorsConfig config = CorsConfig.withOrigin(origin)
                .allowNullOrigin()
                .allowCredentials()
                .build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(equalTo("*")));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(nullValue()));
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
        final CorsConfig config = CorsConfig.withAnyOrigin().exposeHeaders("custom1", "custom2").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(response.headers().getAll(ACCESS_CONTROL_EXPOSE_HEADERS), hasItems("custom1", "custom1"));
    }

    @Test
    public void simpleRequestAllowCredentials() {
        final CorsConfig config = CorsConfig.withAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
    }

    @Test
    public void simpleRequestDoNotAllowCredentials() {
        final CorsConfig config = CorsConfig.withAnyOrigin().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(false));
    }

    @Test
    public void anyOriginAndAllowCredentialsShouldEchoRequestOrigin() {
        final CorsConfig config = CorsConfig.withAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("http://localhost:7777"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN));
    }

    @Test
    public void simpleRequestExposeHeaders() {
        final CorsConfig config = CorsConfig.withAnyOrigin().exposeHeaders("one", "two").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().getAll(ACCESS_CONTROL_EXPOSE_HEADERS), hasItems("one", "two"));
    }

    @Test
    public void simpleRequestShortCurcuit() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").shortCurcuit().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.getStatus(), is(FORBIDDEN));
    }

    @Test
    public void simpleRequestNoShortCurcuit() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.getStatus(), is(OK));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
    }

    @Test
    public void shortCurcuitNonCorsRequest() {
        final CorsConfig config = CorsConfig.withOrigin("https://localhost").shortCurcuit().build();
        final HttpResponse response = simpleRequest(config, null);
        assertThat(response.getStatus(), is(OK));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
    }

    @Test
    public void shortCurcuitWithConnectionKeepAliveShouldStayOpen() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").shortCurcuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        request.headers().set(CONNECTION, KEEP_ALIVE);

        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(HttpHeaders.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.getStatus(), is(FORBIDDEN));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void shortCurcuitWithoutConnectionShouldStayOpen() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").shortCurcuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");

        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(HttpHeaders.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.getStatus(), is(FORBIDDEN));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void shortCurcuitWithConnectionCloseShouldClose() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").shortCurcuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        request.headers().set(CONNECTION, CLOSE);

        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(HttpHeaders.isKeepAlive(response), is(false));

        assertThat(channel.isOpen(), is(false));
        assertThat(response.getStatus(), is(FORBIDDEN));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestShouldReleaseRequest() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", Arrays.asList("value1", "value2"))
                .build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "content-type, xheader1", null);
        assertThat(channel.writeInbound(request), is(false));
        assertThat(request.refCnt(), is(0));
        assertThat(ReferenceCountUtil.release(channel.readOutbound()), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestWithConnectionKeepAliveShouldStayOpen() throws Exception {

        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", KEEP_ALIVE);
        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(HttpHeaders.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.getStatus(), is(OK));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestWithoutConnectionShouldStayOpen() throws Exception {

        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", null);
        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(HttpHeaders.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.getStatus(), is(OK));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestWithConnectionCloseShouldClose() throws Exception {

        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", CLOSE);
        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(HttpHeaders.isKeepAlive(response), is(false));

        assertThat(channel.isOpen(), is(false));
        assertThat(response.getStatus(), is(OK));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void forbiddenShouldReleaseRequest() {
        final CorsConfig config = CorsConfig.withOrigin("https://localhost").shortCurcuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config), new EchoHandler());
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        assertThat(channel.writeInbound(request), is(false));
        assertThat(request.refCnt(), is(0));
        assertThat(ReferenceCountUtil.release(channel.readOutbound()), is(true));
        assertThat(channel.finish(), is(false));
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
        assertThat(channel.writeInbound(httpRequest), is(false));
        return (HttpResponse) channel.readOutbound();
    }

    private static HttpResponse preflightRequest(final CorsConfig config,
                                                 final String origin,
                                                 final String requestHeaders) {
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        assertThat(channel.writeInbound(optionsRequest(origin, requestHeaders, null)), is(false));
        HttpResponse response = (HttpResponse) channel.readOutbound();
        assertThat(channel.finish(), is(false));
        return response;
    }

    private static FullHttpRequest optionsRequest(final String origin,
                                                  final String requestHeaders,
                                                  final CharSequence connection) {
        final FullHttpRequest httpRequest = createHttpRequest(OPTIONS);
        httpRequest.headers().set(ORIGIN, origin);
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_METHOD, httpRequest.getMethod().toString());
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
        if (connection != null) {
            httpRequest.headers().set(CONNECTION, connection);
        }

        return httpRequest;
    }

    private static FullHttpRequest createHttpRequest(HttpMethod method) {
        return new DefaultFullHttpRequest(HTTP_1_1, method, "/info");
    }

    private static class EchoHandler extends SimpleChannelInboundHandler<Object> {

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, OK));
        }
    }

}
