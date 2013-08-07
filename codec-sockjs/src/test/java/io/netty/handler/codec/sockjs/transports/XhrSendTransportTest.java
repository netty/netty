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

import static io.netty.handler.codec.sockjs.SockJSTestUtil.verifyDefaultResponseHeaders;
import static io.netty.handler.codec.sockjs.SockJSTestUtil.verifyContentType;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.sockjs.Config;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class XhrSendTransportTest {

    @Test (expected = NullPointerException.class)
    public void constructWithoutConfig() {
        new XhrSendTransport(null);
    }

    @Test
    public void messageReceivedNoPayload() {
        final FullHttpResponse response = processHttpRequest(requestWithBody(null));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("Payload expected."));
    }

    @Test
    public void messageReceivedNoPayloadHttpVersion1_0() {
        final FullHttpResponse response = processHttpRequest(requestWithBody(null, HttpVersion.HTTP_1_0));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_0));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("Payload expected."));
    }

    @Test
    public void messageReceivedSimpleString() {
        final String body = "[\"some message\"]";
        final FullHttpResponse response = processHttpRequest(requestWithBody(body));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.NO_CONTENT));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        assertThat(response.content().capacity(), is(0));
        verifyDefaultResponseHeaders(response, Transports.CONTENT_TYPE_PLAIN);
    }

    @Test
    public void messageReceivedJsonObject() {
        final String body = "[{\"firstName\": \"Fletch\"}]";
        final FullHttpResponse response = processHttpRequest(requestWithBody(body));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.NO_CONTENT));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        assertThat(response.content().capacity(), is(0));
        verifyDefaultResponseHeaders(response, Transports.CONTENT_TYPE_PLAIN);
    }

    @Test
    public void messageReceivedNoFormDataParameter() {
        final FullHttpResponse response = processHttpRequest(requestWithFormData(null));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("Payload expected."));
    }

    @Test
    public void messageReceivedFormDataParameter() {
        final String data = "[\"some message\"]";
        final FullHttpResponse response = processHttpRequest(requestWithFormData(data));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.NO_CONTENT));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        assertThat(response.content().capacity(), is(0));
        verifyDefaultResponseHeaders(response, Transports.CONTENT_TYPE_PLAIN);
    }

    @Test
    public void messageReceivedInvalidJson() {
        final String data = "[\"some message";
        final FullHttpResponse response = processHttpRequest(requestWithFormData(data));
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        verifyContentType(response, Transports.CONTENT_TYPE_PLAIN);
        assertThat(response.content().toString(UTF_8), equalTo("Broken JSON encoding."));
    }

    private FullHttpResponse processHttpRequest(final FullHttpRequest request) {
        final XhrSendTransport transport = new XhrSendTransport(Config.prefix("/test").cookiesNeeded().build());
        final EmbeddedChannel channel = new EmbeddedChannel(transport);
        channel.writeInbound(request);
        final FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
        channel.finish();
        return response;
    }

    private FullHttpRequest requestWithBody(final String body) {
        return requestWithBody(body, HttpVersion.HTTP_1_1);
    }

    private FullHttpRequest requestWithBody(final String body, HttpVersion httpVersion) {
        final DefaultFullHttpRequest r = new DefaultFullHttpRequest(httpVersion, HttpMethod.GET, "/test");
        if (body != null) {
            r.content().writeBytes(Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        }
        return r;
    }

    private FullHttpRequest requestWithFormData(final String data) {
        final DefaultFullHttpRequest r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/test");
        r.headers().set(HttpHeaders.Names.CONTENT_TYPE, Transports.CONTENT_TYPE_FORM);
        if (data == null) {
            r.content().writeBytes(Unpooled.copiedBuffer("d=", CharsetUtil.UTF_8));
        } else {
            r.content().writeBytes(Unpooled.copiedBuffer("d=" + data, CharsetUtil.UTF_8));
        }
        return r;
    }

}
