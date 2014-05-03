/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link Http2Settings}.
 */
public class Http2SettingsTest {

    private Http2Settings settings;

    @Before
    public void setup() {
        settings = new Http2Settings();
    }

    @Test
    public void allValuesShouldBeNotSet() {
        assertFalse(settings.hasAllowCompressedData());
        assertFalse(settings.hasMaxHeaderTableSize());
        assertFalse(settings.hasInitialWindowSize());
        assertFalse(settings.hasMaxConcurrentStreams());
        assertFalse(settings.hasPushEnabled());
    }

    @Test(expected = NoSuchElementException.class)
    public void unsetAllowCompressedDataShouldThrow() {
        // Set everything else.
        settings.maxHeaderTableSize(1);
        settings.initialWindowSize(1);
        settings.maxConcurrentStreams(1);
        settings.pushEnabled(true);

        settings.allowCompressedData();
    }

    @Test(expected = NoSuchElementException.class)
    public void unsetHeaderTableSizeShouldThrow() {
        // Set everything else.
        settings.allowCompressedData(true);
        settings.initialWindowSize(1);
        settings.maxConcurrentStreams(1);
        settings.pushEnabled(true);

        settings.maxHeaderTableSize();
    }

    @Test(expected = NoSuchElementException.class)
    public void unsetInitialWindowSizeShouldThrow() {
        // Set everything else.
        settings.allowCompressedData(true);
        settings.maxHeaderTableSize(1);
        settings.maxConcurrentStreams(1);
        settings.pushEnabled(true);

        settings.initialWindowSize();
    }

    @Test(expected = NoSuchElementException.class)
    public void unsetMaxConcurrentStreamsShouldThrow() {
        // Set everything else.
        settings.allowCompressedData(true);
        settings.maxHeaderTableSize(1);
        settings.initialWindowSize(1);
        settings.pushEnabled(true);

        settings.maxConcurrentStreams();
    }

    @Test(expected = NoSuchElementException.class)
    public void unsetPushEnabledShouldThrow() {
        // Set everything else.
        settings.allowCompressedData(true);
        settings.maxHeaderTableSize(1);
        settings.initialWindowSize(1);
        settings.maxConcurrentStreams(1);

        settings.pushEnabled();
    }

    @Test
    public void allowCompressedDataShouldBeSet() {
        settings.allowCompressedData(true);
        assertTrue(settings.hasAllowCompressedData());
        assertTrue(settings.allowCompressedData());
        settings.allowCompressedData(false);
        assertFalse(settings.allowCompressedData());
    }

    @Test
    public void headerTableSizeShouldBeSet() {
        settings.maxHeaderTableSize(123);
        assertTrue(settings.hasMaxHeaderTableSize());
        assertEquals(123, settings.maxHeaderTableSize());
    }

    @Test
    public void initialWindowSizeShouldBeSet() {
        settings.initialWindowSize(123);
        assertTrue(settings.hasInitialWindowSize());
        assertEquals(123, settings.initialWindowSize());
    }

    @Test
    public void maxConcurrentStreamsShouldBeSet() {
        settings.maxConcurrentStreams(123);
        assertTrue(settings.hasMaxConcurrentStreams());
        assertEquals(123, settings.maxConcurrentStreams());
    }

    @Test
    public void pushEnabledShouldBeSet() {
        settings.pushEnabled(true);
        assertTrue(settings.hasPushEnabled());
        assertTrue(settings.pushEnabled());
        settings.pushEnabled(false);
        assertFalse(settings.pushEnabled());
    }
}
