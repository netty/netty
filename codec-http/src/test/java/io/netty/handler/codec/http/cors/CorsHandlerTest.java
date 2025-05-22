/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http.cors;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.cors.CorsConfigBuilder.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CorsHandlerTest {

    @Test
    public void nonCorsRequest() {
        final HttpResponse response = simpleRequest(forAnyOrigin().build(), null);
        assertFalse(response.headers().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestWithAnyOrigin() {
        final HttpResponse response = simpleRequest(forAnyOrigin().build(), "http://localhost:7777");
        assertEquals("*", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestWithNullOrigin() {
        final HttpResponse response = simpleRequest(forOrigin("http://test.com").allowNullOrigin()
                .allowCredentials()
                .build(), "null");
        assertEquals("null", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestWithOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(forOrigin(origin).build(), origin);
        assertEquals(origin, response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestWithOrigins() {
        final String origin1 = "http://localhost:8888";
        final String origin2 = "https://localhost:8888";
        final String[] origins = {origin1, origin2};
        final HttpResponse response1 = simpleRequest(forOrigins(origins).build(), origin1);
        assertEquals(origin1, response1.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response1.headers().get(ACCESS_CONTROL_ALLOW_HEADERS));
        assertTrue(ReferenceCountUtil.release(response1));

        final HttpResponse response2 = simpleRequest(forOrigins(origins).build(), origin2);
        assertEquals(origin2, response2.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response2.headers().get(ACCESS_CONTROL_ALLOW_HEADERS));
        assertTrue(ReferenceCountUtil.release(response2));
    }

    @Test
    public void simpleRequestWithNoMatchingOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(
                forOrigins("https://localhost:8888").build(), origin);
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightDeleteRequestWithCustomHeaders() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .allowedRequestMethods(GET, DELETE)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertEquals("http://localhost:8888", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS)).contains("GET");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS)).contains("DELETE");
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightGetRequestWithCustomHeaders() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .allowedRequestMethods(OPTIONS, GET, DELETE)
                .allowedRequestHeaders("content-type", "xheader1")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertEquals("http://localhost:8888", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS)).contains("OPTIONS");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS)).contains("GET");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS)).contains("content-type");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS)).contains("xheader1");
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithDefaultHeaders() {
        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertEquals("0", response.headers().get(CONTENT_LENGTH));
        assertNotNull(response.headers().get(DATE));
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithCustomHeader() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", "somevalue")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertEquals("somevalue", response.headers().get(of("CustomHeader")));
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertEquals("0", response.headers().get(CONTENT_LENGTH));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithUnauthorizedOrigin() {
        final String origin = "http://host";
        final CorsConfig config = forOrigin("http://localhost").build();
        final HttpResponse response = preflightRequest(config, origin, "xheader1");
        assertFalse(response.headers().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithCustomHeaders() {
        final String headerName = "CustomHeader";
        final String value1 = "value1";
        final String value2 = "value2";
        final CorsConfig config = forOrigin("http://localhost:8888")
                .preflightResponseHeader(headerName, value1, value2)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertValues(response, headerName, value1, value2);
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithCustomHeadersIterable() {
        final String headerName = "CustomHeader";
        final String value1 = "value1";
        final String value2 = "value2";
        final CorsConfig config = forOrigin("http://localhost:8888")
                .preflightResponseHeader(headerName, Arrays.asList(value1, value2))
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertValues(response, headerName, value1, value2);
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithValueGenerator() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .preflightResponseHeader("GenHeader", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "generatedValue";
                    }
                }).build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertEquals("generatedValue", response.headers().get(of("GenHeader")));
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestWithNullOrigin() {
        final String origin = "null";
        final CorsConfig config = forOrigin(origin)
                .allowNullOrigin()
                .allowCredentials()
                .build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertEquals("null", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestAllowCredentials() {
        final String origin = "null";
        final CorsConfig config = forOrigin(origin).allowCredentials().build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertEquals("true", response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void preflightRequestDoNotAllowCredentials() {
        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "");
        // the only valid value for Access-Control-Allow-Credentials is true.
        assertFalse(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestCustomHeaders() {
        final CorsConfig config = forAnyOrigin().exposeHeaders("custom1", "custom2").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertEquals("*", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS)).contains("custom1");
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS)).contains("custom2");
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestAllowCredentials() {
        final CorsConfig config = forAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertEquals("true", response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestDoNotAllowCredentials() {
        final CorsConfig config = forAnyOrigin().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertFalse(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void anyOriginAndAllowCredentialsShouldEchoRequestOrigin() {
        final CorsConfig config = forAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertEquals("true", response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertEquals("http://localhost:7777", response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals(ORIGIN.toString(), response.headers().get(VARY));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestExposeHeaders() {
        final CorsConfig config = forAnyOrigin().exposeHeaders("one", "two").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS)).contains("one");
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS)).contains("two");
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestShortCircuit() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertEquals(FORBIDDEN, response.status());
        assertEquals("0", response.headers().get(CONTENT_LENGTH));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestNoShortCircuit() {
        final CorsConfig config = forOrigin("http://localhost:8080").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertEquals(OK, response.status());
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void shortCircuitNonCorsRequest() {
        final CorsConfig config = forOrigin("https://localhost").shortCircuit().build();
        final HttpResponse response = simpleRequest(config, null);
        assertEquals(OK, response.status());
        assertNull(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void shortCircuitWithConnectionKeepAliveShouldStayOpen() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        request.headers().set(CONNECTION, KEEP_ALIVE);

        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();
        assertTrue(HttpUtil.isKeepAlive(response));

        assertTrue(channel.isOpen());
        assertEquals(FORBIDDEN, response.status());
        assertTrue(ReferenceCountUtil.release(response));
        assertFalse(channel.finish());
    }

    @Test
    public void shortCircuitWithoutConnectionShouldStayOpen() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");

        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();
        assertTrue(HttpUtil.isKeepAlive(response));

        assertTrue(channel.isOpen());
        assertEquals(FORBIDDEN, response.status());
        assertTrue(ReferenceCountUtil.release(response));
        assertFalse(channel.finish());
    }

    @Test
    public void shortCircuitWithConnectionCloseShouldClose() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        request.headers().set(CONNECTION, CLOSE);

        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();
        assertFalse(HttpUtil.isKeepAlive(response));

        assertFalse(channel.isOpen());
        assertEquals(FORBIDDEN, response.status());
        assertTrue(ReferenceCountUtil.release(response));
        assertFalse(channel.finish());
    }

    @Test
    public void preflightRequestShouldReleaseRequest() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", Arrays.asList("value1", "value2"))
                .build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "content-type, xheader1", null);
        assertFalse(channel.writeInbound(request));
        assertEquals(0, request.refCnt());
        assertTrue(ReferenceCountUtil.release(channel.readOutbound()));
        assertFalse(channel.finish());
    }

    @Test
    public void preflightRequestWithConnectionKeepAliveShouldStayOpen() throws Exception {

        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", KEEP_ALIVE);
        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();
        assertTrue(HttpUtil.isKeepAlive(response));

        assertTrue(channel.isOpen());
        assertEquals(OK, response.status());
        assertTrue(ReferenceCountUtil.release(response));
        assertFalse(channel.finish());
    }

    @Test
    public void preflightRequestWithoutConnectionShouldStayOpen() throws Exception {

        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", null);
        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();
        assertTrue(HttpUtil.isKeepAlive(response));

        assertTrue(channel.isOpen());
        assertEquals(OK, response.status());
        assertTrue(ReferenceCountUtil.release(response));
        assertFalse(channel.finish());
    }

    @Test
    public void preflightRequestWithConnectionCloseShouldClose() throws Exception {

        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", CLOSE);
        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();
        assertFalse(HttpUtil.isKeepAlive(response));

        assertFalse(channel.isOpen());
        assertEquals(OK, response.status());
        assertTrue(ReferenceCountUtil.release(response));
        assertFalse(channel.finish());
    }

    @Test
    public void forbiddenShouldReleaseRequest() {
        final CorsConfig config = forOrigin("https://localhost").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config), new EchoHandler());
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        assertFalse(channel.writeInbound(request));
        assertEquals(0, request.refCnt());
        assertTrue(ReferenceCountUtil.release(channel.readOutbound()));
        assertFalse(channel.finish());
    }

    @Test
    public void differentConfigsPerOrigin() {
        String host1 = "http://host1:80";
        String host2 = "http://host2";
        CorsConfig rule1 = forOrigin(host1).allowedRequestMethods(HttpMethod.GET).build();
        CorsConfig rule2 = forOrigin(host2).allowedRequestMethods(HttpMethod.GET, HttpMethod.POST)
                .allowCredentials().build();

        List<CorsConfig> corsConfigs = Arrays.asList(rule1, rule2);

        final HttpResponse preFlightHost1 = preflightRequest(corsConfigs, host1, "", false);
        assertEquals("GET", preFlightHost1.headers().get(ACCESS_CONTROL_ALLOW_METHODS));
        assertNull(preFlightHost1.headers().getAsString(ACCESS_CONTROL_ALLOW_CREDENTIALS));

        final HttpResponse preFlightHost2 = preflightRequest(corsConfigs, host2, "", false);
        assertValues(preFlightHost2, ACCESS_CONTROL_ALLOW_METHODS.toString(), "GET", "POST");
        assertEquals("true", preFlightHost2.headers().getAsString(ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    public void specificConfigPrecedenceOverGeneric() {
        String host1 = "http://host1";
        String host2 = "http://host2";

        CorsConfig forHost1 = forOrigin(host1).allowedRequestMethods(HttpMethod.GET).maxAge(3600L).build();
        CorsConfig allowAll = forAnyOrigin().allowedRequestMethods(HttpMethod.POST, HttpMethod.GET, HttpMethod.OPTIONS)
                .maxAge(1800).build();

        List<CorsConfig> rules = Arrays.asList(forHost1, allowAll);

        final HttpResponse host1Response = preflightRequest(rules, host1, "", false);
        assertEquals("GET", host1Response.headers().get(ACCESS_CONTROL_ALLOW_METHODS));
        assertEquals("3600", host1Response.headers().getAsString(ACCESS_CONTROL_MAX_AGE));

        final HttpResponse host2Response = preflightRequest(rules, host2, "", false);
        assertValues(host2Response, ACCESS_CONTROL_ALLOW_METHODS.toString(), "POST", "GET", "OPTIONS");
        assertEquals("*", host2Response.headers().getAsString(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("1800", host2Response.headers().getAsString(ACCESS_CONTROL_MAX_AGE));
    }

    @Test
    public void simpleRequestAllowPrivateNetwork() {
        final CorsConfig config = forOrigin("http://localhost:8888").allowPrivateNetwork().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", null);
        request.headers().set(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, "true");
        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();

        assertEquals("true", response.headers().get(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK));
        assertTrue(ReferenceCountUtil.release(response));
    }

    @Test
    public void simpleRequestDoNotAllowPrivateNetwork() {
        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", null);
        request.headers().set(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, "true");
        assertFalse(channel.writeInbound(request));
        final HttpResponse response = channel.readOutbound();

        assertEquals("false", response.headers().get(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK));
        assertTrue(ReferenceCountUtil.release(response));
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
        assertFalse(channel.writeInbound(httpRequest));
        HttpResponse response =  channel.readOutbound();
        assertFalse(channel.finish());
        return response;
    }

    private static HttpResponse preflightRequest(final CorsConfig config,
                                                 final String origin,
                                                 final String requestHeaders) {
        return preflightRequest(Collections.singletonList(config), origin, requestHeaders, config.isShortCircuit());
    }

    private static HttpResponse preflightRequest(final List<CorsConfig> configs,
                                                 final String origin,
                                                 final String requestHeaders,
                                                 final boolean isSHortCircuit) {
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(configs, isSHortCircuit));
        assertFalse(channel.writeInbound(optionsRequest(origin, requestHeaders, null)));
        HttpResponse response = channel.readOutbound();
        assertFalse(channel.finish());
        return response;
    }

    private static FullHttpRequest optionsRequest(final String origin,
                                                  final String requestHeaders,
                                                  final AsciiString connection) {
        final FullHttpRequest httpRequest = createHttpRequest(OPTIONS);
        httpRequest.headers().set(ORIGIN, origin);
        httpRequest.headers().set(ACCESS_CONTROL_REQUEST_METHOD, httpRequest.method().toString());
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
            ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.buffer(0),
                    DefaultHttpHeadersFactory.headersFactory().withCombiningHeaders(true),
                    DefaultHttpHeadersFactory.trailersFactory().withCombiningHeaders(true)));
        }
    }

    private static void assertValues(final HttpResponse response, final String headerName, final String... values) {
        final String header = response.headers().get(of(headerName));
        for (String value : values) {
            assertThat(header).contains(value);
        }
    }
}
