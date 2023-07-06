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
package io.netty.handler.codec.http;

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryStringDecoderTest {

    @Test
    public void testBasicUris() throws URISyntaxException {
        QueryStringDecoder d = assertParameters(new QueryStringDecoder(new URI("http://localhost/path")));
        assertEquals(0, d.parameters().size());
    }

    private static QueryStringDecoder assertParameters(QueryStringDecoder decoder) {
        final Map<String, List<String>> parameters = new LinkedHashMap<String, List<String>>();
        decoder.decodeParameters(parameters);
        assertFalse(decoder.hasDecodedParameters());
        Map<String, List<String>> expectedParameters = decoder.parameters();
        assertTrue(decoder.hasDecodedParameters());
        assertEquals(expectedParameters, parameters);
        // ensure the order for both is the same
        Iterator<Entry<String, List<String>>> expectedIterator = expectedParameters.entrySet().iterator();
        Iterator<Entry<String, List<String>>> iterator = parameters.entrySet().iterator();
        for (int i = 0; i < expectedParameters.size(); i++) {
            Entry<String, List<String>> expectedEntry = expectedIterator.next();
            Entry<String, List<String>> entry = iterator.next();
            assertEquals(expectedEntry.getKey(), entry.getKey(),
                         "Expected parameter name in position " + (i + 1) + " is " + expectedEntry.getKey() +
                         " while it is found " + entry.getKey());
            // assertArray correctly detect ordering
            assertArrayEquals(expectedEntry.getValue().toArray(new String[0]), entry.getValue().toArray(new String[0]),
                              "Paramters values order isn't the same for " + entry.getKey());
        }
        return decoder;
    }

    @Test
    public void testBasic() {
        QueryStringDecoder d;

        d = assertParameters(new QueryStringDecoder("/foo"));
        assertEquals("/foo", d.path());
        assertEquals(0, d.parameters().size());

        d = assertParameters(new QueryStringDecoder("/foo%20bar"));
        assertEquals("/foo bar", d.path());
        assertEquals(0, d.parameters().size());

        d = assertParameters(new QueryStringDecoder("/foo?a=b=c"));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(1, d.parameters().get("a").size());
        assertEquals("b=c", d.parameters().get("a").get(0));

        d = assertParameters(new QueryStringDecoder("/foo?a=1&a=2"));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(2, d.parameters().get("a").size());
        assertEquals("1", d.parameters().get("a").get(0));
        assertEquals("2", d.parameters().get("a").get(1));

        d = assertParameters(new QueryStringDecoder("/foo%20bar?a=1&a=2"));
        assertEquals("/foo bar", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(2, d.parameters().get("a").size());
        assertEquals("1", d.parameters().get("a").get(0));
        assertEquals("2", d.parameters().get("a").get(1));

        d = assertParameters(new QueryStringDecoder("/foo?a=&a=2"));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(2, d.parameters().get("a").size());
        assertEquals("", d.parameters().get("a").get(0));
        assertEquals("2", d.parameters().get("a").get(1));

        d = assertParameters(new QueryStringDecoder("/foo?a=1&a="));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(2, d.parameters().get("a").size());
        assertEquals("1", d.parameters().get("a").get(0));
        assertEquals("", d.parameters().get("a").get(1));

        d = assertParameters(new QueryStringDecoder("/foo?a=1&a=&a="));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(3, d.parameters().get("a").size());
        assertEquals("1", d.parameters().get("a").get(0));
        assertEquals("", d.parameters().get("a").get(1));
        assertEquals("", d.parameters().get("a").get(2));

        d = assertParameters(new QueryStringDecoder("/foo?a=1=&a==2"));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(2, d.parameters().get("a").size());
        assertEquals("1=", d.parameters().get("a").get(0));
        assertEquals("=2", d.parameters().get("a").get(1));

        d = assertParameters(new QueryStringDecoder("/foo?abc=1%2023&abc=124%20"));
        assertEquals("/foo", d.path());
        assertEquals(1, d.parameters().size());
        assertEquals(2, d.parameters().get("abc").size());
        assertEquals("1 23", d.parameters().get("abc").get(0));
        assertEquals("124 ", d.parameters().get("abc").get(1));

        d = assertParameters(new QueryStringDecoder("/foo?abc=%7E"));
        assertEquals("~", d.parameters().get("abc").get(0));
    }

    @Test
    public void testExotic() {
        assertQueryString("", "");
        assertQueryString("foo", "foo");
        assertQueryString("foo", "foo?");
        assertQueryString("/foo", "/foo?");
        assertQueryString("/foo", "/foo");
        assertQueryString("?a=", "?a");
        assertQueryString("foo?a=", "foo?a");
        assertQueryString("/foo?a=", "/foo?a");
        assertQueryString("/foo?a=", "/foo?a&");
        assertQueryString("/foo?a=", "/foo?&a");
        assertQueryString("/foo?a=", "/foo?&a&");
        assertQueryString("/foo?a=", "/foo?&=a");
        assertQueryString("/foo?a=", "/foo?=a&");
        assertQueryString("/foo?a=", "/foo?a=&");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&&c=d");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&=&c=d");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&==&c=d");
        assertQueryString("/foo?a=b&c=&x=y", "/foo?a=b&c&x=y");
        assertQueryString("/foo?a=", "/foo?a=");
        assertQueryString("/foo?a=", "/foo?&a=");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&c=d");
        assertQueryString("/foo?a=1&a=&a=", "/foo?a=1&a&a=");
    }

    @Test
    public void testSemicolon() {
        assertQueryString("/foo?a=1;2", "/foo?a=1;2", false);
        // ";" should be treated as a normal character, see #8855
        assertQueryString("/foo?a=1;2", "/foo?a=1%3B2", true);
    }

    @Test
    public void testPathSpecific() {
        // decode escaped characters
        assertEquals("/foo bar/", new QueryStringDecoder("/foo%20bar/?").path());
        assertEquals("/foo\r\n\\bar/", new QueryStringDecoder("/foo%0D%0A\\bar/?").path());

        // a 'fragment' after '#' should be cuted (see RFC 3986)
        assertEquals("", new QueryStringDecoder("#123").path());
        assertEquals("foo", new QueryStringDecoder("foo?bar#anchor").path());
        assertEquals("/foo-bar", new QueryStringDecoder("/foo-bar#anchor").path());
        assertEquals("/foo-bar", new QueryStringDecoder("/foo-bar#a#b?c=d").path());

        // '+' is not escape ' ' for the path
        assertEquals("+", new QueryStringDecoder("+").path());
        assertEquals("/foo+bar/", new QueryStringDecoder("/foo+bar/?").path());
        assertEquals("/foo++", new QueryStringDecoder("/foo++?index.php").path());
        assertEquals("/foo +", new QueryStringDecoder("/foo%20+?index.php").path());
        assertEquals("/foo+ ", new QueryStringDecoder("/foo+%20").path());
    }

    @Test
    public void testExcludeFragment() {
        // a 'fragment' after '#' should be cuted (see RFC 3986)
        assertEquals("a", assertParameters(new QueryStringDecoder("?a#anchor"))
                .parameters().keySet().iterator().next());
        assertEquals("b", assertParameters(new QueryStringDecoder("?a=b#anchor")).parameters().get("a").get(0));
        assertTrue(assertParameters(new QueryStringDecoder("?#")).parameters().isEmpty());
        assertTrue(assertParameters(new QueryStringDecoder("?#anchor")).parameters().isEmpty());
        assertTrue(assertParameters(new QueryStringDecoder("#?a=b#anchor")).parameters().isEmpty());
        assertTrue(assertParameters(new QueryStringDecoder("?#a=b#anchor")).parameters().isEmpty());
    }

    @Test
    public void testHashDos() {
        StringBuilder buf = new StringBuilder();
        buf.append('?');
        for (int i = 0; i < 65536; i ++) {
            buf.append('k');
            buf.append(i);
            buf.append("=v");
            buf.append(i);
            buf.append('&');
        }
        assertEquals(1024, assertParameters(new QueryStringDecoder(buf.toString())).parameters().size());
    }

    @Test
    public void testHasPath() {
        QueryStringDecoder decoder = assertParameters(new QueryStringDecoder("1=2", false));
        assertEquals("", decoder.path());
        Map<String, List<String>> params = decoder.parameters();
        assertEquals(1, params.size());
        assertTrue(params.containsKey("1"));
        List<String> param = params.get("1");
        assertNotNull(param);
        assertEquals(1, param.size());
        assertEquals("2", param.get(0));
    }

    @Test
    public void testUrlDecoding() throws Exception {
        final String caffe = new String(
                // "Caffé" but instead of putting the literal E-acute in the
                // source file, we directly use the UTF-8 encoding so as to
                // not rely on the platform's default encoding (not portable).
                new byte[] {'C', 'a', 'f', 'f', (byte) 0xC3, (byte) 0xA9},
                "UTF-8");
        final String[] tests = {
            // Encoded   ->   Decoded or error message substring
            "",               "",
            "foo",            "foo",
            "f+o",            "f o",
            "f++",            "f  ",
            "fo%",            "unterminated escape sequence at index 2 of: fo%",
            "%42",            "B",
            "%5f",            "_",
            "f%4",            "unterminated escape sequence at index 1 of: f%4",
            "%x2",            "invalid hex byte 'x2' at index 1 of '%x2'",
            "%4x",            "invalid hex byte '4x' at index 1 of '%4x'",
            "Caff%C3%A9",     caffe,
            "случайный праздник",               "случайный праздник",
            "случайный%20праздник",             "случайный праздник",
            "случайный%20праздник%20%E2%98%BA", "случайный праздник ☺",
        };
        for (int i = 0; i < tests.length; i += 2) {
            final String encoded = tests[i];
            final String expected = tests[i + 1];
            try {
                final String decoded = QueryStringDecoder.decodeComponent(encoded);
                assertEquals(expected, decoded);
            } catch (IllegalArgumentException e) {
                assertEquals(expected, e.getMessage());
            }
        }
    }

    private static void assertQueryString(String expected, String actual) {
        assertQueryString(expected, actual, false);
    }

    private static void assertQueryString(String expected, String actual, boolean semicolonIsNormalChar) {
        QueryStringDecoder ed = new QueryStringDecoder(expected, CharsetUtil.UTF_8, true,
                1024, semicolonIsNormalChar);
        QueryStringDecoder ad = new QueryStringDecoder(actual, CharsetUtil.UTF_8, true,
                1024, semicolonIsNormalChar);
        // ensure the new methods using PrameterConsumer to behave correctly
        assertParameters(ed);
        assertParameters(ad);
        LegacyQueryStringDecoder edLegacy = new LegacyQueryStringDecoder(expected, CharsetUtil.UTF_8, true,
                                                       1024, semicolonIsNormalChar);
        LegacyQueryStringDecoder adLegacy = new LegacyQueryStringDecoder(actual, CharsetUtil.UTF_8, true,
                                                       1024, semicolonIsNormalChar);
        // ensure same behaviour to legacy
        assertEquals(edLegacy.path(), ed.path(), "Path regression tests vs legacy failed");
        assertEquals(adLegacy.path(), ad.path(), "Path regression tests vs legacy failed");
        assertEquals(edLegacy.parameters(), ed.parameters(), "Parameters regression tests vs legacy failed");
        assertEquals(adLegacy.parameters(), ad.parameters(), "Parameters regression tests vs legacy failed");
        // ensure consistency between ed/ad: it should be trivially true in case legacy is well-behaving
        assertEquals(ed.path(), ad.path());
        assertEquals(ed.parameters(), ad.parameters());
    }

    // See #189
    @Test
    public void testURI() {
        URI uri = URI.create("http://localhost:8080/foo?param1=value1&param2=value2&param3=value3");
        QueryStringDecoder decoder = assertParameters(new QueryStringDecoder(uri));
        assertEquals("/foo", decoder.path());
        assertEquals("/foo", decoder.rawPath());
        assertEquals("param1=value1&param2=value2&param3=value3", decoder.rawQuery());
        Map<String, List<String>> params =  decoder.parameters();
        assertEquals(3, params.size());
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator();

        Entry<String, List<String>> entry = entries.next();
        assertEquals("param1", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value1", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("param2", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value2", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("param3", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value3", entry.getValue().get(0));

        assertFalse(entries.hasNext());
    }

    // See #189
    @Test
    public void testURISlashPath() {
        URI uri = URI.create("http://localhost:8080/?param1=value1&param2=value2&param3=value3");
        QueryStringDecoder decoder = assertParameters(new QueryStringDecoder(uri));
        assertEquals("/", decoder.path());
        assertEquals("/", decoder.rawPath());
        assertEquals("param1=value1&param2=value2&param3=value3", decoder.rawQuery());

        Map<String, List<String>> params =  decoder.parameters();
        assertEquals(3, params.size());
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator();

        Entry<String, List<String>> entry = entries.next();
        assertEquals("param1", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value1", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("param2", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value2", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("param3", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value3", entry.getValue().get(0));

        assertFalse(entries.hasNext());
    }

    // See #189
    @Test
    public void testURINoPath() {
        URI uri = URI.create("http://localhost:8080?param1=value1&param2=value2&param3=value3");
        QueryStringDecoder decoder = assertParameters(new QueryStringDecoder(uri));
        assertEquals("", decoder.path());
        assertEquals("", decoder.rawPath());
        assertEquals("param1=value1&param2=value2&param3=value3", decoder.rawQuery());

        Map<String, List<String>> params =  decoder.parameters();
        assertEquals(3, params.size());
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator();

        Entry<String, List<String>> entry = entries.next();
        assertEquals("param1", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value1", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("param2", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value2", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("param3", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("value3", entry.getValue().get(0));

        assertFalse(entries.hasNext());
    }

    // See https://github.com/netty/netty/issues/1833
    @Test
    public void testURI2() {
        URI uri = URI.create("http://foo.com/images;num=10?query=name;value=123");
        QueryStringDecoder decoder = assertParameters(new QueryStringDecoder(uri));
        assertEquals("/images;num=10", decoder.path());
        assertEquals("/images;num=10", decoder.rawPath());
        assertEquals("query=name;value=123", decoder.rawQuery());

        Map<String, List<String>> params =  decoder.parameters();
        assertEquals(2, params.size());
        Iterator<Entry<String, List<String>>> entries = params.entrySet().iterator();

        Entry<String, List<String>> entry = entries.next();
        assertEquals("query", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("name", entry.getValue().get(0));

        entry = entries.next();
        assertEquals("value", entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals("123", entry.getValue().get(0));

        assertFalse(entries.hasNext());
    }

    @Test
    public void testEmptyStrings() {
        QueryStringDecoder pathSlash = new QueryStringDecoder("path/");
        assertEquals("path/", pathSlash.rawPath());
        assertEquals("", pathSlash.rawQuery());
        QueryStringDecoder pathQuestion = new QueryStringDecoder("path?");
        assertEquals("path", pathQuestion.rawPath());
        assertEquals("", pathQuestion.rawQuery());
        QueryStringDecoder empty = new QueryStringDecoder("");
        assertEquals("", empty.rawPath());
        assertEquals("", empty.rawQuery());
    }
}
