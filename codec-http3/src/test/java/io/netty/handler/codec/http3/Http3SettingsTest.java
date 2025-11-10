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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http3;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Spliterator;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * unit tests for {@link Http3Settings}.
 */
public class Http3SettingsTest {

    @Test
    void testDefaultSettingsValues() {
        Http3Settings settings = Http3Settings.defaultSettings();

        assertEquals(0L, settings.qpackMaxTableCapacity());
        assertEquals(0L, settings.qpackBlockedStreams());
        assertEquals(Boolean.FALSE, settings.connectProtocolEnabled());
        System.out.println(settings.qpackMaxTableCapacity());
        assertNull(settings.maxFieldSectionSize());
    }

    @Test
    void testPutAndGetSettings() {
        Http3Settings settings = new Http3Settings();

        settings.qpackMaxTableCapacity(128)
                .qpackBlockedStreams(4)
                .enableConnectProtocol(true)
                .maxFieldSectionSize(4096);

        assertEquals(128L, settings.qpackMaxTableCapacity());
        assertEquals(4L, settings.qpackBlockedStreams());
        assertEquals(Boolean.TRUE, settings.connectProtocolEnabled());
        assertEquals(4096L, settings.maxFieldSectionSize());
    }

    @Test
    void testEqualsAndHashCode() {
        Http3Settings s1 = new Http3Settings()
                .qpackMaxTableCapacity(256)
                .qpackBlockedStreams(8)
                .enableConnectProtocol(true);

        Http3Settings s2 = new Http3Settings()
                .qpackMaxTableCapacity(256)
                .qpackBlockedStreams(8)
                .enableConnectProtocol(true);

        Http3Settings s3 = new Http3Settings().qpackMaxTableCapacity(999);

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1, s3);
        assertNotEquals(null, s1);
        assertNotEquals("not-a-settings", s1);
    }

    @Test
    void testIteratorProvidesEntries() {
        Http3Settings settings = new Http3Settings()
                .qpackMaxTableCapacity(100)
                .qpackBlockedStreams(2);

        AtomicInteger count = new AtomicInteger();
        for (Map.Entry<Long, Long> e : settings) {
            assertNotNull(e.getKey());
            assertNotNull(e.getValue());
            count.incrementAndGet();
        }
        assertTrue(count.get() >= 2);
    }

    @Test
    void testForEachConsumer() {
        Http3Settings settings = new Http3Settings()
                .qpackMaxTableCapacity(5)
                .qpackBlockedStreams(7);

        List<Long> keys = new ArrayList<>();
        settings.forEach(entry -> keys.add(entry.getKey()));
        assertTrue(keys.contains(Http3Settings.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY));
        assertTrue(keys.contains(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS));
    }

    @Test
    void testSpliterator() {
        Http3Settings settings = new Http3Settings()
                .qpackMaxTableCapacity(1)
                .qpackBlockedStreams(2);
        Spliterator<Map.Entry<Long, Long>> spliterator = settings.spliterator();
        assertNotNull(spliterator);
        assertTrue(spliterator.estimateSize() > 0);
    }

    @Test
    void testCopyFrom() {
        Http3Settings src = new Http3Settings()
                .qpackMaxTableCapacity(512)
                .enableConnectProtocol(true)
                .qpackBlockedStreams(9);
        Http3Settings dst = new Http3Settings();
        dst.putAll(src);
        assertEquals(src, dst);

        src.qpackMaxTableCapacity(100);
        assertNotEquals(src, dst);
    }

    @Test
    void testValidationRejectsInvalidValues() {
        Http3Settings settings = new Http3Settings();

        // Negative value tests
        assertThrows(IllegalArgumentException.class,
                () -> settings.qpackBlockedStreams(-1));
        assertThrows(IllegalArgumentException.class,
                () -> settings.qpackMaxTableCapacity(-10));
        assertThrows(IllegalArgumentException.class,
                () -> settings.maxFieldSectionSize(-5));

        // Invalid ENABLE_CONNECT_PROTOCOL (must be 0 or 1)
        assertThrows(IllegalArgumentException.class,
                () -> settings.put(Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, 5L));

        // Non-standard but negative key
        assertThrows(IllegalArgumentException.class,
                () -> settings.put(0x9999, -99L));
    }

    @Test
    void testVerifyStandardSettingValidCases() {
        Http3Settings settings = new Http3Settings();
        settings.put(Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, 1L);
        settings.put(Http3Settings.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0L);
        settings.put(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 5L);
        settings.put(Http3Settings.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, 4096L);

        assertEquals(Boolean.TRUE, settings.connectProtocolEnabled());
        assertEquals(4096L, settings.maxFieldSectionSize());
    }

    @Test
    void testCustomSettingsAllowed() {
        Http3Settings settings = new Http3Settings();
        long customKey = 0xdeadbeefL;
        settings.put(customKey, 123L);
        assertEquals(123L, settings.get(customKey));
    }

    @Test
    void testDefaultSettingsBuilderPattern() {
        Http3Settings s = new Http3Settings()
                .qpackMaxTableCapacity(10)
                .enableConnectProtocol(false)
                .qpackBlockedStreams(1)
                .maxFieldSectionSize(100);

        assertEquals(10L, s.qpackMaxTableCapacity());
        assertEquals(Boolean.FALSE, s.connectProtocolEnabled());
        assertEquals(1L, s.qpackBlockedStreams());
        assertEquals(100L, s.maxFieldSectionSize());
    }

    @Test
    void testToStringAndKeyToStringIndirectly() {
        Http3Settings settings = new Http3Settings()
                .qpackMaxTableCapacity(1)
                .qpackBlockedStreams(2)
                .enableConnectProtocol(true);

        String out = settings.toString();

        assertTrue(out.contains("0x1"));
        assertTrue(out.contains("0x7"));
        assertTrue(out.contains("0x8"));
    }


    @Test
    void testEmptySettingsEquality() {
        Http3Settings s1 = new Http3Settings();
        Http3Settings s2 = new Http3Settings();
        assertEquals(s1, s2);
    }

    @Test
    void testDefaultSettingsStaticFactoryCreatesNewInstance() {
        Http3Settings s1 = Http3Settings.defaultSettings();
        Http3Settings s2 = Http3Settings.defaultSettings();
        assertNotSame(s1, s2);
        assertEquals(s1, s2);
    }

    @Test
    void testEnableConnectProtocolNullBehaviour() {
        Http3Settings settings = new Http3Settings();
        assertNull(settings.connectProtocolEnabled());
        settings.enableConnectProtocol(true);
        assertEquals(Boolean.TRUE, settings.connectProtocolEnabled());
        settings.enableConnectProtocol(false);
        assertNotEquals(Boolean.TRUE, settings.connectProtocolEnabled());
    }

    @Test
    void testIteratorEmptyDoesNotFail() {
        Http3Settings settings = new Http3Settings();
        Iterator<Map.Entry<Long, Long>> iterator = settings.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    void testPutOverridesExistingKey() {
        Http3Settings settings = new Http3Settings();
        settings.qpackMaxTableCapacity(10);
        settings.put(Http3Settings.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 20L);
        assertEquals(20L, settings.qpackMaxTableCapacity());
    }

    @Test
    void testNullValueRejected() {
        Http3Settings settings = new Http3Settings();
        assertThrows(NullPointerException.class, () -> settings.put(1, null));
    }

    @Test
    void testUnknownCustomSettingPositiveValueAllowed() {
        Http3Settings settings = new Http3Settings();
        assertDoesNotThrow(() -> settings.put(0xABCD, 1L));
        assertEquals(1L, settings.get(0xABCD));
    }

    @Test
    void testCopyFromNullThrows() {
        Http3Settings settings = new Http3Settings();
        assertThrows(NullPointerException.class, () -> settings.putAll(null));
    }
}
