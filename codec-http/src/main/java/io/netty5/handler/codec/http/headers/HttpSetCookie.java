/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import org.jetbrains.annotations.Nullable;

/**
 * An interface defining a <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie-string</a>.
 */
public interface HttpSetCookie extends HttpCookiePair {
    /**
     * Returns the domain of this {@link HttpSetCookie}.
     *
     * @return The domain of this {@link HttpSetCookie}
     */
    @Nullable
    CharSequence domain();

    /**
     * Returns the path of this {@link HttpSetCookie}.
     *
     * @return The {@link HttpSetCookie}'s path
     */
    @Nullable
    CharSequence path();

    /**
     * Returns the maximum age of this {@link HttpSetCookie} in seconds if specified.
     *
     * @return The maximum age of this {@link HttpSetCookie}. {@code null} if none specified.
     */
    @Nullable
    Long maxAge();

    /**
     * Returns the expire date of this {@link HttpSetCookie} according
     * to <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">Expires</a>.
     *
     * @return the expire date of this {@link HttpSetCookie} according
     * to <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">Expires</a>.
     */
    @Nullable
    CharSequence expires();

    /**
     * Get the value for the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-5.3.7">SameSite attribute</a>.
     * @return The value for the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-5.3.7">SameSite attribute</a>.
     */
    @Nullable
    SameSite sameSite();

    /**
     * Checks to see if this {@link HttpSetCookie} is secure.
     *
     * @return True if this {@link HttpSetCookie} is secure, otherwise false
     */
    boolean isSecure();

    /**
     * Checks to see if this {@link HttpSetCookie} can only be accessed via HTTP.
     * If this returns true, the {@link HttpSetCookie} cannot be accessed through
     * client side script - But only if the browser supports it.
     * For more information, please look <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>
     *
     * @return True if this {@link HttpSetCookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();

    /**
     * Represents
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-4.1.1">samesite-value</a>
     * for the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-5.3.7">SameSite attribute</a>.
     */
    enum SameSite {
        Lax, Strict, None
    }
}
