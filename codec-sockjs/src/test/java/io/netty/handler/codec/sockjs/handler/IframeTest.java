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
import static io.netty.handler.codec.http.HttpHeaders.Names.ETAG;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.IF_NONE_MATCH;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.assertContentType;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.assertNotFound;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.assertOkResponse;
import static io.netty.util.ReferenceCountUtil.release;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.sockjs.SockJsConfig;

import io.netty.handler.codec.sockjs.util.HttpRequestBuilder;
import org.junit.Test;

public class IframeTest {

    @Test
    public void iframeHtm() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.htm";
        assertNotFound(Iframe.response(config, createHttpRequest(path)));
    }

    @Test
    public void iframeHTML() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.HTML";
        assertNotFound(Iframe.response(config, createHttpRequest(path)));
    }

    @Test
    public void iframeHtmlUppercase() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/IFRAME.HTML";
        assertNotFound(Iframe.response(config, createHttpRequest(path)));
    }

    @Test
    public void iframeXml() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.xml";
        assertNotFound(Iframe.response(config, createHttpRequest(path)));
    }

    @Test
    public void iframeUppercase() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/IFRAME";
        assertNotFound(Iframe.response(config, createHttpRequest(path)));
    }

    @Test
    public void ifNoneMatchHeader() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.html";
        final HttpRequest httpRequest = createHttpRequest(path);
        httpRequest.headers().set(IF_NONE_MATCH, "*");
        final HttpResponse response = Iframe.response(config, httpRequest);
        assertThat(response.headers().getAndConvert(SET_COOKIE), equalTo("JSESSIONID=dummy; path=/"));
        assertThat(response.status().code(), is(NOT_MODIFIED.code()));
        release(response);
    }

    @Test
    public void iframeHtml() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.html";
        final HttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertOkResponse(response);
        assertContentType(response, "text/html; charset=UTF-8");
        assertThat(response.headers().getAndConvert(CACHE_CONTROL), equalTo("max-age=31536000, public"));
        assertThat(response.headers().getAndConvert(EXPIRES), is(notNullValue()));
        assertThat(response.headers().getAndConvert(SET_COOKIE), is(nullValue()));
        assertThat(response.headers().get(ETAG), is(notNullValue()));
        release(response);
    }

    @Test
    public void matchesNull() {
        assertThat(Iframe.matches(null), is(false));
    }

    @Test
    public void matchesSlash() {
        assertThat(Iframe.matches("/"), is(false));
    }

    @Test
    public void matches() {
        assertThat(Iframe.matches("/iframe.html"), is(true));
    }

    private static SockJsConfig config() {
        return SockJsConfig.withPrefix("/simplepush").build();
    }

    private static HttpRequest createHttpRequest(final String path) {
        return HttpRequestBuilder.getRequest(path).build();
    }

}
