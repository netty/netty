/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.httpx;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.stream.Stream;
import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ServerCookieEncoder;

/**
 * An HTTP response.
 *
 * <h3>Accessing Cookies</h3>
 * <p>
 * Unlike the Servlet API, {@link Cookie} support is provided separately via {@link CookieDecoder},
 * {@link ClientCookieEncoder}, and {@link ServerCookieEncoder}.
 *
 * @see HttpRequest
 * @see CookieDecoder
 * @see ClientCookieEncoder
 * @see ServerCookieEncoder
 */
public interface HttpResponse extends HttpMessage {

    /**
     * Returns the status of this {@link HttpResponse}.
     *
     * @return The {@link HttpResponseStatus} of this {@link HttpResponse}
     */
    HttpResponseStatus getStatus();

    /**
     * Set the status of this {@link HttpResponse}.
     */
    HttpResponse setStatus(HttpResponseStatus status);

    @Override
    HttpResponse setVersion(HttpVersion version);

    @Override
    HttpResponse setContent(ByteBuf content);

    @Override
    HttpResponse setContent(Stream<ByteBuf> content);
}
