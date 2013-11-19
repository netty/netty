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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

public class InfoTest {

    @Test
    public void webSocketSupported() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        assertThat(infoAsJson(response).get("websocket").asBoolean(), is(true));
    }

    @Test
    public void webSocketNotSupported() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        assertThat(infoAsJson(response).get("websocket").asBoolean(), is(false));
    }

    @Test
    public void cookiesNeeded() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().cookiesNeeded().build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        assertThat(infoAsJson(response).get("cookie_needed").asBoolean(), is(true));
    }

    @Test
    public void cookiesNotNeeded() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        assertThat(infoAsJson(response).get("cookie_needed").asBoolean(), is(false));
    }

    @Test
    public void origins() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        assertThat(infoAsJson(response).get("origins").get(0).asText(), is("*:*"));
    }

    @Test
    public void entropy() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        assertThat(infoAsJson(response).get("entropy").asLong(), is(notNullValue()));
    }

    @Test
    public void contentTypeHeader() throws Exception {
        assertThat(headersFromInfo().get(HttpHeaders.Names.CONTENT_TYPE), equalTo("application/json; charset=UTF-8"));
    }

    @Test
    public void expiresHeader() throws Exception {
        assertThat(headersFromInfo().get(HttpHeaders.Names.EXPIRES), is(nullValue()));
    }

    @Test
    public void lastModifiedHeader() throws Exception {
        assertThat(headersFromInfo().get(HttpHeaders.Names.LAST_MODIFIED), is(nullValue()));
    }

    @Test
    public void setCookieHeader() throws Exception {
        assertThat(headersFromInfo().get(HttpHeaders.Names.SET_COOKIE), is(nullValue()));
    }

    @Test
    public void contentLenghtHeader() throws Exception {
        assertThat(headersFromInfo().get(HttpHeaders.Names.CONTENT_LENGTH), is(notNullValue()));
    }

    @Test
    public void cacheControlHeader() throws Exception {
        final String cacheControl = headersFromInfo().get(HttpHeaders.Names.CACHE_CONTROL);
        assertThat(cacheControl.contains("no-store"), is(true));
        assertThat(cacheControl.contains("no-cache"), is(true));
        assertThat(cacheControl.contains("must-revalidate"), is(true));
        assertThat(cacheControl.contains("max-age"), is(true));
    }

    private static HttpHeaders headersFromInfo() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().build();
        final FullHttpResponse response = Info.response(config, createHttpRequest("/simplepush"));
        return response.headers();
    }

    private static JsonNode infoAsJson(final FullHttpResponse response) throws Exception {
        final ObjectMapper om = new ObjectMapper();
        return om.readTree(response.content().toString(CharsetUtil.UTF_8));
    }

    private static HttpRequest createHttpRequest(final String prefix) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, prefix + "/info",
                Unpooled.copiedBuffer("", Charset.defaultCharset()));
    }

}
