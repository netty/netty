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
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

/**
 * Interface defining a HTTP <a href="https://tools.ietf.org/html/rfc6265#section-4.2.1">cookie-pair</a>.
 */
public interface HttpCookiePair {
    /**
     * Returns the name of this {@link HttpCookiePair}.
     *
     * @return The name of this {@link HttpCookiePair}
     */
    CharSequence name();

    /**
     * Returns the value of this {@link HttpCookiePair}.
     *
     * @return The value of this {@link HttpCookiePair}
     */
    CharSequence value();

    /**
     * Returns {@code true} if the value should be wrapped in DQUOTE as described in
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     *
     * @return {@code true} if the value should be wrapped in DQUOTE as described in
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     */
    boolean isWrapped();

    /**
     * Get the encoded value of this {@link HttpCookiePair} for the {@code Cookie} HTTP header.
     *
     * @return the encoded value of this {@link HttpCookiePair}.
     */
    CharSequence encodedCookie();
}
