/*
 * Copyright 2025 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpVersionParsingTest {

    @Test
    void testStandardVersions() {
        HttpVersion v10 = HttpVersion.valueOf("HTTP/1.0");
        HttpVersion v11 = HttpVersion.valueOf("HTTP/1.1");

        assertSame(HttpVersion.HTTP_1_0, v10);
        assertSame(HttpVersion.HTTP_1_1, v11);

        assertEquals("HTTP", v10.protocolName());
        assertEquals(1, v10.majorVersion());
        assertEquals(0, v10.minorVersion());

        assertEquals("HTTP", v11.protocolName());
        assertEquals(1, v11.majorVersion());
        assertEquals(1, v11.minorVersion());
    }

    @Test
    void testLowerCaseProtocolNameNonStrict() {
        HttpVersion version = HttpVersion.valueOf("http/1.1");
        assertEquals("HTTP", version.protocolName());
        assertEquals(1, version.majorVersion());
        assertEquals(1, version.minorVersion());
        assertEquals("HTTP/1.1", version.text());
    }

    @Test
    void testMixedCaseProtocolNameNonStrict() {
        HttpVersion version = HttpVersion.valueOf("hTtP/1.0");
        assertEquals("HTTP", version.protocolName());
        assertEquals(1, version.majorVersion());
        assertEquals(0, version.minorVersion());
        assertEquals("HTTP/1.0", version.text());
    }

    @Test
    void testCustomLowerCaseProtocolNonStrict() {
        HttpVersion version = HttpVersion.valueOf("mqtt/5.0");
        assertEquals("MQTT", version.protocolName());
        assertEquals(5, version.majorVersion());
        assertEquals(0, version.minorVersion());
        assertEquals("MQTT/5.0", version.text());
    }

    @Test
    void testCustomVersionNonStrict() {
        HttpVersion version = HttpVersion.valueOf("MyProto/2.3");
        assertEquals("MYPROTO", version.protocolName()); // uppercased
        assertEquals(2, version.majorVersion());
        assertEquals(3, version.minorVersion());
        assertEquals("MYPROTO/2.3", version.text());
    }

    @Test
    void testCustomVersionStrict() {
        HttpVersion version = new HttpVersion("HTTP/1.1", true, true);
        assertEquals("HTTP", version.protocolName());
        assertEquals(1, version.majorVersion());
        assertEquals(1, version.minorVersion());
    }

    @Test
    void testCustomVersionStrictFailsOnLongVersion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new HttpVersion("HTTP/10.1", true, true)
        );
        assertTrue(ex.getMessage().contains("invalid version format"));
    }

    @Test
    void testInvalidFormatMissingSlash() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpVersion.valueOf("HTTP1.1")
        );
    }

    @Test
    void testInvalidFormatWhitespaceInProtocol() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpVersion.valueOf("HT TP/1.1")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "HTTP ",
            " HTTP",
            "H TTP",
            " HTTP ",
            "HTTP\r",
            "HTTP\n",
            "HTTP\r\n",
            "HTT\rP",
            "HTT\nP",
            "HTT\r\nP",
            "\rHTTP",
            "\nHTTP",
            "\r\nHTTP",
            " \r\nHTTP",
            "\r \nHTTP",
            "\r\n HTTP",
            "\r\nHTTP ",
            "\nHTTP ",
            "\rHTTP ",
            "\r HTTP",
            " \rHTTP",
            "\nHTTP ",
            "\n HTTP",
            " \nHTTP",
            "HTTP \n",
            "HTTP \r",
            " HTTP\r",
            " HTTP\r",
            "HTTP \n",
            " HTTP\n",
            " HTTP\n",
            "HTT\nTP",
            "HTT\rTP",
            " HTT\rP",
            " HTT\rP",
            "HTT\nTP",
            " HTT\nP",
            " HTT\nP",
    })
    void httpVersionMustRejectIllegalTokens(String protocol) {
        try {
            HttpVersion httpVersion = new HttpVersion(protocol, 1, 0, true);
            // If no exception is thrown, then the version must have been sanitized and made safe.
            assertTrue(HttpUtil.isEncodingSafeStartLineToken(httpVersion.text()));
        } catch (IllegalArgumentException ignore) {
            // Throwing is good.
        }
    }
}
