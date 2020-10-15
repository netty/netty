/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http.cookie;

/**
 * An interface defining an
 * <a href="http://en.wikipedia.org/wiki/HTTP_cookie">HTTP cookie</a>.
 */
public interface Cookie extends Comparable<Cookie> {

    /**
     * Constant for undefined MaxAge attribute value.
     */
    long UNDEFINED_MAX_AGE = Long.MIN_VALUE;

    /**
     * Returns the name of this {@link Cookie}.
     *
     * @return The name of this {@link Cookie}
     */
    String name();

    /**
     * Returns the value of this {@link Cookie}.
     *
     * @return The value of this {@link Cookie}
     */
    String value();

    /**
     * Sets the value of this {@link Cookie}.
     *
     * @param value The value to set
     */
    void setValue(String value);

    /**
     * Returns true if the raw value of this {@link Cookie},
     * was wrapped with double quotes in original Set-Cookie header.
     *
     * @return If the value of this {@link Cookie} is to be wrapped
     */
    boolean wrap();

    /**
     * Sets true if the value of this {@link Cookie}
     * is to be wrapped with double quotes.
     *
     * @param wrap true if wrap
     */
    void setWrap(boolean wrap);

    /**
     * Returns the domain of this {@link Cookie}.
     *
     * @return The domain of this {@link Cookie}
     */
    String domain();

    /**
     * Sets the domain of this {@link Cookie}.
     *
     * @param domain The domain to use
     */
    void setDomain(String domain);

    /**
     * Returns the path of this {@link Cookie}.
     *
     * @return The {@link Cookie}'s path
     */
    String path();

    /**
     * Sets the path of this {@link Cookie}.
     *
     * @param path The path to use for this {@link Cookie}
     */
    void setPath(String path);

    /**
     * Returns the maximum age of this {@link Cookie} in seconds or {@link Cookie#UNDEFINED_MAX_AGE} if unspecified
     *
     * @return The maximum age of this {@link Cookie}
     */
    long maxAge();

    /**
     * Sets the maximum age of this {@link Cookie} in seconds.
     * If an age of {@code 0} is specified, this {@link Cookie} will be
     * automatically removed by browser because it will expire immediately.
     * If {@link Cookie#UNDEFINED_MAX_AGE} is specified, this {@link Cookie} will be removed when the
     * browser is closed.
     *
     * @param maxAge The maximum age of this {@link Cookie} in seconds
     */
    void setMaxAge(long maxAge);

    /**
     * Checks to see if this {@link Cookie} is secure
     *
     * @return True if this {@link Cookie} is secure, otherwise false
     */
    boolean isSecure();

    /**
     * Sets the security getStatus of this {@link Cookie}
     *
     * @param secure True if this {@link Cookie} is to be secure, otherwise false
     */
    void setSecure(boolean secure);

    /**
     * Checks to see if this {@link Cookie} can only be accessed via HTTP.
     * If this returns true, the {@link Cookie} cannot be accessed through
     * client side script - But only if the browser supports it.
     * For more information, please look <a href="https://owasp.org/www-community/HttpOnly">here</a>
     *
     * @return True if this {@link Cookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();

    /**
     * Determines if this {@link Cookie} is HTTP only.
     * If set to true, this {@link Cookie} cannot be accessed by a client
     * side script. However, this works only if the browser supports it.
     * For for information, please look
     * <a href="https://owasp.org/www-community/HttpOnly">here</a>.
     *
     * @param httpOnly True if the {@link Cookie} is HTTP only, otherwise false.
     */
    void setHttpOnly(boolean httpOnly);
}
