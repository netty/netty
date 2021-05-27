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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.hamcrest.core.IsEqual;
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
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class CorsHandlerTest {

    @Test
    public void nonCorsRequest() {
        final HttpResponse response = simpleRequest(forAnyOrigin().build(), null);
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestWithAnyOrigin() {
        final HttpResponse response = simpleRequest(forAnyOrigin().build(), "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestWithNullOrigin() {
        final HttpResponse response = simpleRequest(forOrigin("http://test.com").allowNullOrigin()
                .allowCredentials()
                .build(), "null");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("null"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(equalTo("true")));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestWithOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(forOrigin(origin).build(), origin);
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestWithOrigins() {
        final String origin1 = "http://localhost:8888";
        final String origin2 = "https://localhost:8888";
        final String[] origins = {origin1, origin2};
        final HttpResponse response1 = simpleRequest(forOrigins(origins).build(), origin1);
        assertThat(response1.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin1));
        assertThat(response1.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response1), is(true));

        final HttpResponse response2 = simpleRequest(forOrigins(origins).build(), origin2);
        assertThat(response2.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(origin2));
        assertThat(response2.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response2), is(true));
    }

    @Test
    public void simpleRequestWithNoMatchingOrigin() {
        final String origin = "http://localhost:8888";
        final HttpResponse response = simpleRequest(
                forOrigins("https://localhost:8888").build(), origin);
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightDeleteRequestWithCustomHeaders() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .allowedRequestMethods(GET, DELETE)
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS), containsString("GET"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS), containsString("DELETE"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightGetRequestWithCustomHeaders() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .allowedRequestMethods(OPTIONS, GET, DELETE)
                .allowedRequestHeaders("content-type", "xheader1")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is("http://localhost:8888"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS), containsString("OPTIONS"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_METHODS), containsString("GET"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), containsString("content-type"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS), containsString("xheader1"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightRequestWithDefaultHeaders() {
        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(CONTENT_LENGTH), is("0"));
        assertThat(response.headers().get(DATE), is(notNullValue()));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightRequestWithCustomHeader() {
        final CorsConfig config = forOrigin("http://localhost:8888")
                .preflightResponseHeader("CustomHeader", "somevalue")
                .build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "content-type, xheader1");
        assertThat(response.headers().get(of("CustomHeader")), equalTo("somevalue"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(response.headers().get(CONTENT_LENGTH), is("0"));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightRequestWithUnauthorizedOrigin() {
        final String origin = "http://host";
        final CorsConfig config = forOrigin("http://localhost").build();
        final HttpResponse response = preflightRequest(config, origin, "xheader1");
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_ORIGIN), is(false));
        assertThat(ReferenceCountUtil.release(response), is(true));
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
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
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
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
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
        assertThat(response.headers().get(of("GenHeader")), equalTo("generatedValue"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightRequestWithNullOrigin() {
        final String origin = "null";
        final CorsConfig config = forOrigin(origin)
                .allowNullOrigin()
                .allowCredentials()
                .build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(equalTo("null")));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(equalTo("true")));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightRequestAllowCredentials() {
        final String origin = "null";
        final CorsConfig config = forOrigin(origin).allowCredentials().build();
        final HttpResponse response = preflightRequest(config, origin, "content-type, xheader1");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(equalTo("true")));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void preflightRequestDoNotAllowCredentials() {
        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final HttpResponse response = preflightRequest(config, "http://localhost:8888", "");
        // the only valid value for Access-Control-Allow-Credentials is true.
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(false));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestCustomHeaders() {
        final CorsConfig config = forAnyOrigin().exposeHeaders("custom1", "custom2").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("custom1"));
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("custom2"));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestAllowCredentials() {
        final CorsConfig config = forAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestDoNotAllowCredentials() {
        final CorsConfig config = forAnyOrigin().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(false));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void anyOriginAndAllowCredentialsShouldEchoRequestOrigin() {
        final CorsConfig config = forAnyOrigin().allowCredentials().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("http://localhost:7777"));
        assertThat(response.headers().get(VARY), equalTo(ORIGIN.toString()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestExposeHeaders() {
        final CorsConfig config = forAnyOrigin().exposeHeaders("one", "two").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("one"));
        assertThat(response.headers().get(ACCESS_CONTROL_EXPOSE_HEADERS), containsString("two"));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestShortCircuit() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.status(), is(FORBIDDEN));
        assertThat(response.headers().get(CONTENT_LENGTH), is("0"));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void simpleRequestNoShortCircuit() {
        final CorsConfig config = forOrigin("http://localhost:8080").build();
        final HttpResponse response = simpleRequest(config, "http://localhost:7777");
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void shortCircuitNonCorsRequest() {
        final CorsConfig config = forOrigin("https://localhost").shortCircuit().build();
        final HttpResponse response = simpleRequest(config, null);
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN), is(nullValue()));
        assertThat(ReferenceCountUtil.release(response), is(true));
    }

    @Test
    public void shortCircuitWithConnectionKeepAliveShouldStayOpen() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        request.headers().set(CONNECTION, KEEP_ALIVE);

        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = channel.readOutbound();
        assertThat(HttpUtil.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.status(), is(FORBIDDEN));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void shortCircuitWithoutConnectionShouldStayOpen() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");

        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = channel.readOutbound();
        assertThat(HttpUtil.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.status(), is(FORBIDDEN));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void shortCircuitWithConnectionCloseShouldClose() {
        final CorsConfig config = forOrigin("http://localhost:8080").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        request.headers().set(CONNECTION, CLOSE);

        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = channel.readOutbound();
        assertThat(HttpUtil.isKeepAlive(response), is(false));

        assertThat(channel.isOpen(), is(false));
        assertThat(response.status(), is(FORBIDDEN));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestShouldReleaseRequest() {
        final CorsConfig config = forOrigin("http://localhost:8888")
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

        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", KEEP_ALIVE);
        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = channel.readOutbound();
        assertThat(HttpUtil.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.status(), is(OK));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestWithoutConnectionShouldStayOpen() throws Exception {

        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", null);
        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = channel.readOutbound();
        assertThat(HttpUtil.isKeepAlive(response), is(true));

        assertThat(channel.isOpen(), is(true));
        assertThat(response.status(), is(OK));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void preflightRequestWithConnectionCloseShouldClose() throws Exception {

        final CorsConfig config = forOrigin("http://localhost:8888").build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config));
        final FullHttpRequest request = optionsRequest("http://localhost:8888", "", CLOSE);
        assertThat(channel.writeInbound(request), is(false));
        final HttpResponse response = channel.readOutbound();
        assertThat(HttpUtil.isKeepAlive(response), is(false));

        assertThat(channel.isOpen(), is(false));
        assertThat(response.status(), is(OK));
        assertThat(ReferenceCountUtil.release(response), is(true));
        assertThat(channel.finish(), is(false));
    }

    @Test
    public void forbiddenShouldReleaseRequest() {
        final CorsConfig config = forOrigin("https://localhost").shortCircuit().build();
        final EmbeddedChannel channel = new EmbeddedChannel(new CorsHandler(config), new EchoHandler());
        final FullHttpRequest request = createHttpRequest(GET);
        request.headers().set(ORIGIN, "http://localhost:8888");
        assertThat(channel.writeInbound(request), is(false));
        assertThat(request.refCnt(), is(0));
        assertThat(ReferenceCountUtil.release(channel.readOutbound()), is(true));
        assertThat(channel.finish(), is(false));
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
        assertThat(preFlightHost1.headers().get(ACCESS_CONTROL_ALLOW_METHODS), is("GET"));
        assertThat(preFlightHost1.headers().getAsString(ACCESS_CONTROL_ALLOW_CREDENTIALS), is(nullValue()));

        final HttpResponse preFlightHost2 = preflightRequest(corsConfigs, host2, "", false);
        assertValues(preFlightHost2, ACCESS_CONTROL_ALLOW_METHODS.toString(), "GET", "POST");
        assertThat(preFlightHost2.headers().getAsString(ACCESS_CONTROL_ALLOW_CREDENTIALS), IsEqual.equalTo("true"));
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
        assertThat(host1Response.headers().get(ACCESS_CONTROL_ALLOW_METHODS), is("GET"));
        assertThat(host1Response.headers().getAsString(ACCESS_CONTROL_MAX_AGE), equalTo("3600"));

        final HttpResponse host2Response = preflightRequest(rules, host2, "", false);
        assertValues(host2Response, ACCESS_CONTROL_ALLOW_METHODS.toString(), "POST", "GET", "OPTIONS");
        assertThat(host2Response.headers().getAsString(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(host2Response.headers().getAsString(ACCESS_CONTROL_MAX_AGE), equalTo("1800"));
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
        HttpResponse response =  channel.readOutbound();
        assertThat(channel.finish(), is(false));
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
        assertThat(channel.writeInbound(optionsRequest(origin, requestHeaders, null)), is(false));
        HttpResponse response = channel.readOutbound();
        assertThat(channel.finish(), is(false));
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
            ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, OK, true, true));
        }
    }

    private static void assertValues(final HttpResponse response, final String headerName, final String... values) {
        final String header = response.headers().get(of(headerName));
        for (String value : values) {
            assertThat(header, containsString(value));
        }
    }
}
