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

import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.sockjs.util.HttpRequestBuilder.getRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class InfoTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    public void webSocketSupported() throws Exception {
        final FullHttpResponse response = infoResponse(SockJsConfig.withPrefix("/simplepush").build());
        assertThat(infoAsJson(response).get("websocket").asBoolean(), is(true));
        response.release();
    }

    @Test
    public void webSocketNotSupported() throws Exception {
        final FullHttpResponse response = infoResponse(SockJsConfig.withPrefix("/simplepush")
                .disableWebSocket()
                .build());
        assertThat(infoAsJson(response).get("websocket").asBoolean(), is(false));
        response.release();
    }

    @Test
    public void cookiesNeeded() throws Exception {
        final FullHttpResponse response = infoResponse(SockJsConfig.withPrefix("/simplepush")
                .disableWebSocket()
                .cookiesNeeded()
                .build());
        assertThat(infoAsJson(response).get("cookie_needed").asBoolean(), is(true));
        response.release();
    }

    @Test
    public void cookiesNotNeeded() throws Exception {
        final FullHttpResponse response = infoResponse(SockJsConfig.withPrefix("/simplepush")
                .disableWebSocket().build());
        assertThat(infoAsJson(response).get("cookie_needed").asBoolean(), is(false));
        response.release();
    }

    @Test
    public void origins() throws Exception {
        final FullHttpResponse response = infoResponse(SockJsConfig.withPrefix("/simplepush")
                .disableWebSocket()
                .build());
        assertThat(infoAsJson(response).get("origins").get(0).asText(), is("*:*"));
        response.release();
    }

    @Test
    public void entropy() throws Exception {
        final FullHttpResponse response = infoResponse(SockJsConfig.withPrefix("/simplepush")
                .disableWebSocket()
                .build());
        assertThat(infoAsJson(response).get("entropy").asLong(), is(notNullValue()));
        response.release();
    }

    @Test
    public void contentTypeHeader() throws Exception {
        assertThat(headersFromInfo().getAndConvert(CONTENT_TYPE), equalTo("application/json; charset=UTF-8"));
    }

    @Test
    public void expiresHeader() throws Exception {
        assertThat(headersFromInfo().getAndConvert(EXPIRES), is(nullValue()));
    }

    @Test
    public void lastModifiedHeader() throws Exception {
        assertThat(headersFromInfo().getAndConvert(LAST_MODIFIED), is(nullValue()));
    }

    @Test
    public void setCookieHeader() throws Exception {
        assertThat(headersFromInfo().getAndConvert(SET_COOKIE), is(nullValue()));
    }

    @Test
    public void contentLenghtHeader() throws Exception {
        assertThat(headersFromInfo().getAndConvert(CONTENT_LENGTH), is(notNullValue()));
    }

    @Test
    public void cacheControlHeader() throws Exception {
        final String cacheControl = headersFromInfo().getAndConvert(CACHE_CONTROL);
        assertThat(cacheControl.contains("no-store"), is(true));
        assertThat(cacheControl.contains("no-cache"), is(true));
        assertThat(cacheControl.contains("must-revalidate"), is(true));
        assertThat(cacheControl.contains("max-age"), is(true));
    }

    @Test
    public void matchesNull() {
        assertThat(Info.matches(null), is(false));
    }

    @Test
    public void matchesSlash() {
        assertThat(Info.matches("/"), is(false));
    }

    @Test
    public void matches() {
        assertThat(Info.matches("/info"), is(true));
    }

    private static HttpHeaders headersFromInfo() throws Exception {
        final SockJsConfig config = SockJsConfig.withPrefix("/simplepush").disableWebSocket().build();
        final FullHttpResponse response = infoResponse(config);
        final HttpHeaders headers = response.headers();
        response.release();
        return headers;
    }

    private static JsonNode infoAsJson(final FullHttpResponse response) throws Exception {
        return OM.readTree(response.content().toString(CharsetUtil.UTF_8));
    }

    private static FullHttpResponse infoResponse(final SockJsConfig config) throws Exception {
        return Info.response(config, getRequest(config.prefix() + "/info").build());
    }

}
