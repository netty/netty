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
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.netty5.util.AsciiString;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static io.netty5.handler.codec.http.headers.HttpHeaders.newHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultHttpCookiePairTest {

    @Test
    void testEqual() {
        assertThat(new DefaultHttpCookiePair("foo", "bar")).isEqualTo(new DefaultHttpCookiePair("foo", "bar"));
        assertThat(new DefaultHttpCookiePair("foo", "bar")).hasSameHashCodeAs(new DefaultHttpCookiePair("foo", "bar"));

        // comparing String and AsciiString
        assertThat(new DefaultHttpCookiePair("foo", "bar")).isEqualTo(
                new DefaultHttpCookiePair(AsciiString.cached("foo"), AsciiString.cached("bar")));
        assertThat(new DefaultHttpCookiePair("foo", "bar")).hasSameHashCodeAs(
                new DefaultHttpCookiePair(AsciiString.cached("foo"), AsciiString.cached("bar")));

        // isWrapped attribute is ignored:
        assertThat(new DefaultHttpCookiePair("foo", "bar", true)).isEqualTo(
                new DefaultHttpCookiePair("foo", "bar", false));
        assertThat(new DefaultHttpCookiePair("foo", "bar", true)).hasSameHashCodeAs(
                new DefaultHttpCookiePair("foo", "bar", false));
    }

    @Test
    void testNotEqual() {
        // Name is case-sensitive:
        assertThat(
                new DefaultHttpCookiePair("foo", "bar")
        ).isNotEqualTo(
                new DefaultHttpCookiePair("Foo", "bar"));
        assertThat(
                new DefaultHttpCookiePair("foo", "bar").hashCode()
        ).isNotEqualTo(
                new DefaultHttpCookiePair("fooo", "bar").hashCode());

        assertThat(
                new DefaultHttpCookiePair("foo", "bar")
        ).isNotEqualTo(
                new DefaultHttpCookiePair(AsciiString.cached("Foo"), AsciiString.cached("bar")));
        assertThat(
                new DefaultHttpCookiePair("foo", "bar").hashCode()
        ).isNotEqualTo(
                new DefaultHttpCookiePair(AsciiString.cached("fooo"), AsciiString.cached("bar")).hashCode());

        assertThat(
                new DefaultHttpCookiePair("foo", "bar", true)
        ).isNotEqualTo(
                new DefaultHttpCookiePair("foO", "bar", true));
        assertThat(
                new DefaultHttpCookiePair("foo", "bar", true).hashCode()
        ).isNotEqualTo(
                new DefaultHttpCookiePair("fooo", "bar", true).hashCode());

        // Value is case-sensitive:
        assertThat(
                new DefaultHttpCookiePair("foo", "bar")
        ).isNotEqualTo(
                new DefaultHttpCookiePair("foo", "Bar"));
        assertThat(
                new DefaultHttpCookiePair("foo", "bar").hashCode()
        ).isNotEqualTo(
                new DefaultHttpCookiePair("foo", "barr").hashCode());

        assertThat(
                new DefaultHttpCookiePair("foo", "bar", false)
        ).isNotEqualTo(
                new DefaultHttpCookiePair("foo", "baR", false));
        assertThat(
                new DefaultHttpCookiePair("foo", "bar", false)
        ).isNotEqualTo(
                new DefaultHttpCookiePair("foo", "barr", false).hashCode());
    }

    @Test
    void testAddOneInvalidCookie() {
        HttpHeaders headers = newHeaders();
        assertThrows(HeaderValidationException.class, () -> {
            headers.addCookie(new DefaultHttpCookiePair("foo", "value-with-ctrl-char\u0000"));
        });
    }

    @Test
    void testAddTwoCookiesWithLastInvalid() {
        HttpHeaders headers = newHeaders();
        headers.addCookie(new DefaultHttpCookiePair("valid", "foo"));
        assertThrows(HeaderValidationException.class, () -> {
            headers.addCookie(new DefaultHttpCookiePair("invalid", "value-with-ctrl-char\u0000"));
        });
    }

    @Test
    void cookiesWithExtraTrailingSemiColonAndTwoSpaces() {
        final HttpHeaders headers = newHeaders();
        headers.add("cookie", "a=v1; b=v2; lastCookie=v3;  ");
        Exception e = assertThrows(IllegalArgumentException.class, () -> headers.getCookies().forEach(c -> { }));
        assertThat(e).hasMessageContaining("no cookie value found after")
                .hasMessageContaining("lastCookie");
    }

    @Test
    void delimiterAndSpacesButNoCookie() {
        final HttpHeaders headers = newHeaders();
        headers.add("cookie", ";  ");
        Exception e = assertThrows(IllegalArgumentException.class, () -> headers.getCookies().forEach(c -> { }));
        assertThat(e).hasMessageContaining("no cookie value found after")
                .hasMessageContaining("no previous cookie");
    }

    @Test
    void setCookieAsCookie() {
        final HttpHeaders headers = newHeaders();
        // HttpSetCookie also implements HttpCookiePair
        headers.addCookie(new DefaultHttpSetCookie("k", "v", false, true, true));
        Iterator<HttpCookiePair> itr = headers.getCookiesIterator();
        assertThat(itr.hasNext()).isTrue();
        assertThat(itr.next()).isEqualTo(new DefaultHttpCookiePair("k", "v"));
        assertThat(itr.hasNext()).isFalse();
    }
}
