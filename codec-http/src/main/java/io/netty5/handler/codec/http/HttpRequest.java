/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty5.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty5.handler.codec.http.cookie.Cookie;
import io.netty5.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty5.handler.codec.http.cookie.ServerCookieEncoder;

/**
 * An HTTP request.
 *
 * <h3>Accessing Query Parameters and Cookie</h3>
 * <p>
 * Unlike the Servlet API, a query string is constructed and decomposed by
 * {@link QueryStringEncoder} and {@link QueryStringDecoder}.
 *
 * {@link Cookie} support is also provided separately via {@link ServerCookieDecoder}, {@link ClientCookieDecoder},
 * {@link ServerCookieEncoder}, and {@link ClientCookieEncoder}.
 *
 * @see HttpResponse
 * @see ServerCookieDecoder
 * @see ClientCookieDecoder
 * @see ServerCookieEncoder
 * @see ClientCookieEncoder
 */
public interface HttpRequest extends HttpMessage {

    /**
     * Returns the {@link HttpMethod} of this {@link HttpRequest}.
     *
     * @return The {@link HttpMethod} of this {@link HttpRequest}
     */
    HttpMethod method();

    /**
     * Set the {@link HttpMethod} of this {@link HttpRequest}.
     */
    HttpRequest setMethod(HttpMethod method);

    /**
     * Returns the requested URI (or alternatively, path)
     *
     * @return The URI being requested
     */
    String uri();

    /**
     *  Set the requested URI (or alternatively, path)
     */
    HttpRequest setUri(String uri);

    @Override
    HttpRequest setProtocolVersion(HttpVersion version);
}
