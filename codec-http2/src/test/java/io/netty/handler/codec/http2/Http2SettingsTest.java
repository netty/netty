/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http2;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Http2Settings}.
 */
public class Http2SettingsTest {

    private Http2Settings settings;

    @BeforeEach
    public void setup() {
        settings = new Http2Settings();
    }

    @Test
    public void standardSettingsShouldBeNotSet() {
        assertEquals(0, settings.size());
        assertNull(settings.headerTableSize());
        assertNull(settings.initialWindowSize());
        assertNull(settings.maxConcurrentStreams());
        assertNull(settings.pushEnabled());
        assertNull(settings.maxFrameSize());
        assertNull(settings.maxHeaderListSize());
    }

    @Test
    public void standardSettingsShouldBeSet() {
        settings.initialWindowSize(1);
        settings.maxConcurrentStreams(2);
        settings.pushEnabled(true);
        settings.headerTableSize(3);
        settings.maxFrameSize(MAX_FRAME_SIZE_UPPER_BOUND);
        settings.maxHeaderListSize(4);
        assertEquals(1, (int) settings.initialWindowSize());
        assertEquals(2L, (long) settings.maxConcurrentStreams());
        assertTrue(settings.pushEnabled());
        assertEquals(3L, (long) settings.headerTableSize());
        assertEquals(MAX_FRAME_SIZE_UPPER_BOUND, (int) settings.maxFrameSize());
        assertEquals(4L, (long) settings.maxHeaderListSize());
    }

    @Test
    public void settingsShouldSupportUnsignedShort() {
        char key = (char) (Short.MAX_VALUE + 1);
        settings.put(key, (Long) 123L);
        assertEquals(123L, (long) settings.get(key));
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_HEADER_LIST_SIZE, MIN_HEADER_LIST_SIZE + 1L,
            MAX_HEADER_LIST_SIZE - 1L, MAX_HEADER_LIST_SIZE})
    public void headerListSize(final long value) {
        settings.maxHeaderListSize(value);
        assertEquals(value, (long) settings.maxHeaderListSize());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, MIN_HEADER_LIST_SIZE - 1L, MAX_HEADER_LIST_SIZE + 1L, MAX_VALUE})
    public void headerListSizeBoundCheck(final long value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.maxHeaderListSize(value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_HEADER_TABLE_SIZE, MIN_HEADER_TABLE_SIZE + 1L,
            MAX_HEADER_TABLE_SIZE - 1L, MAX_HEADER_TABLE_SIZE})
    public void headerTableSize(final long value) {
        settings.headerTableSize(value);
        assertEquals(value, (long) settings.headerTableSize());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, MIN_HEADER_TABLE_SIZE - 1L, MAX_HEADER_TABLE_SIZE + 1L, MAX_VALUE})
    public void headerTableSizeBoundCheck(final long value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.headerTableSize(value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(booleans = {false, true})
    public void pushEnabled(final boolean value) {
        settings.pushEnabled(value);
        assertEquals(value, settings.pushEnabled());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {0, 1})
    public void enablePush(final long value) {
        settings.put(Http2CodecUtil.SETTINGS_ENABLE_PUSH, (Long) value);
        assertEquals(value, (long) settings.get(Http2CodecUtil.SETTINGS_ENABLE_PUSH));
        assertEquals(value == 1, settings.pushEnabled());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, -1, 2, MAX_VALUE})
    public void enablePushBoundCheck(final long value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.put(Http2CodecUtil.SETTINGS_ENABLE_PUSH, (Long) value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_CONCURRENT_STREAMS, MIN_CONCURRENT_STREAMS + 1L,
            MAX_CONCURRENT_STREAMS - 1L, MAX_CONCURRENT_STREAMS})
    public void maxConcurrentStreams(final long value) {
        settings.maxConcurrentStreams(value);
        assertEquals(value, (long) settings.maxConcurrentStreams());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, MIN_CONCURRENT_STREAMS - 1L, MAX_CONCURRENT_STREAMS + 1L, MAX_VALUE})
    public void maxConcurrentStreamsBoundCheck(final long value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.maxConcurrentStreams(value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(ints = {MIN_INITIAL_WINDOW_SIZE, MIN_INITIAL_WINDOW_SIZE + 1,
            MAX_INITIAL_WINDOW_SIZE - 1, MAX_INITIAL_WINDOW_SIZE})
    public void initialWindowSize(final int value) {
        settings.initialWindowSize(value);
        assertEquals(value, (int) settings.initialWindowSize());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(ints = {Integer.MIN_VALUE, MIN_INITIAL_WINDOW_SIZE - 1})
    public void initialWindowSizeIntBoundCheck(final int value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.initialWindowSize(value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, MIN_INITIAL_WINDOW_SIZE - 1L, MAX_INITIAL_WINDOW_SIZE + 1L, MAX_VALUE})
    public void initialWindowSizeBoundCheck(final long value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.put(Http2CodecUtil.SETTINGS_INITIAL_WINDOW_SIZE, (Long) value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(ints = {MAX_FRAME_SIZE_LOWER_BOUND, MAX_FRAME_SIZE_LOWER_BOUND + 1,
            MAX_FRAME_SIZE_UPPER_BOUND - 1, MAX_FRAME_SIZE_UPPER_BOUND})
    public void maxFrameSize(final int value) {
        settings.maxFrameSize(value);
        assertEquals(value, (int) settings.maxFrameSize());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(ints = {Integer.MIN_VALUE, 0, MAX_FRAME_SIZE_LOWER_BOUND - 1,
            MAX_FRAME_SIZE_UPPER_BOUND + 1, Integer.MAX_VALUE})
    public void maxFrameSizeIntBoundCheck(final int value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.maxFrameSize(value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, 0L, MAX_FRAME_SIZE_LOWER_BOUND - 1L, MAX_FRAME_SIZE_UPPER_BOUND + 1L,
            Integer.MAX_VALUE, MAX_VALUE})
    public void maxFrameSizeBoundCheck(final long value) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.put(Http2CodecUtil.SETTINGS_MAX_FRAME_SIZE, (Long) value);
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {0L, 1L, 123L, Integer.MAX_VALUE, MAX_UNSIGNED_INT - 1L, MAX_UNSIGNED_INT})
    public void nonStandardSetting(final long value) {
        char key = 0;
        settings.put(key, (Long) value);
        assertEquals(value, (long) settings.get(key));
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @ValueSource(longs = {MIN_VALUE, Integer.MIN_VALUE, -1L, MAX_UNSIGNED_INT + 1L, MAX_VALUE})
    public void nonStandardSettingBoundCheck(final long value) {
        final char key = 0;
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                settings.put(key, (Long) value);
            }
        });
    }
}
