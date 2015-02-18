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

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Callable;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
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
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
    }

    @Test
    public void simpleRequestWithOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(CorsConfig.withOrigin(origin).build(), origin);
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin));
    }

    @Test
    public void simpleRequestWithOrigins() {
        final String origin1 = "http://localhost:8888";
        final String origin2 = "https://localhost:8888";
        final String[] origins = {origin1, origin2};
        final HttpResponse response1 = simpleRequest(CorsConfig.withOrigins(origins).build(), origin1);
        assertThat(response1.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin1));
        final HttpResponse response2 = simpleRequest(CorsConfig.withOrigins(origins).build(), origin2);
        assertThat(response2.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin2));
    }

    @Test
    public void simpleRequestWithNoMatchingOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(CorsConfig.withOrigins("https://localhost:8888").build(), origin);
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
    }

    @Test
    public void preflightDeleteRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .allowedRequestMethods(GET, DELETE)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_METHODS), containsString("GET"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_METHODS), containsString("DELETE"));
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void preflightGetRequestWithCustomHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .allowedRequestMethods(OPTIONS, GET, DELETE)
                .allowedRequestHeaders("content-type", "xheader1")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_METHODS), containsString("OPTIONS"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_METHODS), containsString("GET"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_HEADERS), containsString("content-type"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_HEADERS), containsString("xheader1"));
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void preflightRequestWithDefaultHeaders() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().getAndConvert(CONTENT_LENGTH), is("0"));
        assertThat(response.headers().get(DATE), is(notNullValue()));
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void preflightRequestWithCustomHeader() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", "somevalue")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().getAndConvert("CustomHeader"), equalTo("somevalue"));
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void preflightRequestWithCustomHeaders() {
        final String headerName = "CustomHeader";
        final String value1 = "value1";
        final String value2 = "value2";
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader(headerName, value1, value2)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertValues(response, headerName, value1, value2);
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void preflightRequestWithCustomHeadersIterable() {
        final String headerName = "CustomHeader";
        final String value1 = "value1";
        final String value2 = "value2";
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader(headerName, Arrays.asList(value1, value2))
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertValues(response, headerName, value1, value2);
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
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
        assertThat(response.headers().getAndConvert("GenHeader"), equalTo("generatedValue"));
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void preflightRequestWithNullOrigin() {
        final String origin = "null";
        final CorsConfig config = CorsConfig.withOrigin(origin)
                .allowNullOrigin()
                .allowCredentials()
                .build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is(equalTo("*")));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), is(equalTo("*")));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(nullValue()));
    }

    @Test
    public void preflightRequestAllowCredentials() {
        final String origin = "null";
        final CorsConfig config = CorsConfig.withOrigin(origin).allowCredentials().build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(equalTo("true")));
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
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("custom1"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("custom2"));
    }

    @Test
    public void simpleRequestAllowCredentials() {
        final CorsConfig config = CorsConfig.withAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
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
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("http://localhost:7777"));
        assertThat(response.headers().getAndConvert(VARY), equalTo(ORIGIN.toString()));
    }

    @Test
    public void simpleRequestExposeHeaders() {
        final CorsConfig config = CorsConfig.withAnyOrigin().exposeHeaders("one", "two").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("one"));
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("two"));
    }

    @Test
    public void simpleRequestShortCurcuit() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").shortCurcuit().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.status(), is(FORBIDDEN));
    }

    @Test
    public void simpleRequestNoShortCurcuit() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8080").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
    }

    @Test
    public void shortCurcuitNonCorsRequest() {
        final CorsConfig config = CorsConfig.withOrigin("https://localhost").shortCurcuit().build();
        final HttpResponse response = simpleRequest(config, null);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
    }

    @Test
    public void preflightRequestShouldReleaseRequest() {
        final CorsConfig config = CorsConfig.withOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", Arrays.asList("value1", "value2"))
                .build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "content-type, xheader1");
        channel.writeInbound(request);
        assertThat(request.refCnt(), is(0));
    }

    @Test
    public void forbiddenShouldReleaseRequest() {
        final CorsConfig config = CorsConfig.withOrigin("https://localhost").shortCurcuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config), new EchoHandler());
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        channel.writeInbound(request);
        assertThat(request.refCnt(), is(0));
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
        channel.writeInbound(optionsRequest(origin, requestHeaders));
        return (HttpResponse) channel.readOutbound();
    }

    private static FullHttpRequest optionsRequest(final String origin, final String requestHeaders) {
        final FullHttpRequest httpRequest = createHttpRequest(OPTIONS);
        httpRequest.headers().set(ORIGIN, origin);
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_METHOD, httpRequest.method().toString());
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_HEADERS, requestHeaders);
        return httpRequest;
    }

    private static FullHttpRequest createHttpRequest(HttpMethod method) {
        return new DefaultFullHttpRequest(HTTP_1_1, method, "/info");
    }

    private static class EchoHandler extends ChannelHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, OK, true, true));
        }
    }

    private static void assertValues(final HttpResponse response, final String headerName, final String... values) {
        final String header = response.headers().getAndConvert(headerName);
        for (String value : values) {
            assertThat(header, containsString(value));
        }
    }

}
