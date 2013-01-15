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
package io.netty.handler.codec.http;


/**
 * An HTTP response.
 *
 * <h3>Accessing Cookies</h3>
 * <p>
 * Unlike the Servlet API, {@link Cookie} support is provided separately via {@link CookieDecoder},
 * {@link ClientCookieEncoder}, and {@link ServerCookieEncoder}.
 *
 * @see HttpRequestHeader
 * @see CookieDecoder
 * @see ClientCookieEncoder
 * @see ServerCookieEncoder
 */
public interface HttpResponseHeader extends HttpHeader {

    /**
     * Returns the status of this {@link HttpResponseHeader}.
     *
     * @return The {@link HttpResponseStatus} of this {@link HttpResponseHeader}
     */
    HttpResponseStatus getStatus();

    /**
     * Sets the status of this {@link HttpResponseHeader}
     *
     * @param status The {@link HttpResponseStatus} to use
     */
    void setStatus(HttpResponseStatus status);
}
