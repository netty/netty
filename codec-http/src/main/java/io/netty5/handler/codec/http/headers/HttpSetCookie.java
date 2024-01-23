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
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import io.netty5.handler.codec.DateFormatter;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

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
     * Return a {@link #maxAge()} value that is computed from the {@link #expires()} attribute,
     * or {@code null} if no expires attribute has been specified.
     * <p>
     * The reference for computing the max age, is the current time when this function is called.
     * The computed time is not cached, so multiple calls may return different computed max-age values, as the
     * moment of expiration approaches.
     *
     * @return The maximum age of this {@link HttpSetCookie}, but computed from the {@link #expires()} attribute.
     * Or {@code null} if no expires attribute is specified.
     */
    @Nullable
    default Long expiresAsMaxAge() {
        CharSequence expires = expires();
        if (expires != null) {
            Date expiresDate = DateFormatter.parseHttpDate(expires);
            if (expiresDate != null) {
                long maxAgeMillis = expiresDate.getTime() - System.currentTimeMillis();
                return maxAgeMillis / 1000 + (maxAgeMillis % 1000 != 0 ? 1 : 0);
            }
        }
        return null;
    }

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
     * For more information, please look <a href="https://www.owasp.org/index.php/HTTPOnly">here</a>
     *
     * @return True if this {@link HttpSetCookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();

    /**
     * Get the encoded value of this {@link HttpSetCookie} for the {@code Set-Cookie} HTTP header.
     *
     * @return the encoded value of this {@link HttpSetCookie}.
     */
    CharSequence encodedSetCookie();

    /**
     * Represents
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-4.1.1">samesite-value</a>
     * for the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-5.3.7">SameSite attribute</a>.
     */
    enum SameSite {
        Lax, Strict, None
    }

    /**
     * Checks to see if this {@link HttpSetCookie} is partitioned
     *
     * @return True if this {@link HttpSetCookie} is partitioned, otherwise false
     */
    boolean isPartitioned();
}
