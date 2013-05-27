/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.sockjs.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.sockjs.SockJsChannelInitializer;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.SockJsService;
import io.netty.handler.codec.sockjs.SockJsServiceFactory;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.transport.TransportType;
import io.netty.handler.codec.sockjs.util.JsonConverter;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.websocketx.WebSocketVersion.V00;
import static io.netty.handler.codec.http.websocketx.WebSocketVersion.V07;
import static io.netty.handler.codec.http.websocketx.WebSocketVersion.V08;
import static io.netty.handler.codec.http.websocketx.WebSocketVersion.V13;
import static io.netty.handler.codec.sockjs.transport.EventSourceTransport.CONTENT_TYPE_EVENT_STREAM;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_HTML;
import static io.netty.handler.codec.sockjs.transport.TransportType.EVENTSOURCE;
import static io.netty.handler.codec.sockjs.transport.TransportType.HTMLFILE;
import static io.netty.handler.codec.sockjs.transport.TransportType.JSONP;
import static io.netty.handler.codec.sockjs.transport.TransportType.JSONP_SEND;
import static io.netty.handler.codec.sockjs.transport.TransportType.XHR;
import static io.netty.handler.codec.sockjs.transport.TransportType.XHR_SEND;
import static io.netty.handler.codec.sockjs.transport.TransportType.WEBSOCKET;
import static io.netty.handler.codec.sockjs.transport.TransportType.XHR_STREAMING;
import static io.netty.handler.codec.sockjs.util.HttpRequestBuilder.getRequest;
import static io.netty.handler.codec.sockjs.util.HttpRequestBuilder.optionsRequest;
import static io.netty.handler.codec.sockjs.util.HttpRequestBuilder.postRequest;
import static io.netty.handler.codec.sockjs.util.HttpRequestBuilder.wsUpgradeRequest;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.*;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_FORM;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_JAVASCRIPT;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_PLAIN;
import static io.netty.handler.codec.sockjs.util.SockJsTestServices.closeServiceFactory;
import static io.netty.handler.codec.sockjs.util.SockJsTestServices.echoServiceFactory;
import static io.netty.handler.codec.sockjs.util.SockJsTestServices.singletonEchoServiceFactory;
import static io.netty.handler.codec.sockjs.util.TestChannels.channelForMockService;
import static io.netty.handler.codec.sockjs.util.TestChannels.channelForService;
import static io.netty.handler.codec.sockjs.util.TestChannels.corsConfig;
import static io.netty.handler.codec.sockjs.util.TestChannels.corsConfigBuilder;
import static io.netty.handler.codec.sockjs.util.TestChannels.factoryForService;
import static io.netty.handler.codec.sockjs.util.TestChannels.jsonpChannelForService;
import static io.netty.handler.codec.sockjs.util.TestChannels.webSocketChannel;
import static io.netty.handler.codec.sockjs.util.TestChannels.websocketChannelForService;
import static io.netty.util.CharsetUtil.US_ASCII;
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.util.ReferenceCountUtil.release;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.hamcrest.MatcherAssert.assertThat;

public class SockJsProtocolTest {

    private static final String SERVER_ID = "abc";

    private String sessionId;

    @Before
    public void generateSessionId() {
        sessionId = UUID.randomUUID().toString();
    }

    /*
     * Equivalent to BaseUrlGreeting.test_notFound in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void baseUrlGreetingTestNotFound() throws Exception {
        sendInvalidUrlRequest("/echo", "/a");
        sendInvalidUrlRequest("/echo", "/a.html");
        sendInvalidUrlRequest("/echo", "//");
        sendInvalidUrlRequest("/echo", "///");
        sendInvalidUrlRequest("/echo", "//a");
        sendInvalidUrlRequest("/echo", "/a/a/");
        sendInvalidUrlRequest("/echo", "/a/");
    }

    /*
     * Equivalent to IframePage.test_simpleUrl in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void iframePageSimpleUrl() throws Exception {
        sendValidIframeRequest("/echo", "/iframe.html");
    }

    /*
     * Equivalent to IframePage.test_versionedUrl in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void iframePageTestVersionedUrl() {
        sendValidIframeRequest("/echo", "/iframe-a.html");
        sendValidIframeRequest("/echo", "/iframe-.html");
        sendValidIframeRequest("/echo", "/iframe-0.1.2.html");
        sendValidIframeRequest("/echo", "/iframe-0.1.2.abc-dirty.2144.html");
    }

    /*
     * Equivalent to IframePage.test_queriedUrl in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void iframePageTestQueriedUrl() {
        sendValidIframeRequest("/echo", "/iframe-a.html?t=1234");
        sendValidIframeRequest("/echo", "/iframe-0.1.2.html?t=123414");
        sendValidIframeRequest("/echo", "/iframe-0.1.2abc-dirty.2144.html?t=qweqweq123");
    }

    /*
     * Equivalent to IframePage.test_invalidUrl in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void iframePageTestInvalidUrl() {
        sendInvalidUrlRequest("/echo", "/iframe.htm");
        sendInvalidUrlRequest("/echo", "/iframe");
        sendInvalidUrlRequest("/echo", "/IFRAME.HTML");
        sendInvalidUrlRequest("/echo", "/IFRAME");
        sendInvalidUrlRequest("/echo", "/iframe.HTML");
        sendInvalidUrlRequest("/echo", "/iframe.xml");
        sendInvalidUrlRequest("/echo", "/iframe-/.html");
    }

    /*
     * Equivalent to IframePage.test_cacheability in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void iframeCachability() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final String url = config.prefix() + "/iframe.html";
        final EmbeddedChannel ch1 = channelForMockService(config);
        final EmbeddedChannel ch2 = channelForMockService(config);

        ch1.writeInbound(getRequest(url).defaultCors().build());
        final FullHttpResponse response = ch1.readOutbound();
        final String etag = response.headers().getAndConvert(ETAG);
        release(response);

        ch2.writeInbound(getRequest(url).defaultCors().build());
        assertEntityTag(readFullHttpResponse(ch2), etag);

        final EmbeddedChannel ch3 = channelForMockService(config);
        ch3.writeInbound(getRequest(url).defaultCors().header(IF_NONE_MATCH, etag).build());
        assertNotModified(readHttpResponse(ch3));
        assertChannelFinished(ch1, ch2, ch3);
    }

    /*
     * Equivalent to InfoTest.test_basic in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void infoTestBasic() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").cookiesNeeded().build();

        final FullHttpResponse response = infoForMockService(config);
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertContentType(response, "application/json; charset=UTF-8");
        assertNoSetCookie(response);
        assertNoCache(response);
        assertOriginLocalhost(response);
        final JsonNode json = contentAsJson(response);
        assertThat(json.get("websocket").asBoolean(), is(true));
        assertThat(json.get("cookie_needed").asBoolean(), is(true));
        assertThat(json.get("origins").get(0).asText(), is("*:*"));
        assertThat(json.get("entropy").asLong(), is(notNullValue()));
        release(response);
    }

    /*
     * Equivalent to InfoTest.test_entropy in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void infoTestEntropy() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        assertThat(getEntropy(infoForMockService(config)) != getEntropy(infoForMockService(config)), is(true));
    }

    /*
     * Equivalent to InfoTest.test_options in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void infoTestOptions() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = channelForMockService(config, corsConfigBuilder()
                .allowedRequestHeaders(CONTENT_TYPE.toString())
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
                .build());

        ch.writeInbound(optionsRequest(config.prefix()).defaultCors().build());
        final HttpResponse response = ch.readOutbound();
        // sockjs-protocol expects a response in th 2xx range. Currently are CORS handler will return a 200.
        // Perhaps this should be configurable, or perhaps we should change it to 204 No Content?
        // http://www.w3.org/TR/cors/#cross-origin-request-with-preflight-0
        assertOkResponse(response);
        assertCorsPreflightHeaders(response);
        assertOriginLocalhost(response);
        assertChannelFinished(ch);
    }

    @Test
    public void corsConfigOverride() throws Exception {
        final String origin = "http://example.com";
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = channelForMockService(config, corsConfigBuilder(origin).build());

        ch.writeInbound(optionsRequest(config.prefix())
                .origin(origin)
                .accessControlRequestMethod()
                .accessControlRequestHeader(CONTENT_TYPE)
                .build());
        assertOrigin((HttpResponse) ch.readOutbound(), origin);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to InfoTest.test_options_null_origin in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void infoTestOptionsNullOrigin() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final EmbeddedChannel ch = channelForMockService(config, corsConfigBuilder()
                .allowNullOrigin()
                .allowedRequestHeaders(CONTENT_TYPE.toString())
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
                .build());

        ch.writeInbound(optionsRequest(config.prefix())
                .origin("null")
                .accessControlRequestMethod()
                .accessControlRequestHeader(CONTENT_TYPE)
                .build());
        final HttpResponse response = ch.readOutbound();
        // sockjs-protocol expects a response in th 2xx range. Currently are CORS handler will return a 200.
        // Perhaps this should be configurable, or perhaps we should change it to 204 No Content?
        // http://www.w3.org/TR/cors/#cross-origin-request-with-preflight-0
        assertOkResponse(response);
        assertCorsPreflightHeaders(response);
        assertOrigin(response, "*");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to InfoTest.test_disabled_websocket in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void infoTestDisabledWebsocket() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").disableWebSocket().build();
        final EmbeddedChannel ch = channelForMockService(config);
        ch.writeInbound(getRequest(config.prefix() + "/info").build());

        final FullHttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertThat(contentAsJson(response).get("websocket").asBoolean(), is(false));
        release(response);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to SessionURLs.test_anyValue in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void sessionUrlsTestAnyValue() throws Exception {
        sendSuccessfulRequest("/a/a");
        sendSuccessfulRequest("/_/_");
        sendSuccessfulRequest("/1/1");
        sendSuccessfulRequest("/abcdefgh_i-j%20/abcdefg_i-j%20");
    }

    /*
     * Equivalent to SessionURLs.test_invalidPaths in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void sessionUrlsTestInvalidPaths() throws Exception {
        sendInvalidUrlRequest("echo", "//");
        sendInvalidUrlRequest("echo", "/a./a");
        sendInvalidUrlRequest("echo", "/a/a.");
        sendInvalidUrlRequest("echo", "/./.");
        sendInvalidUrlRequest("echo", "/");
        sendInvalidUrlRequest("echo", "///");
    }

    /*
     * Equivalent to SessionURLs.test_ignoringServerId in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void sessionUrlsTestIgnoringServerId() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"a\"]"));
        assertMessageFrameContent(sendXhrRequest(serviceFactory), "a");
    }

    /*
     * Equivalent to Protocol.test_simpleSession in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void protocolTestSimpleSession() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"a\"]"));
        assertMessageFrameContent(sendXhrRequest(serviceFactory), "a");
        assertNotFound(xhrSendRequest(serviceFactory, "/echo/111/badsession", "[\"a\"]"));
    }

    /*
     * Equivalent to Protocol.test_closeSession in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void protocolTestCloseSession() throws Exception {
        final SockJsServiceFactory serviceFactory = closeServiceFactory();

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertGoAwayResponse(sendXhrRequest(serviceFactory));
        assertGoAwayResponse(sendXhrRequest(serviceFactory));
    }

    /*
     * Equivalent to WebSocketHttpErrors.test_httpMethod in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHttpErrorsTestHttpMethod() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, WEBSOCKET)).defaultCors().build());
        assertCanOnlyUpgradeToWebSocket(ch);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebSocketHttpErrors.test_invalidConnectionHeader in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHttpErrorsTestInvalidConnectionHeader() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V13)
                .header(UPGRADE, Values.WEBSOCKET)
                .header(CONNECTION, Values.CLOSE)
                .build());
        assertConnectionMustBeUpgrade(ch);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebsocketHttpErrors.test_invalidMethod in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHttpErrorsTestInvalidMethod() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V13)
                .method(POST)
                .build());
        assertMethodNotAllowed((HttpResponse) ch.readOutbound());
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebsocketHixie76.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHixie76TestTransport() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        // Hybi-00 (hy_pertext bi_directional) is just a copy of Hixie-76
        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00).build());
        assertWebSocketUpgradeResponse(ch, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch);
        release(ch.readOutbound());
        ch.writeInbound(textWebSocketFrame("\"Fletch\""));
        assertWebSocketTextFrame(ch, "a[\"Fletch\"]");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebsocketHixie76.test_close in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHixie76TestClose() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/close").build();
        final SockJsServiceFactory serviceFactory = closeServiceFactory(config);
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        // Hybi-00 (hy_pertext bi_directional) is just a copy of Hixie-76
        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00).build());
        assertWebSocketUpgradeResponse(ch, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch);
        assertWebSocketCloseFrame(ch);
        assertChannelFinished(ch);
        assertWebSocketTestClose(V13);
    }

    /*
     * Equivalent to WebsocketHixie76.test_empty_frame in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHixie76TestEmptyFrame() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        // Hybi-00 (hy_pertext bi_directional) is just a copy of Hixie-76
        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00).build());
        assertWebSocketUpgradeResponse(ch, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch);

        ch.writeInbound(textWebSocketFrame(""));
        ch.writeInbound(textWebSocketFrame("\"a\""));
        assertWebSocketTextFrame(ch, "a[\"a\"]");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebsocketHixie76.test_reuseSessionId in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHixie76TestReuseSessionId() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch1 = websocketChannelForService(serviceFactory);
        final EmbeddedChannel ch2 = websocketChannelForService(serviceFactory);

        ch1.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00).build());
        assertWebSocketUpgradeResponse(ch1, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch1);

        ch2.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00).build());
        assertWebSocketUpgradeResponse(ch2, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch2);

        ch1.writeInbound(textWebSocketFrame("\"ch1 message\""));
        assertWebSocketTextFrame(ch1, "a[\"ch1 message\"]");

        ch2.writeInbound(textWebSocketFrame("\"ch2 message\""));
        assertWebSocketTextFrame(ch2, "a[\"ch2 message\"]");

        final EmbeddedChannel ch3 = websocketChannelForService(serviceFactory);
        ch3.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00).build());
        assertWebSocketUpgradeResponse(ch3, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch3);
        ch3.writeInbound(textWebSocketFrame("\"a\""));
        assertWebSocketTextFrame(ch3, "a[\"a\"]");

        ch1.close();
        ch2.close();
        ch3.close();
        assertChannelFinished(ch1, ch2, ch3);
    }

    /*
     * Equivalent to WebsocketHixie76.test_haproxy in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHixie76TestHAProxy() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V00)
                // A HA proxy request does not have the key as it content.
                .clearContent()
                .build());
        assertWebSocketUpgradeResponse(ch);

        ch.writeInbound(Unpooled.copiedBuffer("^n:ds[4U", US_ASCII));
        assertByteContent(readByteBuf(ch), "8jKS'y:G*Co,Wxa-");
        release(ch.readOutbound());
        assertByteContent(readByteBuf(ch), "o");

        ch.writeInbound(textWebSocketFrame("\"a\""));
        release(ch.readOutbound());
        release(ch.readOutbound());

        assertByteContent(readByteBuf(ch), "a[\"a\"]");
        release(ch.readOutbound());
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebsocketHixie76.test_broken_json in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHixie76TestBrokenJSON() throws Exception {
        final String serviceName = "/close";
        final String sessionUrl = serviceName + "/222/" + UUID.randomUUID();
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).build();
        final EmbeddedChannel ch = websocketChannelForService(echoServiceFactory(config));

        ch.writeInbound(wsUpgradeRequest(sessionUrl + "/websocket", V00).build());
        assertWebSocketUpgradeResponse(ch, "8jKS'y:G*Co,Wxa-");
        assertWebSocketOpenFrame(ch);
        assertWebSocketTestBrokenJSON(V13);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to WebsocketHybi10.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi10TestTransport() throws Exception {
        webSocketTestTransport(V08);
    }

    /*
     * Equivalent to WebsocketHybi10.test_close in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi10TestClose() throws Exception {
        assertWebSocketTestClose(V08);
    }

    /*
     * Equivalent to WebsocketHybi10.test_headersSantity in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi10TestHeadersSanity() throws Exception {
        sendValidWebSocketRequest(V07);
        sendValidWebSocketRequest(V08);
        sendValidWebSocketRequest(V13);
    }

    /*
     * Equivalent to WebsocketHybi10.test_broken_json in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi10TestBrokenJSON() throws Exception {
        assertWebSocketTestBrokenJSON(V08);
    }

    /*
     * Equivalent to WebsocketHybi10.test_transport, but for Hybi17, in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi17TestTransport() throws Exception {
        webSocketTestTransport(V13);
    }

    /*
     * Equivalent to WebsocketHybi10.test_close, but for Hybi17, in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi17TestClose() throws Exception {
        assertWebSocketTestClose(V13);
    }

    /*
     * Equivalent to WebsocketHybi10.test_broken_json, but for Hybi17, in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi17TestBrokenJSON() throws Exception {
        assertWebSocketTestBrokenJSON(V13);
    }

    /*
     * Equivalent to WebsocketHybi10.test_firefox_602_connection_header in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void webSocketHybi10Firefox602ConnectionHeader() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = webSocketChannel(serviceFactory, corsConfig());

        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), V08)
                .header(CONNECTION, "keep-alive, Upgrade")
                .build());
        assertWebSocketUpgradeResponse(ch);
        release(ch.readOutbound());
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to XhrPolling.test_options in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrPollingTestOptions() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final CorsConfig corsConfig = corsConfigBuilder()
                .allowedRequestHeaders(CONTENT_TYPE.toString())
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
                .build();

        final FullHttpRequest xhrRequest = optionsRequest(sessionUrlFor(serviceFactory, XHR)).defaultCors().build();
        assertCorsPreflightHeaders(sendXhrRequest(xhrRequest, serviceFactory, corsConfig));
        final FullHttpRequest xhrSendRequest = optionsRequest(sessionUrlFor(serviceFactory, XHR_SEND))
                .defaultCors()
                .build();
        assertCorsPreflightHeaders(sendXhrRequest(xhrSendRequest, serviceFactory, corsConfig));
    }

    /*
     * Equivalent to XhrPolling.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrPollingTestTransport() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        final FullHttpResponse response = sendXhrRequest(serviceFactory);
        assertOpenFrameResponse(response);
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), equalTo(CONTENT_TYPE_JAVASCRIPT));
        assertOriginLocalhost(response);
        assertNoCache(response);

        final FullHttpResponse xhrSendResponse = sendXhrSendRequest(serviceFactory, "[\"x\"]");
        assertThat(xhrSendResponse.headers().getAndConvert(CONTENT_TYPE), equalTo(CONTENT_TYPE_PLAIN));
        assertOriginLocalhost(response);
        assertNoCache(xhrSendResponse);
        assertNoContent(xhrSendResponse);
        assertMessageFrameContent(sendXhrRequest(serviceFactory), "x");
    }

    @Test
    public void xhrPollingSessionReuse() throws Exception {
        final SockJsServiceFactory serviceFactory = singletonEchoServiceFactory();

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"x\"]"));
        assertMessageFrameContent(sendXhrRequest(serviceFactory), "x");
        sendXhrRequest(serviceFactory);
        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"a\"]"));
        assertMessageFrameContent(sendXhrRequest(serviceFactory), "a");
    }

    /*
     * Equivalent to XhrPolling.test_invalid_session in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrPollingTestInvalidSession() throws Exception {
        final FullHttpResponse xhrSendResponse = sendXhrSendRequest(echoServiceFactory(), "[\"x\"]");
        assertThat(xhrSendResponse.status(), is(HttpResponseStatus.NOT_FOUND));
        release(xhrSendResponse);
    }

    /*
     * Equivalent to XhrPolling.test_invalid_json sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrPollingTestInvalidJson() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertInternalServerError(sendXhrSendRequest(serviceFactory, "[\"x\""), "Broken JSON encoding.");
        assertInternalServerError(sendXhrSendRequest(serviceFactory, ""), "Payload expected.");
        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"a\"]"));
        assertMessageFrameContent(sendXhrRequest(serviceFactory), "a");
    }

    /*
     * Equivalent to XhrPolling.test_content_types sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrPollingTestContentTypes() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"a\"]", "text/plain"));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"b\"]", "application/json"));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"c\"]", "application/json;charset=utf-8"));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"d\"]", "application/xml"));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"e\"]", "text/xml"));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"f\"]", "text/xml; charset=utf-8"));
        assertNoContent(sendXhrSendRequestWithContentType(serviceFactory, "[\"g\"]", ""));
        assertContent(sendXhrRequest(serviceFactory), "a[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]\n");
    }

    /*
     * Equivalent to XhrPolling.test_request_headers_cors sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrPollingTestRequestHeadersCors() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        final CorsConfig corsConfig = SockJsChannelInitializer.defaultCorsOptions()
                .allowNullOrigin()
                .allowedRequestHeaders("a, b, c")
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
                .build();
        final FullHttpRequest okRequest = postRequest(sessionUrlFor(serviceFactory, XHR))
                .defaultCors()
                .accessControlRequestHeader("a, b, c")
                .build();
        final HttpResponse response = sendXhrRequest(okRequest, serviceFactory, corsConfig);
        assertOkResponse(response);
        assertOriginLocalhost(response);
        assertAccessRequestAllowHeaders(response, "a, b, c");

        final CorsConfig noHeaders = SockJsChannelInitializer.defaultCorsOptions()
                .allowNullOrigin()
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
                .build();
        final FullHttpRequest emptyHeaderRequest = postRequest(newSessionUrlFor(serviceFactory, XHR))
                .defaultCors()
                .accessControlRequestHeader("")
                .build();
        final HttpResponse emptyHeaderResponse = sendXhrRequest(emptyHeaderRequest, serviceFactory, noHeaders);
        assertOkResponse(emptyHeaderResponse);
        assertOriginLocalhost(response);
        assertThat(emptyHeaderResponse.headers().getAndConvert(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));

        final FullHttpRequest noHeaderRequest = postRequest(newSessionUrlFor(serviceFactory, XHR))
                .defaultCors()
                .build();
        final HttpResponse noHeaderResponse = sendXhrRequest(noHeaderRequest, serviceFactory, noHeaders);
        assertOkResponse(noHeaderResponse);
        assertOriginLocalhost(response);
        assertThat(noHeaderResponse.headers().getAndConvert(ACCESS_CONTROL_ALLOW_HEADERS), is(nullValue()));
    }

    /*
     * Equivalent to XhrStreaming.test_options in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrStreamingTestOptions() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, XHR_STREAMING)).defaultCors().build());
        final HttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertContentType(response, CONTENT_TYPE_JAVASCRIPT);
        assertOriginLocalhost(response);
        assertNoCache(response);

        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));
        assertNoContent(xhrSendRequest(serviceFactory, sessionUrlFor(serviceFactory), "[\"x\"]"));
        assertContent(readHttpContent(ch), "a[\"x\"]\n");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to XhrStreaming.test_response_limit in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void xhrStreamingTestResponseLimit() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").maxStreamingBytesSize(4096).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, XHR_STREAMING)).defaultCors().build());
        final HttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertContentType(response, CONTENT_TYPE_JAVASCRIPT);
        assertOriginLocalhost(response);
        assertNoCache(response);

        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));

        final String msg = generateMessage(128);
        for (int i = 0; i < 31; i++) {
            assertNoContent(sendXhrSendRequest(serviceFactory, "[\"" + msg + "\"]"));
            assertContent(readHttpContent(ch), "a[\"" + msg + "\"]\n");
        }
        assertLastContent(readLastHttpContent(ch));
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to EventSource.test_response_limit in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void eventSourceTestResponseLimit() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").maxStreamingBytesSize(4096).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);
        final String msg = generateMessage(4096);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, EVENTSOURCE)).defaultCors().build());
        final HttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertContentType(response, CONTENT_TYPE_EVENT_STREAM);
        assertContent(readHttpContent(ch), "\r\n");
        assertContent(readHttpContent(ch), "data: o\r\n\r\n");

        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"" + msg + "\"]"));
        assertContent(readHttpContent(ch), "data: a[\"" + msg + "\"]\r\n\r\n");
        assertLastContent(readLastHttpContent(ch));
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to EventSource.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void eventSourceTestTransport() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").maxStreamingBytesSize(4096).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);
        final String msg = "[\"  \\u0000\\n\\r \"]";

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, EVENTSOURCE)).defaultCors().build());
        final HttpResponse response =  ch.readOutbound();
        assertOkResponse(response);
        assertContentType(response, CONTENT_TYPE_EVENT_STREAM);
        assertContent(readHttpContent(ch), "\r\n");
        assertContent(readHttpContent(ch), "data: o\r\n\r\n");

        assertNoContent(sendXhrSendRequest(serviceFactory, msg));
        assertContent(readHttpContent(ch), "data: a" + msg + "\r\n\r\n");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to HtmlFile.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void htmlFileTestTransport() throws Exception {
        final String serviceName = "/echo";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).maxStreamingBytesSize(4096).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory,  HTMLFILE) + "?c=callback").defaultCors().build());
        final HttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertContentType(response, CONTENT_TYPE_HTML);

        final HttpContent headerChunk = ch.readOutbound();
        assertThat(headerChunk.content().readableBytes(), is(greaterThan(1024)));
        assertThat(headerChunk.content().toString(UTF_8), containsString("var c = parent.callback"));
        release(headerChunk);
        assertContent(readHttpContent(ch), "<script>\np(\"o\");\n</script>\r\n");

        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"x\"]"));
        assertContent(readHttpContent(ch), "<script>\np(\"a[\\\"x\\\"]\");\n</script>\r\n");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to HtmlFile.test_no_callback in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void htmlFileTestNoCallback() throws Exception {
        final String serviceName = "/echo";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).maxStreamingBytesSize(4096).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory,  HTMLFILE) + "?c=").defaultCors().build());
        assertInternalServerError(readFullHttpResponse(ch), "\"callback\" parameter required");
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to HtmlFile.test_response_limit in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void htmlFileTestResponseLimit() throws Exception {
        final String serviceName = "/echo";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).maxStreamingBytesSize(4096).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);
        final String msg = generateMessage(4096);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory,  HTMLFILE) + "?c=callback").defaultCors().build());
        assertOkResponse((HttpResponse) ch.readOutbound());
        assertHtmlfile(readHttpContent(ch));
        assertHtmlOpenScript(readHttpContent(ch));

        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"" + msg + "\"]"));
        assertContent(readHttpContent(ch), "<script>\np(\"a[\\\"" + msg + "\\\"]\");\n</script>\r\n");
        assertLastContent(readLastHttpContent(ch));
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to JsonPolling.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonpPollingTestTransport() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        final FullHttpResponse response = sendJsonpRequest(serviceFactory, "?c=%63allback");
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.content().toString(UTF_8), equalTo("callback(\"o\");\r\n"));
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), equalTo(CONTENT_TYPE_JAVASCRIPT));
        assertNoCache(response);
        release(response);

        final String data = "d=%5B%22x%22%5D";
        final FullHttpResponse sendResponse = sendJsonpSend(serviceFactory, data);
        assertOkResponse(sendResponse);
        assertThat(sendResponse.content().toString(UTF_8), equalTo("ok"));
        assertContentType(sendResponse, CONTENT_TYPE_PLAIN);
        assertNoCache(response);
        release(sendResponse);

        final FullHttpResponse pollResponse = sendJsonpRequest(serviceFactory, "?c=callback");
        assertOkResponse(pollResponse);
        assertContentType(pollResponse, CONTENT_TYPE_JAVASCRIPT);
        assertNoCache(pollResponse);
        assertContent(pollResponse, "callback(\"a[\\\"x\\\"]\");\r\n");
    }

    /*
     * Equivalent to JsonPolling.test_no_callback in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonpPollingTestNoCallback() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        assertInternalServerError(sendJsonpRequest(serviceFactory, ""), "\"callback\" parameter required");
    }

    /*
     * Equivalent to JsonPolling.test_invalid_json in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonpPollingTestInvalidJson() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        final FullHttpResponse response = sendJsonpRequest(serviceFactory, "?c=x");
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.content().toString(UTF_8), equalTo("x(\"o\");\r\n"));
        release(response);

        assertBrokenJSONEncoding(sendJsonpSend(serviceFactory, "d=%5B%22x"));
        assertPayloadExpected(sendJsonpSend(serviceFactory, ""));
        assertPayloadExpected(sendJsonpSend(serviceFactory, "d="));
        assertPayloadExpected(sendJsonpSend(serviceFactory, "p=p"));

        final FullHttpResponse sendResponse = sendJsonpSend(serviceFactory, "d=%5B%22b%22%5D");
        assertOkResponse(sendResponse);
        release(sendResponse);

        final FullHttpResponse pollResponse = sendJsonpRequest(serviceFactory, "?c=x");
        assertOkResponse(pollResponse);
        assertContent(pollResponse, "x(\"a[\\\"b\\\"]\");\r\n");
    }

    /*
     * Equivalent to JsonPolling.test_content_types in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonpPollingTestContentTypes() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();

        assertContent(sendJsonpRequest(serviceFactory, "?c=x"), "x(\"o\");\r\n");

        final FullHttpResponse sendResponse = sendJsonpSend(serviceFactory, "d=%5B%22abc%22%5D");
        assertOkResponse(sendResponse);
        release(sendResponse);

        final FullHttpRequest plainRequest = postRequest(sessionUrlFor(serviceFactory, JSONP_SEND))
                .defaultCors()
                .content("[\"%61bc\"]")
                .contentType(CONTENT_TYPE_PLAIN)
                .build();
        final FullHttpResponse plainResponse = jsonpSend(plainRequest, serviceFactory);
        assertOkResponse(plainResponse);
        release(plainResponse);

        final FullHttpResponse pollResponse = sendJsonpRequest(serviceFactory, "?c=x");
        assertOkResponse(pollResponse);
        assertContent(pollResponse, "x(\"a[\\\"abc\\\",\\\"%61bc\\\"]\");\r\n");
    }

    /*
     * Equivalent to JsonPolling.test_close in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonpPollingTestClose() throws Exception {
        final SockJsServiceFactory serviceFactory = closeServiceFactory();

        assertContent(sendJsonpRequest(serviceFactory, "?c=x"), "x(\"o\");\r\n");
        assertContent(sendJsonpRequest(serviceFactory, "?c=x"), "x(\"c[3000,\\\"Go away!\\\"]\");\r\n");
        assertContent(sendJsonpRequest(serviceFactory, "?c=x"), "x(\"c[3000,\\\"Go away!\\\"]\");\r\n");
    }

    /*
     * Equivalent to JsessionIdCookie.test_basic in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsessionIdCookieTestBasic() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/cookie_needed_echo").cookiesNeeded().build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);

        final FullHttpResponse response = infoRequest(config.prefix(), serviceFactory);
        assertOkResponse(response);
        assertNoSetCookie(response);
        assertThat(infoAsJson(response).get("cookie_needed").asBoolean(), is(true));
        release(response);
    }

    /*
     * Equivalent to JsessionIdCookie.test_xhr in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsessionIdCookieTestXhr() throws Exception {
        sendValidCookieNeededEchoRequest(TransportType.XHR.path());
        final String serviceName = "/cookie_needed_echo";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).cookiesNeeded().build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, XHR))
                .defaultCors()
                .cookie(ClientCookieEncoder.encode("JSESSIONID", "abcdef"))
                .build());

        final FullHttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertSetCookie("abcdef", response);
        release(response);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to JsessionIdCookie.test_xhr_streaming in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsessionIdCookieTestXhrStreaming() throws Exception {
        sendValidCookieNeededEchoRequest(XHR_STREAMING.path());
    }

    /*
     * Equivalent to JsessionIdCookie.test_eventsource in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsessionIdCookieTestEventSource() throws Exception {
        sendValidCookieNeededEchoRequest(EVENTSOURCE.path());
    }

    /*
     * Equivalent to JsessionIdCookie.test_htmlfile in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsessionIdCookieTestHtmlFile() throws Exception {
        sendValidCookieNeededEchoRequest(HTMLFILE.path() + "?c=callback");
    }

    /*
     * Equivalent to JsessionIdCookie.test_jsonp in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsessionIdCookieTestJsonp() throws Exception {
        sendValidCookieNeededEchoRequest(JSONP.path() + "?c=callback");
    }

    /*
     * Equivalent to RawWebsocket.test_transport in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void rawWebsocketTestTransport() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(wsUpgradeRequest(serviceFactory.config().prefix() + WEBSOCKET.path(), V13).build());
        // Discard Switching Protocols emptyResponse
        release(ch.readOutbound());

        ch.writeInbound(textWebSocketFrame("Hello world!\uffff"));
        assertWebSocketTextFrame(ch, "Hello world!\uffff");
        assertChannelFinished(ch);
    }

    @Test
    public void webSocketCloseSession() throws Exception {
        final String serviceName = "/closesession";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).build();
        final SockJsService sockJsService = mock(SockJsService.class);
        final SockJsServiceFactory serviceFactory = factoryForService(sockJsService, config);
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(webSocketUpgradeRequest(serviceFactory, V13));
        assertWebSocketUpgradeResponse(ch);
        assertWebSocketOpenFrame(ch);

        ch.writeInbound(new CloseWebSocketFrame(1000, "Normal close"));
        final CloseWebSocketFrame closeFrame = ch.readOutbound();
        assertThat(closeFrame.statusCode(), is(1000));
        assertThat(closeFrame.reasonText(), equalTo("Normal close"));
        verify(sockJsService).onOpen(any(SockJsSessionContext.class));
        verify(sockJsService).onClose();
        release(closeFrame);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to JSONEncoding.test_xhr_server_encodes in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonEncodingTestXhrServerEncodes() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final String content = escapeCharacters(serverKillerStringEsc().toCharArray());

        assertOpenFrameResponse(sendXhrRequest(serviceFactory));
        assertNoContent(sendXhrSendRequest(serviceFactory, "[\"" + content + "\"]"));
        assertContent(sendXhrRequest(serviceFactory), "a[\"" + content + "\"]\n");
    }

    /*
     * Equivalent to JSONEncoding.test_xhr_server_decodes in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void jsonEncodingTestXhrServerDecodes() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final String content = "[\"" + generateUnicodeValues(0x0000, 0xFFFF) + "\"]";

        final FullHttpResponse response = sendXhrRequest(serviceFactory);
        assertOpenFrameResponse(response);
        assertNoContent(sendXhrSendRequest(serviceFactory, content));

        final FullHttpResponse pollResponse = sendXhrRequest(serviceFactory);
        assertOkResponse(pollResponse);

        // Let the content go through the MessageFrame to match what the buildResponse will go through.
        final MessageFrame messageFrame = new MessageFrame(JsonConverter.decode(content)[0]);
        String expectedContent = JsonConverter.encode(messageFrame.content().toString(UTF_8) + '\n');
        String responseContent = JsonConverter.encode(pollResponse.content().toString(UTF_8));
        assertThat(responseContent, equalTo(expectedContent));
        release(pollResponse);
        release(messageFrame);
    }

    /*
     * Equivalent to HandlingClose.test_close_frame in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void handlingCloseTestCloseFrame() throws Exception {
        final SockJsServiceFactory serviceFactory = closeServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);
        final String url = sessionUrlFor(serviceFactory, XHR_STREAMING);

        ch.writeInbound(getRequest(url).build());
        assertOkResponse(readHttpResponse(ch));
        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));
        assertCloseFrameContent(readHttpContent(ch));
        assertLastContent(readLastHttpContent(ch));

        final EmbeddedChannel ch2 = channelForService(serviceFactory);
        ch2.writeInbound(getRequest(url).build());
        assertOkResponse(readHttpResponse(ch2));
        assertPrelude(readHttpContent(ch2));
        assertCloseFrameContent(readHttpContent(ch2));
        assertLastContent(readLastHttpContent(ch2));

        assertChannelFinished(ch);
        assertChannelFinished(ch2);
    }

    /*
     * Equivalent to HandlingClose.test_close_request in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void handlingCloseTestCloseRequest() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final String url = sessionUrlFor(serviceFactory, XHR_STREAMING);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(postRequest(url).build());
        assertOkResponse(readHttpResponse(ch));
        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));

        final EmbeddedChannel ch2 = channelForService(serviceFactory);
        ch2.writeInbound(postRequest(url).build());
        assertOkResponse(readHttpResponse(ch2));
        assertPrelude(readHttpContent(ch2));
        assertContent(readHttpContent(ch2), "c[2010,\"Another connection still open\"]\n");

        // read and release EmptyLastHttpContent
        assertLastContent(readLastHttpContent(ch2));
        assertChannelFinished(ch, ch2);
    }

    /*
     * Equivalent to RawWebsocket.test_close in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void rawWebsocketTestClose() throws Exception {
        final SockJsServiceFactory serviceFactory = closeServiceFactory();
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(webSocketUpgradeRequest(serviceFactory));
        assertWebSocketUpgradeResponse(ch);
        assertWebSocketOpenFrame(ch);
        assertWebSocketCloseFrame(ch);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to HandlingClose.test_abort_xhr_streaming in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void handlingCloseTestAbortXhrStreaming() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final String url = sessionUrlFor(serviceFactory, XHR_STREAMING);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(url + XHR_STREAMING.path()).build());
        assertOkResponse(readHttpResponse(ch));
        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));

        final EmbeddedChannel ch2 = channelForService(serviceFactory);
        ch2.writeInbound(getRequest(url + XHR_STREAMING.path()).build());
        assertOkResponse(readHttpResponse(ch2));
        assertPrelude(readHttpContent(ch2));
        assertContent(readHttpContent(ch2), "c[2010,\"Another connection still open\"]\n");
        assertLastContent(readLastHttpContent(ch2));
        ch2.close();

        final EmbeddedChannel ch3 = channelForService(serviceFactory);
        ch3.writeInbound(postRequest(url).build());

        assertOkResponse(readHttpResponse(ch3));
        assertPrelude(readHttpContent(ch3));
        assertContent(readHttpContent(ch3), "c[1002,\"Connection interrupted\"]\n");
        assertLastContent(readLastHttpContent(ch3));

        ch.close();
        assertChannelFinished(ch, ch2, ch3);
    }

    /*
     * Equivalent to HandlingClose.test_abort_xhr_polling in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void handlingCloseTestAbortXhrPolling() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final String url = sessionUrlFor(serviceFactory, XHR);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(url).build());
        assertOpenFrameResponse(readFullHttpResponse(ch));

        final EmbeddedChannel ch2 = channelForService(serviceFactory);
        ch2.writeInbound(getRequest(url).build());
        assertContent(readFullHttpResponse(ch2), "c[2010,\"Another connection still open\"]\n");

        final EmbeddedChannel ch3 = channelForService(serviceFactory);
        ch3.writeInbound(getRequest(url).build());

        assertContent(readFullHttpResponse(ch3), "c[1002,\"Connection interrupted\"]\n");
        assertChannelFinished(ch, ch2, ch3);
    }

    /*
     * Equivalent to Http10.test_synchronous in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void http10TestSynchronous() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(getRequest(serviceFactory.config().prefix(), HTTP_1_0).header(CONNECTION, KEEP_ALIVE).build());

        final FullHttpResponse response = ch.readOutbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.protocolVersion(), is(HTTP_1_0));
        assertThat(response.headers().get(TRANSFER_ENCODING), is(nullValue()));
        assertWelcomeMessage(response);
        if (response.headers().get(CONTENT_LENGTH) == null) {
            assertThat(response.headers().getAndConvert(CONNECTION), equalTo("close"));
            assertThat(ch.isActive(), is(false));
        } else {
            assertThat(response.headers().getAndConvert(CONTENT_LENGTH), is("19"));
            final String connectionHeader = response.headers().getAndConvert(CONNECTION);
            if (connectionHeader.contains("close") || connectionHeader.isEmpty()) {
                assertThat(ch.isActive(), is(false));
            } else {
                assertThat(connectionHeader, equalTo("keep-alive"));
                ch.writeInbound(getRequest(serviceFactory.config().prefix(), HTTP_1_0).build());
                assertOkResponse(readHttpResponse(ch));
            }
        }
        release(response);
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to Http10.test_streaming in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void http10TestStreaming() throws Exception {
        final SockJsServiceFactory serviceFactory = closeServiceFactory();
        final String url = sessionUrlFor(serviceFactory, XHR_STREAMING);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(postRequest(url, HTTP_1_0).header(CONNECTION, KEEP_ALIVE).build());

        final HttpResponse response =  ch.readOutbound();
        assertOkResponse(response);
        assertThat(response.protocolVersion(), is(HTTP_1_0));
        assertThat(response.headers().get(TRANSFER_ENCODING), is(nullValue()));
        assertThat(response.headers().get(CONTENT_LENGTH), is(nullValue()));
        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));
        assertContent(readHttpContent(ch), "c[3000,\"Go away!\"]\n");
        assertLastContent(readLastHttpContent(ch));
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to Http11.test_synchronous in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void http11TestSynchronous() throws Exception {
        final SockJsServiceFactory serviceFactory = echoServiceFactory();
        final EmbeddedChannel ch = channelForService(serviceFactory);

        final FullHttpRequest request = getRequest(serviceFactory.config().prefix()).build();
        request.headers().set(CONNECTION, KEEP_ALIVE);
        ch.writeInbound(request);

        final FullHttpResponse response = ch.readOutbound();
        assertThat(response.status(), is(HttpResponseStatus.OK));
        assertThat(response.protocolVersion(), is(HTTP_1_1));
        String connectionHeader = response.headers().getAndConvert(CONNECTION);
        if (connectionHeader != null) {
            assertThat(connectionHeader, equalTo("keep-alive"));
        }

        assertWelcomeMessage(response);
        if (response.headers().get(CONTENT_LENGTH) != null) {
            assertThat(response.headers().getAndConvert(CONTENT_LENGTH), is("19"));
            assertThat(response.headers().getAndConvert(TRANSFER_ENCODING), is(nullValue()));
        } else {
            assertThat(response.headers().getAndConvert(TRANSFER_ENCODING), is("chunked"));
        }
        release(response);

        ch.writeInbound(getRequest(serviceFactory.config().prefix(), HTTP_1_0).build());
        assertOkResponse(readHttpResponse(ch));
        assertChannelFinished(ch);
    }

    /*
     * Equivalent to Http11.test_streaming in sockjs-protocol-0.3.3.py.
     */
    @Test
    public void http11TestStreaming() throws Exception {
        final SockJsServiceFactory serviceFactory = closeServiceFactory();
        final String url = sessionUrlFor(serviceFactory, XHR_STREAMING);
        final EmbeddedChannel ch = channelForService(serviceFactory);

        ch.writeInbound(postRequest(url).header(CONNECTION, KEEP_ALIVE).build());

        final HttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertThat(response.protocolVersion(), is(HTTP_1_1));
        assertThat(response.headers().getAndConvert(TRANSFER_ENCODING), equalTo("chunked"));
        assertThat(response.headers().get(CONTENT_LENGTH), is(nullValue()));

        assertPrelude(readHttpContent(ch));
        assertOpenFrameContent(readHttpContent(ch));
        assertContent(readHttpContent(ch), "c[3000,\"Go away!\"]\n");
        assertLastContent(readLastHttpContent(ch));
        assertChannelFinished(ch);
    }

    @Test
    public void prefixNotFound() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").cookiesNeeded().build();
        final EmbeddedChannel ch = channelForMockService(config);

        ch.writeInbound(getRequest("/missing").build());
        assertNotFound(readFullHttpResponse(ch));
        assertChannelFinished(ch);
    }

    private static String serverKillerStringEsc() {
        return "\\u200c\\u200d\\u200e\\u200f\\u2028\\u2029\\u202a\\u202b\\u202c\\u202d\\u202e\\u202f\\u2060\\u2061" +
               "\\u2062\\u2063\\u2064\\u2065\\u2066\\u2067\\u2068\\u2069\\u206a\\u206b\\u206c\\u206d\\u206e\\u206f" +
               "\\ufff0\\ufff1\\ufff2\\ufff3\\ufff4\\ufff5\\ufff6\\ufff7\\ufff8\\ufff9\\ufffa\\ufffb\\ufffc\\ufffd" +
               "\\ufffe\\uffff";
    }

    private static String generateUnicodeValues(final int start, final int end) {
        final StringBuilder sb = new StringBuilder();
        for (int i = start ; i <= end; i++) {
            final String hex = Integer.toHexString(i);
            sb.append("\\u");
            switch (hex.length()) {
                case 1: {
                    sb.append("000");
                    break;
                }
                case 2: {
                    sb.append("00");
                    break;
                }
                case 3: {
                    sb.append('0');
                    break;
                }
            }
            sb.append(JsonConverter.escapeCharacters(hex.toCharArray()));
        }
        return sb.toString();
    }

    private static void sendValidCookieNeededEchoRequest(final String transportPath) {
        final String serviceName = "/cookie_needed_echo";
        final String sessionUrl = serviceName + "/abc/" + UUID.randomUUID();
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).cookiesNeeded().build();
        final EmbeddedChannel ch = channelForService(echoServiceFactory(config));

        ch.writeInbound(getRequest(sessionUrl + transportPath).build());
        final HttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertSetCookie("dummy", response);
        release(response);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode infoAsJson(final FullHttpResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.content().toString(UTF_8));
    }

    private void sendValidWebSocketRequest(final WebSocketVersion version) throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final String url = sessionUrlFor(serviceFactory, WEBSOCKET);
        final EmbeddedChannel ch = webSocketChannel(echoServiceFactory(config), corsConfig());

        ch.writeInbound(wsUpgradeRequest(url, version).build());
        assertWebSocketUpgradeResponse(ch);
        // discard open frame.
        release(ch.readOutbound());
        assertChannelFinished(ch);
    }

    private String sessionUrlFor(final SockJsServiceFactory serviceFactory) {
        return serviceFactory.config().prefix() + '/' + SERVER_ID + '/' + sessionId;
    }

    private static String newSessionUrlFor(final SockJsServiceFactory serviceFactory) {
        return serviceFactory.config().prefix() + '/' + SERVER_ID + '/' + UUID.randomUUID();
    }

    private String sessionUrlFor(final SockJsServiceFactory serviceFactory, final TransportType type) {
        return sessionUrlFor(serviceFactory) + type.path();
    }

    private static String newSessionUrlFor(final SockJsServiceFactory serviceFactory, final TransportType type) {
        return newSessionUrlFor(serviceFactory) + type.path();
    }

    private void webSocketTestTransport(final WebSocketVersion version) {
        final String serviceName = "/echo";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(wsUpgradeRequest(sessionUrlFor(serviceFactory, WEBSOCKET), version).build());
        // Discard the HTTP Response (this will be a ByteBuf and not an object
        // as we have a HttpEncoder is in the pipeline to start with.
        release(ch.readOutbound());

        assertWebSocketOpenFrame(ch);
        ch.writeInbound(textWebSocketFrame("\"a\""));
        assertWebSocketTextFrame(ch, "a[\"a\"]");
        assertChannelFinished(ch);
    }

    private void assertWebSocketTestClose(final WebSocketVersion version) {
        final String serviceName = "/close";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).build();
        final SockJsServiceFactory serviceFactory = closeServiceFactory(config);
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(webSocketUpgradeRequest(serviceFactory, version));
        // read and discard the HTTP Response (this will be a ByteBuf and not an object
        // as we have a HttpEncoder in the pipeline to start with.
        release(ch.readOutbound());
        assertWebSocketOpenFrame(ch);
        assertWebSocketCloseFrame(ch);
        assertChannelFinished(ch);
    }

    private void assertWebSocketTestBrokenJSON(final WebSocketVersion version) {
        final String serviceName = "/close";
        final SockJsConfig config = SockJsConfig.withPrefix(serviceName).build();
        final SockJsServiceFactory serviceFactory = echoServiceFactory(config);
        final EmbeddedChannel ch = websocketChannelForService(serviceFactory);

        ch.writeInbound(webSocketUpgradeRequest(serviceFactory, version));
        // read and discard the HTTP Response (this will be a ByteBuf and not an object
        // as we have a HttpEncoder in the pipeline to start with.
        release(ch.readOutbound());
        assertWebSocketOpenFrame(ch);
        ch.writeInbound(textWebSocketFrame("[\"a\""));
        assertThat(ch.isActive(), is(false));
        assertChannelFinished(ch);
    }

    private FullHttpRequest webSocketUpgradeRequest(final SockJsServiceFactory serviceFactory) {
        return webSocketUpgradeRequest(serviceFactory, V13);
    }

    private FullHttpRequest webSocketUpgradeRequest(final SockJsServiceFactory factory,
                                                    final WebSocketVersion version) {
        final String url = sessionUrlFor(factory, WEBSOCKET);
        return wsUpgradeRequest(url, version).build();
    }

    private static String generateMessage(final int characters) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < characters; i++) {
            sb.append('x');
        }
        return sb.toString();
    }

    private FullHttpResponse sendJsonpRequest(final SockJsServiceFactory service, final String pathParam) {
        final EmbeddedChannel ch = jsonpChannelForService(service);
        try {
            ch.writeInbound(getRequest(sessionUrlFor(service, JSONP) + pathParam).build());
            return ch.readOutbound();
        } finally {
            ch.finish();
        }
    }

    private static FullHttpResponse jsonpSend(final FullHttpRequest request, final SockJsServiceFactory service) {
        final EmbeddedChannel ch = jsonpChannelForService(service);
        try {
            ch.writeInbound(request);
            return ch.readOutbound();
        } finally {
            ch.finish();
        }
    }

    private FullHttpResponse sendJsonpSend(final SockJsServiceFactory serviceFactory, final String content) {
        final String url = sessionUrlFor(serviceFactory, JSONP_SEND);
        return sendJsonpSend(url, serviceFactory, content);
    }

    private static FullHttpResponse sendJsonpSend(final String url,
                                           final SockJsServiceFactory serviceFactory,
                                           final String content) {
        return jsonpSend(postRequest(url)
                .header(CONTENT_TYPE, CONTENT_TYPE_FORM)
                .content(content)
                .build(),
                serviceFactory);
    }

    private FullHttpResponse sendXhrSendRequest(final SockJsServiceFactory serviceFactory, final String content) {
        return xhrSendRequest(serviceFactory, sessionUrlFor(serviceFactory), content);
    }

    private static FullHttpResponse xhrSendRequest(final SockJsServiceFactory serviceFactory,
                                              final String path,
                                              final String content) {
        return xhrSendRequestWithContentType(serviceFactory, path, content, CONTENT_TYPE_PLAIN);
    }

    private FullHttpResponse sendXhrSendRequestWithContentType(final SockJsServiceFactory serviceFactory,
                                                               final String content,
                                                               final String contentType) {
        return xhrSendRequestWithContentType(serviceFactory, sessionUrlFor(serviceFactory), content, contentType);
    }

    private static FullHttpResponse xhrSendRequestWithContentType(final SockJsServiceFactory serviceFactory,
                                            final String path,
                                            final String content,
                                            final String contentType) {
        final EmbeddedChannel ch = channelForService(serviceFactory);
        try {
            ch.writeInbound(postRequest(path + XHR_SEND.path())
                    .defaultCors()
                    .content(content)
                    .contentType(contentType)
                    .build());
            return ch.readOutbound();
        } finally {
            ch.finish();
        }
    }

    private FullHttpResponse sendXhrRequest(final SockJsServiceFactory serviceFactory) {
        final EmbeddedChannel ch = channelForService(serviceFactory);
        try {
            ch.writeInbound(getRequest(sessionUrlFor(serviceFactory, XHR)).defaultCors().build());
            return ch.readOutbound();
        } finally {
            ch.finish();
        }
    }

    private static FullHttpResponse infoRequest(final String url, final SockJsServiceFactory serviceFactory) {
        final EmbeddedChannel ch = channelForService(serviceFactory);
        try {
            ch.writeInbound(getRequest(url + "/info").defaultCors().build());
            return ch.readOutbound();
        } finally {
            ch.finish();
        }
    }

    private static HttpResponse sendXhrRequest(final FullHttpRequest request,
                                               final SockJsServiceFactory serviceFactory,
                                               final CorsConfig corsConfig) {
        final EmbeddedChannel ch = channelForService(serviceFactory, corsConfig);
        try {
            ch.writeInbound(request);
            return ch.readOutbound();
        } finally {
            ch.finish();
        }
    }

    private static void sendSuccessfulRequest(final String sessionPart) {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo").cookiesNeeded().build();
        final EmbeddedChannel ch = channelForMockService(config);
        ch.writeInbound(getRequest("/echo" + sessionPart + TransportType.XHR.path()).defaultCors().build());
        final FullHttpResponse response = ch.readOutbound();
        assertOpenFrameResponse(response);
    }

    private static void sendValidIframeRequest(final String service, final String path) {
        final SockJsConfig config = SockJsConfig.withPrefix(service).cookiesNeeded().build();
        final EmbeddedChannel ch = channelForMockService(config);

        ch.writeInbound(getRequest(config.prefix() + path).defaultCors().build());
        final FullHttpResponse response = ch.readOutbound();
        assertOkResponse(response);
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), equalTo("text/html; charset=UTF-8"));
        assertThat(response.headers().getAndConvert(CACHE_CONTROL), equalTo("max-age=31536000, public"));
        assertThat(response.headers().getAndConvert(EXPIRES), is(notNullValue()));
        assertNoSetCookie(response);
        assertThat(response.headers().get(ETAG), is(notNullValue()));
        assertIframeContent(response, config.sockJsUrl());
        release(response);
    }

    private static long getEntropy(final FullHttpResponse response) throws Exception {
        try {
            return contentAsJson(response).get("entropy").asLong();
        } finally {
            release(response);
        }
    }

    private static void sendInvalidUrlRequest(final String service, final String path) {
        final SockJsConfig config = SockJsConfig.withPrefix(service).cookiesNeeded().build();
        final EmbeddedChannel ch = channelForMockService(config);
        ch.writeInbound(getRequest('/' + service + path).build());
        assertNotFound(readHttpResponse(ch));
    }

    private static JsonNode contentAsJson(final FullHttpResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.content().toString(UTF_8));
    }

    private static FullHttpResponse infoForMockService(final SockJsConfig config) {
        final EmbeddedChannel ch = channelForMockService(config);
        try {
            ch.writeInbound(getRequest(config.prefix() + "/info").defaultCors().build());
            return ch.readOutbound();
        } finally {
            ch.close();
        }
    }

    /*
     * Escapes unicode characters in the passed in char array to a Java string with
     * Java style escaped charaters.
     *
     * @param value the char[] for which unicode characters should be escaped
     * @return {@code String} Java style escaped unicode characters.
     */
    private static String escapeCharacters(final char[] value) {
        final StringBuilder buffer = new StringBuilder();
        for (char ch : value) {
            if (ch >= '\u0000' && ch <= '\u001F' ||
                    ch >= '\uD800' && ch <= '\uDFFF' ||
                    ch >= '\u200C' && ch <= '\u200F' ||
                    ch >= '\u2028' && ch <= '\u202F' ||
                    ch >= '\u2060' && ch <= '\u206F' ||
                    ch >= '\uFFF0' && ch <= '\uFFFF') {
                final String ss = Integer.toHexString(ch);
                buffer.append('\\').append('u');
                for (int k = 0; k < 4 - ss.length(); k++) {
                    buffer.append('0');
                }
                buffer.append(ss.toLowerCase());
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }

    private static TextWebSocketFrame textWebSocketFrame(final String content) {
        return new TextWebSocketFrame(content);
    }

    private static HttpContent readHttpContent(final EmbeddedChannel ch) {
        return readResponse(ch);
    }

    private static LastHttpContent readLastHttpContent(final EmbeddedChannel ch) {
        return readResponse(ch);
    }

    private static HttpResponse readHttpResponse(final EmbeddedChannel ch) {
        return readResponse(ch);
    }

    private static FullHttpResponse readFullHttpResponse(final EmbeddedChannel ch) {
        return readResponse(ch);
    }

    private static ByteBuf readByteBuf(final EmbeddedChannel ch) {
        return readResponse(ch);
    }

    private static <T> T readResponse(final EmbeddedChannel ch) {
        return ch.readOutbound();
    }

}
