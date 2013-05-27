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
package io.netty.handler.codec.sockjs.util;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCESS_CONTROL_MAX_AGE;
import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.ETAG;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.util.ReferenceCountUtil.release;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.sockjs.protocol.PreludeFrame;

import java.util.regex.Pattern;

public final class SockJsAsserts {

    private static final Pattern SEMICOLON = Pattern.compile(";");
    private static final boolean NOTHING_IN_BUFFERS = false;

    private SockJsAsserts() {
    }

    public static void assertDefaultResponseHeaders(final HttpResponse response, final String contentType) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.getAndConvert(CONTENT_TYPE), equalTo(contentType));
        assertNoCache(response);
    }

    public static void assertContentType(final HttpResponse response, final String contentType) {
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), equalTo(contentType));
    }

    public static void assertOkResponse(final HttpResponse response) {
        assertThat(response.status(), equalTo(OK));
    }

    public static void assertNoCache(final HttpResponse response) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.getAndConvert(CACHE_CONTROL), containsString("no-store"));
        assertThat(headers.getAndConvert(CACHE_CONTROL), containsString("no-cache"));
        assertThat(headers.getAndConvert(CACHE_CONTROL), containsString("must-revalidate"));
        assertThat(headers.getAndConvert(CACHE_CONTROL), containsString("max-age=0"));
    }

    public static void assertOrigin(final HttpResponse response, final String origin) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.getAndConvert(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo(origin));
        assertThat(headers.getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
    }

    public static void assertOriginLocalhost(final HttpResponse response) {
        assertOrigin(response, "http://localhost");
    }

    public static void assertAccessRequestAllowHeaders(final HttpResponse response, final String headers) {
        assertThat(response.headers().getAndConvert(ACCESS_CONTROL_ALLOW_HEADERS), equalTo(headers));
    }

    public static void assertNoSetCookie(final HttpResponse response) {
        assertThat(response.headers().get(SET_COOKIE), is(nullValue()));
    }

    public static void assertNotCached(final HttpResponse response) {
        assertThat(response.headers().getAndConvert(CACHE_CONTROL), containsString("no-store"));
        assertThat(response.headers().getAndConvert(CACHE_CONTROL), containsString("no-cache"));
        assertThat(response.headers().getAndConvert(CACHE_CONTROL), containsString("must-revalidate"));
        assertThat(response.headers().getAndConvert(CACHE_CONTROL), containsString("max-age=0"));
    }

    public static void assertCorsPreflightHeaders(final HttpResponse response, HttpMethod... methods) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.getAndConvert(CONTENT_TYPE), is("text/plain; charset=UTF-8"));
        assertThat(headers.getAndConvert(CACHE_CONTROL), containsString("public"));
        assertThat(headers.getAndConvert(CACHE_CONTROL), containsString("max-age=31536000"));
        assertThat(headers.getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(headers.getAndConvert(ACCESS_CONTROL_MAX_AGE), is("31536000"));
        for (HttpMethod method : methods) {
            assertThat(headers.getAndConvert(ACCESS_CONTROL_ALLOW_METHODS), containsString(method.toString()));
        }
        assertThat(headers.getAndConvert(ACCESS_CONTROL_ALLOW_HEADERS), is("content-type"));
        assertThat(headers.getAndConvert(ACCESS_CONTROL_ALLOW_CREDENTIALS), is("true"));
        assertThat(headers.get(EXPIRES), is(notNullValue()));
        assertThat(headers.getAndConvert(SET_COOKIE), is("JSESSIONID=dummy;path=/"));
    }

    public static void assertMessageFrameContent(final FullHttpResponse response, final String expected) {
        assertThat(response.status(), is(OK));
        assertThat(response.content().toString(UTF_8), equalTo("a[\"" + expected + "\"]\n"));
        release(response);
    }

    public static void assertNoContent(final FullHttpResponse response) {
        assertThat(response.status(), is(NO_CONTENT));
        assertThat(response.content().isReadable(), is(false));
        release(response);
    }

    public static void assertContent(final FullHttpResponse response, final String expected) {
        assertThat(response.status(), is(OK));
        assertThat(response.content().toString(UTF_8), equalTo(expected));
        release(response);
    }

    public static void assertContent(final HttpContent httpContent, final String expected) {
        assertThat(httpContent.content().toString(UTF_8), equalTo(expected));
        release(httpContent);
    }

    public static void assertHtmlfile(final HttpContent httpContent) {
        assertThat(httpContent.content().toString(UTF_8).trim(), equalTo(EXPECTED_HTML_FILE));
        release(httpContent);
    }

    public static void assertHtmlOpenScript(final HttpContent httpContent) {
        assertThat(httpContent.content().toString(UTF_8), equalTo(EXPECTED_HTML_OPEN_SCRIPT));
        release(httpContent);
    }

    public static void assertLastContent(final LastHttpContent lastContent) {
        assertThat(lastContent.content().readableBytes(), is(0));
        release(lastContent);
    }

    public static void assertNotFound(final HttpResponse response) {
        assertThat(response.status(), is(NOT_FOUND));
        release(response);
    }

    public static void assertMethodNotAllowed(final HttpResponse response) {
        assertThat(response.status(), is(METHOD_NOT_ALLOWED));
        release(response);
    }

    public static void assertOpenFrameResponse(final FullHttpResponse response) {
        assertThat(response.status(), is(OK));
        assertThat(response.content().toString(UTF_8), equalTo("o\n"));
        release(response.release());
    }

    public static void assertOpenFrameContent(final HttpContent content) {
        assertThat(content.content().toString(UTF_8), equalTo("o\n"));
        release(content);
    }

    public static void assertGoAwayResponse(final FullHttpResponse response) {
        assertThat(response.status(), is(OK));
        assertThat(response.content().toString(UTF_8), equalTo("c[3000,\"Go away!\"]\n"));
    }

    public static void assertPayloadExpected(final FullHttpResponse response) {
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(response.content().toString(UTF_8), equalTo("Payload expected."));
        release(response.release());
    }

    public static void assertSetCookie(final String sessionId, final HttpResponse response) {
        final String setCookie = response.headers().getAndConvert(SET_COOKIE);
        final String[] split = SEMICOLON.split(setCookie);
        assertThat(split[0], equalTo("JSESSIONID=" + sessionId));
        assertThat(split[1].trim().toLowerCase(), equalTo("path=/"));
    }

    public static void assertBrokenJSONEncoding(final FullHttpResponse response) {
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(response.content().toString(UTF_8), equalTo("Broken JSON encoding."));
        release(response.release());
    }

    public static void assertWebSocketOpenFrame(final EmbeddedChannel ch) {
        final TextWebSocketFrame openFrame = ch.readOutbound();
        assertThat(openFrame.content().toString(UTF_8), equalTo("o"));
        release(openFrame);
    }

    public static void assertWebSocketUpgradeResponse(final EmbeddedChannel ch) throws Exception {
        final HttpResponse upgradeResponse = HttpUtil.decode(ch);
        assertThat(upgradeResponse.status(), equalTo(HttpResponseStatus.SWITCHING_PROTOCOLS));
        final String connection = upgradeResponse.headers().getAndConvert(CONNECTION);
        assertThat(connection.equalsIgnoreCase(Values.UPGRADE.toString()), is(true));
        release(upgradeResponse);
    }

    public static void assertWebSocketUpgradeResponse(final EmbeddedChannel ch, final String expected)
            throws Exception {
        final FullHttpResponse upgradeResponse = HttpUtil.decodeFullHttpResponse(ch);
        assertThat(upgradeResponse.content().toString(UTF_8), equalTo(expected));
        assertThat(upgradeResponse.status(), equalTo(HttpResponseStatus.SWITCHING_PROTOCOLS));
        release(upgradeResponse);
    }

    public static void assertWebSocketTextFrame(final EmbeddedChannel ch, final String expected) {
        final TextWebSocketFrame textFrame = ch.readOutbound();
        assertThat(textFrame.content().toString(UTF_8), equalTo(expected));
        release(textFrame.release());
    }

    public static void assertWebSocketCloseFrame(final EmbeddedChannel ch) {
        final TextWebSocketFrame closeFrame = ch.readOutbound();
        assertThat(closeFrame.content().toString(UTF_8), equalTo("c[3000,\"Go away!\"]"));
        release(closeFrame);
    }

    public static void assertCanOnlyUpgradeToWebSocket(final EmbeddedChannel ch) {
        final FullHttpResponse response = ch.readOutbound();
        assertThat(response.status(), equalTo(HttpResponseStatus.BAD_REQUEST));
        assertThat(response.content().toString(UTF_8), equalTo("Can \"Upgrade\" only to \"WebSocket\"."));
        release(response);
    }

    public static void assertConnectionMustBeUpgrade(final EmbeddedChannel ch) {
        final FullHttpResponse response = ch.readOutbound();
        assertThat(response.status(), equalTo(HttpResponseStatus.BAD_REQUEST));
        assertThat(response.content().toString(UTF_8), equalTo("\"Connection\" must be \"Upgrade\"."));
        release(response);
    }

    public static void assertChannelFinished(final EmbeddedChannel... channels) {
        for (EmbeddedChannel ch : channels) {
            assertThat(ch.finish(), is(NOTHING_IN_BUFFERS));
        }
    }

    public static void assertInternalServerError(final FullHttpResponse response, final String msg) {
        assertThat(response.status(), is(INTERNAL_SERVER_ERROR));
        assertThat(response.content().toString(UTF_8), equalTo(msg));
        release(response);
    }

    public static void assertNoContent(final HttpResponse response) {
        assertThat(response.status(), is(NO_CONTENT));
        release(response);
    }

    public static void assertNotModified(final HttpResponse response) {
        assertThat(response.status(), equalTo(HttpResponseStatus.NOT_MODIFIED));
        assertThat(response.headers().get(CONTENT_TYPE), is(nullValue()));
        release(response);
    }

    public static void assertEntityTag(final FullHttpResponse response, final String expected) {
        assertThat(response.headers().getAndConvert(ETAG), equalTo(expected));
        release(response);
    }

    public static void assertPrelude(final HttpContent httpContent) {
        final int size = PreludeFrame.CONTENT_SIZE + 1;
        assertThat(httpContent.content().readableBytes(), is(size));
        assertThat(httpContent.content().toString(UTF_8), equalTo(generatePrelude(size)));
        release(httpContent);
    }

    public static void assertCloseFrameContent(final HttpContent httpContent) {
        assertContent(httpContent, "c[3000,\"Go away!\"]\n");
    }

    public static void assertWelcomeMessage(final FullHttpResponse response) {
        assertThat(response.content().toString(UTF_8), equalTo("Welcome to SockJS!\n"));
    }

    public static void assertByteContent(final ByteBuf buf, final String content) {
        assertThat(buf.toString(UTF_8), equalTo(content));
        release(buf);
    }

    public static void assertIframeContent(final FullHttpResponse response, final String sockjsUrl) {
        assertThat(response.content().toString(UTF_8), equalTo(iframeHtml(sockjsUrl)));
    }

    private static String generatePrelude(final int size) {
        final StringBuilder sb = new StringBuilder(size);
        for (int i = 1; i < size; i++) {
            sb.append('h');
        }
        sb.append('\n');
        return sb.toString();
    }

    private static final String EXPECTED_HTML_FILE = "<!doctype html>\n" +
            "<html><head>\n" +
            "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
            "</head><body><h2>Don't panic!</h2>\n" +
            "  <script>\n" +
            "    document.domain = document.domain;\n" +
            "    var c = parent.callback;\n" +
            "    c.start();\n" +
            "    function p(d) {c.message(d);};\n" +
            "    window.onload = function() {c.stop();};\n" +
            "  </script>";

    private static final String EXPECTED_HTML_OPEN_SCRIPT = "<script>\n" +
            "p(\"o\");\n" +
            "</script>\r\n";

    private static String iframeHtml(final String sockJSUrl) {
        return "<!DOCTYPE html>\n" + "<html>\n" + "<head>\n"
                + "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n"
                + "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" + "  <script>\n"
                + "    document.domain = document.domain;\n"
                + "    _sockjs_onload = function(){SockJS.bootstrap_iframe();};\n" + "  </script>\n"
                + "  <script src=\"" + sockJSUrl + "\"></script>\n" + "</head>\n" + "<body>\n"
                + "  <h2>Don't panic!</h2>\n"
                + "  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>\n" + "</body>\n"
                + "</html>";
    }
}
