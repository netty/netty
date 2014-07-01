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

import java.util.Set;

/**
 * An interface defining an
 * <a href="http://en.wikipedia.org/wiki/HTTP_cookie">HTTP cookie</a>.
 */
public interface Cookie extends Comparable<Cookie> {

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
     * Returns the comment of this {@link Cookie}.
     *
     * @return The comment of this {@link Cookie}
     */
    String comment();

    /**
     * Sets the comment of this {@link Cookie}.
     *
     * @param comment The comment to use
     */
    void setComment(String comment);

    /**
     * Returns the maximum age of this {@link Cookie} in seconds or {@link Long#MIN_VALUE} if unspecified
     *
     * @return The maximum age of this {@link Cookie}
     */
    long maxAge();

    /**
     * Sets the maximum age of this {@link Cookie} in seconds.
     * If an age of {@code 0} is specified, this {@link Cookie} will be
     * automatically removed by browser because it will expire immediately.
     * If {@link Long#MIN_VALUE} is specified, this {@link Cookie} will be removed when the
     * browser is closed.
     *
     * @param maxAge The maximum age of this {@link Cookie} in seconds
     */
    void setMaxAge(long maxAge);

    /**
     * Returns the version of this {@link Cookie}.
     *
     * @return The version of this {@link Cookie}
     */
    int version();

    /**
     * Sets the version of this {@link Cookie}.
     *
     * @param version The new version to use
     */
    void setVersion(int version);

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
     * For more information, please look <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>
     *
     * @return True if this {@link Cookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();

    /**
     * Determines if this {@link Cookie} is HTTP only.
     * If set to true, this {@link Cookie} cannot be accessed by a client
     * side script. However, this works only if the browser supports it.
     * For for information, please look
     * <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>.
     *
     * @param httpOnly True if the {@link Cookie} is HTTP only, otherwise false.
     */
    void setHttpOnly(boolean httpOnly);

    /**
     * Returns the comment URL of this {@link Cookie}.
     *
     * @return The comment URL of this {@link Cookie}
     */
    String commentUrl();

    /**
     * Sets the comment URL of this {@link Cookie}.
     *
     * @param commentUrl The comment URL to use
     */
    void setCommentUrl(String commentUrl);

    /**
     * Checks to see if this {@link Cookie} is to be discarded by the browser
     * at the end of the current session.
     *
     * @return True if this {@link Cookie} is to be discarded, otherwise false
     */
    boolean isDiscard();

    /**
     * Sets the discard flag of this {@link Cookie}.
     * If set to true, this {@link Cookie} will be discarded by the browser
     * at the end of the current session
     *
     * @param discard True if the {@link Cookie} is to be discarded
     */
    void setDiscard(boolean discard);

    /**
     * Returns the ports that this {@link Cookie} can be accessed on.
     *
     * @return The {@link Set} of ports that this {@link Cookie} can use
     */
    Set<Integer> ports();

    /**
     * Sets the ports that this {@link Cookie} can be accessed on.
     *
     * @param ports The ports that this {@link Cookie} can be accessed on
     */
    void setPorts(int... ports);

    /**
     * Sets the ports that this {@link Cookie} can be accessed on.
     *
     * @param ports The {@link Iterable} collection of ports that this
     *              {@link Cookie} can be accessed on.
     */
    void setPorts(Iterable<Integer> ports);
}
