/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static org.junit.Assert.*;

public class WebSocketHandshakeExceptionTest {

    @Test
    public void testClientExceptionWithoutResponse() {
        WebSocketClientHandshakeException clientException = new WebSocketClientHandshakeException("client message");

        assertNull(clientException.response());
        assertEquals("client message", clientException.getMessage());
    }

    @Test
    public void testClientExceptionWithResponse() {
        HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
        httpResponse.headers().set("x-header", "x-value");
        WebSocketClientHandshakeException clientException = new WebSocketClientHandshakeException("client message",
                                                                                                  httpResponse);

        assertNotNull(clientException.response());
        assertEquals("client message", clientException.getMessage());
        assertEquals(HttpResponseStatus.BAD_REQUEST, clientException.response().status());
        assertEquals(httpResponse.headers(), clientException.response().headers());
    }

    @Test
    public void testServerExceptionWithoutRequest() {
        WebSocketServerHandshakeException serverException = new WebSocketServerHandshakeException("server message");

        assertNull(serverException.request());
        assertEquals("server message", serverException.getMessage());
    }

    @Test
    public void testClientExceptionWithRequest() {
        HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                                                         "ws://localhost:9999/ws");
        httpRequest.headers().set("x-header", "x-value");
        WebSocketServerHandshakeException serverException = new WebSocketServerHandshakeException("server message",
                                                                                                  httpRequest);

        assertNotNull(serverException.request());
        assertEquals("server message", serverException.getMessage());
        assertEquals(HttpMethod.GET, serverException.request().method());
        assertEquals(httpRequest.headers(), serverException.request().headers());
        assertEquals(httpRequest.uri(), serverException.request().uri());
    }
}
