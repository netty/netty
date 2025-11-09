package io.netty.handler.codec.http3;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultHttp3SettingsFrameTest {

    @Test
    void testDefaultFrameHasEmptySettings() {
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame();

        assertNotNull(frame.settings());
        assertFalse(frame.iterator().hasNext());
        assertNull(frame.get(Http3Settings.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY));
    }

    @Test
    void testPutAndGetDelegatesToSettings() {
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame();

        frame.put(Http3Settings.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 256L);
        frame.put(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 8L);

        assertEquals(256L, frame.settings().qpackMaxTableCapacity());
        assertEquals(8L, frame.settings().qpackBlockedStreams());
        assertEquals(8L, frame.get(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS));
    }

    @Test
    void testPutRejectsReservedHttp2Settings() {
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame();
        assertThrows(IllegalArgumentException.class, () -> frame.put(0x4, 10L)); // HTTP/2 reserved key
    }

    @Test
    void testSettingsReferenceIsRetained() {
        Http3Settings settings = new Http3Settings().qpackMaxTableCapacity(512);
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame(settings);

        assertSame(settings, frame.settings());
        assertEquals(512L, frame.settings().qpackMaxTableCapacity());

        // Modify settings externally and verify frame sees update
        settings.qpackBlockedStreams(3);
        assertEquals(3L, frame.get(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS));
    }

    @Test
    void testEqualsAndHashCodeCompareSettings() {
        Http3Settings s1 = new Http3Settings().qpackMaxTableCapacity(128);
        Http3Settings s2 = new Http3Settings().qpackMaxTableCapacity(128);

        DefaultHttp3SettingsFrame f1 = new DefaultHttp3SettingsFrame(s1);
        DefaultHttp3SettingsFrame f2 = new DefaultHttp3SettingsFrame(s2);

        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    void testIteratorReflectsSettingsEntries() {
        Http3Settings settings = new Http3Settings()
                .qpackMaxTableCapacity(64)
                .qpackBlockedStreams(2);
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame(settings);

        Iterator<Map.Entry<Long, Long>> it = frame.iterator();
        int count = 0;
        while (it.hasNext()) {
            Map.Entry<Long, Long> entry = it.next();
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            count++;
        }
        assertTrue(count >= 2);
    }

    @Test
    void testToStringIncludesSettings() {
        Http3Settings settings = new Http3Settings()
                .qpackMaxTableCapacity(1)
                .enableConnectProtocol(true);

        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame(settings);
        String output = frame.toString();

        assertTrue(output.contains("Http3Settings"));
        assertTrue(output.contains("0x1"));
        assertTrue(output.contains("settings="));
    }

    @Test
    void testCopyOfCreatesDeepCopy() {
        DefaultHttp3SettingsFrame original = new DefaultHttp3SettingsFrame(
                new Http3Settings().qpackMaxTableCapacity(10).enableConnectProtocol(true));

        DefaultHttp3SettingsFrame copy = DefaultHttp3SettingsFrame.copyOf(original);

        assertNotSame(original, copy);
        assertEquals(original, copy);

        // Modify original and ensure copy remains unchanged
        original.put(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 5L);
        assertNotEquals(original, copy);
    }

    @Test
    void testDeprecatedMethodsStillWork() {
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame();
        frame.put(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 5L);

        // Uses old get() API
        assertEquals(5L, frame.get(Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS));

        // New typed accessor matches
        assertEquals(5L, frame.settings().qpackBlockedStreams());
    }

    @Test
    void testDuplicateKeyInSettingsFrameTriggersError() {
        DefaultHttp3SettingsFrame frame = new DefaultHttp3SettingsFrame();

        frame.put(0x1, 10L);
        assertThrows(IllegalStateException.class, () -> {
            // Simulate protocol behavior
            if (frame.put(0x1, 20L) != null) {
                throw new IllegalStateException("Duplicate SETTINGS key detected");
            }
        });
    }

}
