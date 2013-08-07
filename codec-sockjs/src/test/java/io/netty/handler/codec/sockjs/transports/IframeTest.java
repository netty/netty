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
package io.netty.handler.codec.sockjs.transports;

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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.Iframe;

import java.nio.charset.Charset;

import org.junit.Test;

public class IframeTest {

    @Test
    public void iframeHtm() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.htm";
        final FullHttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.NOT_FOUND.code()));
    }

    @Test
    public void iframeHTML() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.HTML";
        final FullHttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.NOT_FOUND.code()));
    }

    @Test
    public void iframeHtmlUppercase() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/IFRAME.HTML";
        final FullHttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.NOT_FOUND.code()));
    }

    @Test
    public void iframeXml() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.xml";
        final FullHttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.NOT_FOUND.code()));
    }

    @Test
    public void iframeUppercase() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/IFRAME";
        final FullHttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.NOT_FOUND.code()));
    }

    @Test
    public void ifNoneMatchHeader() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.html";
        final HttpRequest httpRequest = createHttpRequest(path);
        httpRequest.headers().set(HttpHeaders.Names.IF_NONE_MATCH, "*");
        final FullHttpResponse response = Iframe.response(config, httpRequest);
        assertThat(response.headers().get(HttpHeaders.Names.SET_COOKIE), equalTo("JSESSIONID=dummy; path=/"));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.NOT_MODIFIED.code()));
    }

    @Test
    public void iframeHtml() throws Exception {
        final SockJsConfig config = config();
        final String path = config.prefix() + "/iframe.html";
        final FullHttpResponse response = Iframe.response(config, createHttpRequest(path));
        assertThat(response.getStatus().code(), is(HttpResponseStatus.OK.code()));
        assertThat(response.headers().get(HttpHeaders.Names.CONTENT_TYPE), equalTo("text/html; charset=UTF-8"));
        assertThat(response.headers().get(HttpHeaders.Names.CACHE_CONTROL), equalTo("max-age=31536000, public"));
        assertThat(response.headers().get(HttpHeaders.Names.EXPIRES), is(notNullValue()));
        assertThat(response.headers().get(HttpHeaders.Names.SET_COOKIE), is(nullValue()));
        assertThat(response.headers().get(HttpHeaders.Names.ETAG), is(notNullValue()));
    }

    private SockJsConfig config() {
        return SockJsConfig.prefix("/simplepush").sockjsUrl("http://cdn.sockjs.org/sockjs-0.3.4.min.js").build();
    }

    private HttpRequest createHttpRequest(final String path) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, path,
                Unpooled.copiedBuffer("", Charset.defaultCharset()));
    }

}
