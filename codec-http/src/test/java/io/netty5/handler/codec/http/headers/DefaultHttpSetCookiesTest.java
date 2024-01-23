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
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.netty5.handler.codec.http.headers.HttpSetCookie.SameSite;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import static io.netty5.handler.codec.http.headers.DefaultHttpSetCookie.parseSetCookie;
import static io.netty5.handler.codec.http.headers.HttpHeaders.newHeaders;
import static io.netty5.util.AsciiString.contentEqualsIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultHttpSetCookiesTest {
    @Test
    void decodeDuplicateNames() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie",
                "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        decodeDuplicateNames(headers);
    }

    private static void decodeDuplicateNames(final HttpHeaders headers) {
        final Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "/", "somecompany.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abcd", "/2", "somecompany2.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void decodeSecureCookieNames() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "__Secure-ID=123; Secure; Domain=example.com");
        decodeSecureCookieNames(headers);
    }

    private static void decodeSecureCookieNames(final HttpHeaders headers) {
        final Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("__Secure-ID");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("__Secure-ID", "123", null, "example.com", null,
                null, null, false, true, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void decodeDifferentCookieNames() {
        final HttpHeaders headers = newHeaders().add("set-cookie",
                "foo=12345; Domain=somecompany.co.uk; Path=/; HttpOnly",
                "bar=abcd; Domain=somecompany.co.uk; Path=/2; Max-Age=3000");
        decodeDifferentCookieNames(headers);
    }

    private static void decodeDifferentCookieNames(final HttpHeaders headers) {
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("foo");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("foo", "12345", "/", "somecompany.co.uk", null,
                null, null, false, false, true), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
        cookieItr = headers.getSetCookiesIterator("bar");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void decodeSameSiteNotAtEnd() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "foo=12345; Domain=somecompany.co.uk; Path=/; SameSite=Lax; HttpOnly");
        headers.add("set-cookie", "bar=abcd; Domain=somecompany.co.uk; Path=/2; SameSite=None; Max-Age=3000");
        headers.add("set-cookie", "baz=xyz; Domain=somecompany.co.uk; Path=/3; SameSite=Strict; Secure");
        validateSameSite(headers);
    }

    @Test
    void decodeSameSiteAtEnd() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "foo=12345; Domain=somecompany.co.uk; Path=/; HttpOnly; SameSite=Lax");
        headers.add("set-cookie", "bar=abcd; Domain=somecompany.co.uk; Path=/2; Max-Age=3000; SameSite=None");
        headers.add("set-cookie", "baz=xyz; Domain=somecompany.co.uk; Path=/3; Secure; SameSite=Strict");
        validateSameSite(headers);
    }

    private static void validateSameSite(HttpHeaders headers) {
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("foo");
        assertTrue(cookieItr.hasNext());
        HttpSetCookie next = cookieItr.next();
        assertTrue(areSetCookiesEqual(new TestSetCookie(
                "foo", "12345", "/", "somecompany.co.uk", null, null, SameSite.Lax, false, false, true), next));
        assertThat(next.encodedSetCookie()).containsIgnoringCase("samesite=lax");
        assertFalse(cookieItr.hasNext());
        cookieItr = headers.getSetCookiesIterator("bar");
        assertTrue(cookieItr.hasNext());
        next = cookieItr.next();
        assertTrue(areSetCookiesEqual(new TestSetCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, SameSite.None, false, false, false), next));
        assertThat(next.encodedSetCookie()).containsIgnoringCase("samesite=none");
        assertFalse(cookieItr.hasNext());
        cookieItr = headers.getSetCookiesIterator("baz");
        assertTrue(cookieItr.hasNext());
        next = cookieItr.next();
        assertTrue(areSetCookiesEqual(new TestSetCookie("baz", "xyz", "/3", "somecompany.co.uk", null,
                null, SameSite.Strict, false, true, false), next));
        assertThat(next.encodedSetCookie()).containsIgnoringCase("samesite=strict");
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void removeSingleCookie() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "foo=12345; Domain=somecompany.co.uk; Path=/; HttpOnly");
        headers.add("set-cookie", "bar=abcd; Domain=somecompany.co.uk; Path=/2; Max-Age=3000");
        assertTrue(headers.removeSetCookies("foo"));
        assertFalse(headers.getSetCookiesIterator("foo").hasNext());
        assertNull(headers.getSetCookie("foo"));
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("bar");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
        final HttpSetCookie barCookie = headers.getSetCookie("bar");
        assertNotNull(barCookie);
        assertTrue(areSetCookiesEqual(new TestSetCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, null, false, false, false), barCookie));
    }

    @Test
    void removeMultipleCookiesSameName() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie",
                "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        assertTrue(headers.removeSetCookies("qwerty"));
        assertFalse(headers.getSetCookiesIterator("qwerty").hasNext());
        assertNull(headers.getSetCookie("qwerty"));
    }

    @Test
    void addMultipleCookiesSameName() {
        final HttpHeaders headers = newHeaders();
        final TestSetCookie c1 = new TestSetCookie("qwerty", "12345", "/",
                "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false);
        headers.addSetCookie(c1);
        final TestSetCookie c2 = new TestSetCookie("qwerty", "abcd", "/2", "somecompany2.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false);
        headers.addSetCookie(c2);
        final HttpSetCookie tmpCookie = headers.getSetCookie("qwerty");
        assertNotNull(tmpCookie);
        assertTrue(areSetCookiesEqual(c1, tmpCookie));
        decodeDuplicateNames(headers);
    }

    @Test
    void addMultipleCookiesDifferentName() {
        final HttpHeaders headers = newHeaders();
        final TestSetCookie fooCookie = new TestSetCookie("foo", "12345", "/", "somecompany.co.uk", null,
                null, null, false, false, true);
        headers.addSetCookie(fooCookie);
        final TestSetCookie barCookie = new TestSetCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, null, false, false, false);
        headers.addSetCookie(barCookie);
        HttpSetCookie tmpCookie = headers.getSetCookie("foo");
        assertNotNull(tmpCookie);
        assertTrue(areSetCookiesEqual(fooCookie, tmpCookie));
        tmpCookie = headers.getSetCookie("bar");
        assertNotNull(tmpCookie);
        assertTrue(areSetCookiesEqual(barCookie, tmpCookie));
    }

    @Test
    void getCookieNameDomainEmptyPath() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=");
        getCookieNameDomainEmptyPath(headers);
    }

    private static void getCookieNameDomainEmptyPath(HttpHeaders headers) {
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty", "somecompany.co.uk", "");
        assertFalse(cookieItr.hasNext());

        cookieItr = headers.getSetCookiesIterator("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "", "somecompany.co.uk", null,
                null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void getAndRemoveCookiesNameDomainPath() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie",
                "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        getCookiesNameDomainPath(headers);

        // Removal now.
        assertTrue(headers.removeSetCookies("qwerty", "somecompany2.co.uk", "/2"));
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty", "somecompany2.co.uk",
                "/2");
        assertFalse(cookieItr.hasNext());

        // Encode again
        cookieItr = headers.getSetCookiesIterator("qwerty", "somecompany.co.uk", "/");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "/", "somecompany.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    private static void getCookiesNameDomainPath(final HttpHeaders headers) {
        final Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty",
                "somecompany2.co.uk", "/2");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abcd", "/2", "somecompany2.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void getAndRemoveCookiesNameSubDomainPath() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=foo.somecompany.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=bar.somecompany.co.uk; Path=/2");
        headers.add("set-cookie", "qwerty=abxy; Domain=somecompany2.co.uk; Path=/2");
        headers.add("set-cookie", "qwerty=xyz; Domain=somecompany.co.uk; Path=/2");

        getAndRemoveCookiesNameSubDomainPath(headers, true);
    }

    private static void getAndRemoveCookiesNameSubDomainPath(HttpHeaders headers, boolean remove) {
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty", "somecompany.co.uk.",
                "/2");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "/2", "foo.somecompany.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abcd", "/2", "bar.somecompany.co.uk", null,
                null, null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "xyz", "/2", "somecompany.co.uk", null,
                null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());

        if (remove) {
            assertTrue(headers.removeSetCookies("qwerty", "somecompany.co.uk.", "/2"));
            cookieItr = headers.getSetCookiesIterator("qwerty");
            assertTrue(cookieItr.hasNext());
            assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abxy", "/2", "somecompany2.co.uk", null,
                    null, null, false, false, false), cookieItr.next()));
            assertFalse(cookieItr.hasNext());
        }
    }

    @Test
    void getAllCookies() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=foo.somecompany.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=bar.somecompany.co.uk; Path=/2");
        headers.add("set-cookie", "qwerty=abxy; Domain=somecompany2.co.uk; Path=/2");
        headers.add("set-cookie", "qwerty=xyz; Domain=somecompany.co.uk; Path=/2");
        headers.add("cookie", "qwerty=xyz");

        getAllCookies(headers);
    }

    private static void getAllCookies(HttpHeaders headers) {
        Iterator<? extends HttpSetCookie> setCookieItr = headers.getSetCookiesIterator();
        assertTrue(setCookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "/2", "foo.somecompany.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false), setCookieItr.next()));
        assertTrue(setCookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abcd", "/2", "bar.somecompany.co.uk",
                null, null, null, false, false, false), setCookieItr.next()));
        assertTrue(setCookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abxy", "/2", "somecompany2.co.uk",
                null, null, null, false, false, false), setCookieItr.next()));
        assertTrue(setCookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "xyz", "/2", "somecompany.co.uk",
                null, null, null, false, false, false), setCookieItr.next()));
        assertFalse(setCookieItr.hasNext());

        Iterator<? extends HttpCookiePair> cookieItr = headers.getCookiesIterator();
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new DefaultHttpCookiePair("qwerty", "xyz", false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void getAndRemoveCookiesNameDomainSubPath() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=somecompany.co.uk; Path=foo/bar; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany.co.uk; Path=/foo/bar/");
        headers.add("set-cookie", "qwerty=xyz; Domain=somecompany.co.uk; Path=/foo/barnot");
        headers.add("set-cookie", "qwerty=abxy; Domain=somecompany.co.uk; Path=/foo/bar/baz");

        getAndRemoveCookiesNameDomainSubPath(headers, true);
    }

    private static void getAndRemoveCookiesNameDomainSubPath(HttpHeaders headers, boolean remove) {
        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty", "somecompany.co.uk",
                "/foo/bar");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "foo/bar",
                        "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT", null, null, false, false, false),
                cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abcd", "/foo/bar/", "somecompany.co.uk", null,
                null, null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "abxy", "/foo/bar/baz", "somecompany.co.uk", null,
                null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());

        if (remove) {
            assertTrue(headers.removeSetCookies("qwerty", "somecompany.co.uk", "/foo/bar"));
            cookieItr = headers.getSetCookiesIterator("qwerty");
            assertTrue(cookieItr.hasNext());
            assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "xyz", "/foo/barnot", "somecompany.co.uk", null,
                    null, null, false, false, false), cookieItr.next()));
            assertFalse(cookieItr.hasNext());
        }
    }

    @Test
    void percentEncodedValueCanBeDecoded() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=%x21%x23");
        percentEncodedValueCanBeDecoded(headers);
    }

    private static void percentEncodedValueCanBeDecoded(HttpHeaders headers) {
        final HttpSetCookie cookie = headers.getSetCookie("qwerty");
        assertNotNull(cookie);
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "%x21%x23", null, null, null,
                null, null, false, false, false), cookie));

        final Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "%x21%x23", null, null, null,
                null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void percentEncodedNameCanBeDecoded() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "%x21=foo");
        percentEncodedNameCanBeDecoded(headers);
    }

    private static void percentEncodedNameCanBeDecoded(HttpHeaders headers) {
        final HttpSetCookie cookie = headers.getSetCookie("%x21");
        assertNotNull(cookie);
        assertTrue(areSetCookiesEqual(new TestSetCookie("%x21", "foo", null, null, null,
                null, null, false, false, false), cookie));

        // Encode now
        final Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("%x21");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("%x21", "foo", null, null, null,
                null, null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void quotedValueWithSpace() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=\"12 345\"");
        Exception e = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("qwerty"));
        assertThat(e)
                .hasMessageContaining("qwerty")
                .hasMessageContaining("unexpected hex value");
    }

    @Test
    void noSemicolonAfterQuotedValue() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=\"12345\" max-age=12");
        Exception e = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("qwerty"));
        assertThat(e)
                .hasMessageContaining("qwerty")
                .hasMessageContaining("expected semicolon");
    }

    @Test
    void quotesInValuePreserved() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=\"12345\"; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        quotesInValuePreserved(headers);
    }

    static void quotesInValuePreserved(HttpHeaders headers) {
        final HttpSetCookie cookie = headers.getSetCookie("qwerty");
        assertNotNull(cookie);
        assertTrue(cookie.isWrapped());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "/",
                "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT", null, null, true, false, false), cookie));

        // Encode again
        final Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", "/", "somecompany.co.uk",
                "Wed, 30 Aug 2019 00:00:00 GMT", null, null, true, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());

        final CharSequence value = headers.get("set-cookie");
        assertNotNull(value);
        assertTrue(value.toString().toLowerCase().contains("qwerty=\"12345\""));
    }

    @Test
    void getCookiesIteratorRemove() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "foo=bar");
        headers.add("set-cookie",
                "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie",
                "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "baz=xxx");

        Iterator<? extends HttpSetCookie> cookieItr = headers.getSetCookiesIterator("qwerty");
        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();
        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();
        assertFalse(cookieItr.hasNext());

        cookieItr = headers.getSetCookiesIterator("qwerty");
        assertFalse(cookieItr.hasNext());

        HttpSetCookie cookie = headers.getSetCookie("foo");
        assertNotNull(cookie);
        assertTrue(areSetCookiesEqual(new TestSetCookie("foo", "bar", null, null, null,
                null, null, false, false, false), cookie));
        cookie = headers.getSetCookie("baz");
        assertNotNull(cookie);
        assertTrue(areSetCookiesEqual(new TestSetCookie("baz", "xxx", null, null, null,
                null, null, false, false, false), cookie));
    }

    @Test
    void overallIteratorRemoveFirstAndLast() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "foo=bar");
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; " +
                "Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; " +
                "Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "baz=xxx");

        // Overall iteration order isn't defined, so track which elements we don't expect to be present after removal.
        final Set<String> removedNameValue = new HashSet<>();

        Iterator<Entry<CharSequence, CharSequence>> cookieItr = headers.iterator();
        // Remove the first and last element
        assertTrue(cookieItr.hasNext());
        Entry<CharSequence, CharSequence> cookie = cookieItr.next();
        removedNameValue.add(calculateOverallRemoveIntermediateSetValue(cookie));
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        removedNameValue.add(calculateOverallRemoveIntermediateSetValue(cookie));
        cookieItr.remove();
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookieItr = headers.iterator();
        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(calculateOverallRemoveIntermediateSetValue(cookie)));

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(calculateOverallRemoveIntermediateSetValue(cookie)));

        assertFalse(cookieItr.hasNext());
    }

    private static String calculateOverallRemoveIntermediateSetValue(Entry<CharSequence, CharSequence> cookie) {
        int i = cookie.getValue().toString().indexOf(';');
        return i >= 0 ? cookie.getValue().subSequence(0, i).toString() : cookie.getValue().toString();
    }

    @Test
    void overallIteratorRemoveMiddle() {
        final HttpHeaders headers = newHeaders();
        headers.add("cookie", "foo=bar");
        headers.add("cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("cookie", "baz=xxx");

        // Overall iteration order isn't defined, so track which elements we don't expect to be present after removal.
        final Set<String> removedNameValue = new HashSet<>();

        Iterator<Entry<CharSequence, CharSequence>> cookieItr = headers.iterator();
        // Remove the first and last element
        assertTrue(cookieItr.hasNext());
        cookieItr.next();

        assertTrue(cookieItr.hasNext());
        Entry<CharSequence, CharSequence> cookie = cookieItr.next();
        removedNameValue.add(calculateOverallRemoveIntermediateSetValue(cookie));
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        removedNameValue.add(calculateOverallRemoveIntermediateSetValue(cookie));
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookieItr = headers.iterator();
        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(calculateOverallRemoveIntermediateSetValue(cookie)));

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(calculateOverallRemoveIntermediateSetValue(cookie)));

        assertFalse(cookieItr.hasNext());
    }

    @Test
    void overallIteratorRemoveAll() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "foo=bar");
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; " +
                "Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; " +
                "Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "baz=xxx");

        Iterator<Entry<CharSequence, CharSequence>> cookieItr = headers.iterator();
        // Remove the first and last element
        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookieItr = headers.iterator();
        assertFalse(cookieItr.hasNext());
    }

    @Test
    void noEqualsButQuotedValueReturnsNull() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty\"12345\"; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        assertNull(headers.getSetCookie("qwerty\"12345\""));
    }

    @Test
    void noEqualsValueWithAttributesReturnsNull() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        assertNull(headers.getSetCookie("qwerty12345"));
    }

    @Test
    void noEqualsValueReturnsNull() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty12345");
        assertNull(headers.getSetCookie("qwerty12345"));
    }

    @Test
    void expiresAfterSemicolon() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie",
                "qwerty=12345; Domain=somecompany.co.uk; Expires=Wed, 30 Aug 2019 00:00:00 GMT; Path=/");
        HttpSetCookie cookie = headers.getSetCookie("qwerty");
        assertNotNull(cookie);
        assertThat(cookie.name()).isEqualToIgnoringCase("qwerty");
        assertThat(cookie.value()).isEqualToIgnoringCase("12345");
        assertThat(cookie.domain()).isEqualToIgnoringCase("somecompany.co.uk");
        assertThat(cookie.expires()).isEqualToIgnoringCase("Wed, 30 Aug 2019 00:00:00 GMT");
        assertThat(cookie.path()).isEqualToIgnoringCase("/");
    }

    @Test
    void emptyValue() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=");
        HttpSetCookie cookie = headers.getSetCookie("qwerty");
        assertNotNull(cookie);
        assertThat(cookie.value()).isEmpty();
    }

    @Test
    void emptyDomain() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=");
        HttpSetCookie cookie = headers.getSetCookie("qwerty");
        assertNotNull(cookie);
        assertThat(cookie.value()).isEqualToIgnoringCase("12345");
        assertThat(cookie.domain()).isEmpty();
    }

    @Test
    void valueContainsEqualsSign() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=123=45");
        HttpSetCookie cookie = headers.getSetCookie("qwerty");
        assertNotNull(cookie);
        assertThat(cookie.value()).isEqualToIgnoringCase("123=45");
    }

    @Test
    void attributeValueContainsEqualsSign() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=12345; max-age=12=1");
        Exception e = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("qwerty"));
        assertThat(e).hasMessageContaining("qwerty").hasMessageContaining("=");
    }

    @Test
    void parseSetCookieWithEmptyName() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> parseSetCookie("=123", false));
        assertThat(e).hasMessageContaining("cookie name cannot be null or empty");

        e = assertThrows(IllegalArgumentException.class, () -> parseSetCookie("; max-age=123", false));
        assertThat(e).hasMessageContaining("cookie name cannot be null or empty");
    }

    @Test
    void parseSetCookieWithEmptyValue() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> parseSetCookie("q", false));
        assertThat(e).hasMessageContaining("set-cookie value not found at index 1");
    }

    @Test
    void parseSetCookieWithUnexpectedQuote() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> parseSetCookie("\"123\"", false));
        assertThat(e).hasMessageContaining("unexpected quote at index: 0");
    }

    @Test
    void trailingSemiColon() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=12345;");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("qwerty"));
        assertThat(e).hasMessageContaining("qwerty").hasMessageContaining(";");
    }

    @Test
    void invalidCookieNameReturnsNull() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "q@werty=12345");
        assertNotNull(headers.getSetCookie("q@werty"));
    }

    @Test
    void invalidCookieNameNoThrowIfNoValidate() {
        final HttpHeaders headers = newHeaders(4, false, false, false);
        headers.add("set-cookie", "q@werty=12345");
        headers.getSetCookie("q@werty");
    }

    @Test
    void valueIteratorThrowsIfNoNextCall() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=12345");
        assertThrows(IllegalStateException.class, () -> valueIteratorThrowsIfNoNextCall(headers));
    }

    private static void valueIteratorThrowsIfNoNextCall(HttpHeaders headers) {
        final Iterator<? extends HttpSetCookie> itr = headers.getSetCookiesIterator("qwerty");
        assertTrue(itr.hasNext());
        itr.remove();
    }

    @Test
    void mustTolerateNoSpaceBeforeCookieAttributeValue() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "first=12345;Extension");
        headers.add("set-cookie", "second=12345;Expires=Mon, 22 Aug 2022 20:12:35 GMT");
        tolerateNoSpaceBeforeCookieAttributeValue(headers);
    }

    private static void tolerateNoSpaceBeforeCookieAttributeValue(HttpHeaders headers) {
        HttpSetCookie first = headers.getSetCookie("first");
        assertEquals("12345", first.value());

        HttpSetCookie second = headers.getSetCookie("second");
        assertEquals("12345", second.value());
        assertEquals("Mon, 22 Aug 2022 20:12:35 GMT", second.expires());
    }

    @Test
    void entryIteratorThrowsIfDoubleRemove() {
        final HttpHeaders headers = newHeaders();
        headers.add("set-cookie", "qwerty=12345");
        assertThrows(IllegalStateException.class, () -> entryIteratorThrowsIfDoubleRemove(headers));
    }

    private static void entryIteratorThrowsIfDoubleRemove(HttpHeaders headers) {
        final Iterator<? extends HttpSetCookie> itr = headers.getSetCookiesIterator("qwerty");
        assertTrue(itr.hasNext());
        assertTrue(areSetCookiesEqual(new TestSetCookie("qwerty", "12345", null, null, null,
                null, null, false, false, false), itr.next()));
        itr.remove();
        itr.remove();
    }

    private static boolean areSetCookiesEqual(final HttpSetCookie cookie1, final HttpSetCookie cookie2) {
        return contentEqualsIgnoreCase(cookie1.name(), cookie2.name()) &&
                cookie1.value().equals(cookie2.value()) &&
                Objects.equals(cookie1.domain(), cookie2.domain()) &&
                Objects.equals(cookie1.path(), cookie2.path()) &&
                Objects.equals(cookie1.expires(), cookie2.expires()) &&
                Objects.equals(cookie1.value(), cookie2.value()) &&
                cookie1.sameSite() == cookie2.sameSite() &&
                cookie1.isHttpOnly() == cookie2.isHttpOnly() &&
                cookie1.isSecure() == cookie2.isSecure() &&
                cookie1.isWrapped() == cookie2.isWrapped() &&
                cookie1.isPartitioned() == cookie2.isPartitioned();
    }

    private static boolean areCookiesEqual(final HttpCookiePair cookie1, final HttpCookiePair cookie2) {
        return contentEqualsIgnoreCase(cookie1.name(), cookie2.name()) &&
                cookie1.value().equals(cookie2.value()) &&
                cookie1.isWrapped() == cookie2.isWrapped();
    }

    private static final class TestSetCookie implements HttpSetCookie {
        private final String name;
        private final String value;
        @Nullable
        private final String path;
        @Nullable
        private final String domain;
        @Nullable
        private final String expires;
        @Nullable
        private final Long maxAge;
        @Nullable
        private final SameSite sameSite;
        private final boolean isWrapped;
        private final boolean isSecure;
        private final boolean isHttpOnly;

        TestSetCookie(final String name, final String value, @Nullable final String path,
                      @Nullable final String domain, @Nullable final String expires,
                      @Nullable final Long maxAge, @Nullable final SameSite sameSite, final boolean isWrapped,
                      final boolean isSecure, final boolean isHttpOnly) {
            this.name = name;
            this.value = value;
            this.path = path;
            this.domain = domain;
            this.expires = expires;
            this.maxAge = maxAge;
            this.sameSite = sameSite;
            this.isWrapped = isWrapped;
            this.isSecure = isSecure;
            this.isHttpOnly = isHttpOnly;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public boolean isWrapped() {
            return isWrapped;
        }

        @Nullable
        @Override
        public String domain() {
            return domain;
        }

        @Nullable
        @Override
        public String path() {
            return path;
        }

        @Nullable
        @Override
        public Long maxAge() {
            return maxAge;
        }

        @Nullable
        @Override
        public String expires() {
            return expires;
        }

        @Nullable
        @Override
        public SameSite sameSite() {
            return sameSite;
        }

        @Override
        public boolean isSecure() {
            return isSecure;
        }

        @Override
        public boolean isHttpOnly() {
            return isHttpOnly;
        }

        @Override
        public boolean isPartitioned() {
            return false;
        }

        @Override
        public CharSequence encodedCookie() {
            return new DefaultHttpSetCookie(name, value, path, domain, expires, maxAge, sameSite, isWrapped, isSecure,
                    isHttpOnly).encodedCookie();
        }

        @Override
        public CharSequence encodedSetCookie() {
            return new DefaultHttpSetCookie(name, value, path, domain, expires, maxAge, sameSite, isWrapped, isSecure,
                    isHttpOnly).encodedSetCookie();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '[' + name + ']';
        }
    }
}
