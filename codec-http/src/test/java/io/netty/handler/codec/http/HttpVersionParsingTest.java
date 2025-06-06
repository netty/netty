package io.netty.handler.codec.http;

import org.junit.jupiter.api.Test;

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

}
