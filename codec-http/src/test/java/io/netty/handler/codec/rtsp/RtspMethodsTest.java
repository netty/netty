/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.rtsp;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@Isolated("valueOfNormalizesLowercaseInputUnderTurkishLocale flips the JVM-default Locale, "
        + "which is process-global state, so the class must not run alongside the rest of the "
        + "codec-http suite (junit.jupiter.execution.parallel.mode.default = concurrent).")
class RtspMethodsTest {

    @Test
    void valueOfReturnsCachedInstanceForUppercaseName() {
        assertSame(RtspMethods.DESCRIBE, RtspMethods.valueOf("DESCRIBE"));
        assertSame(RtspMethods.SETUP, RtspMethods.valueOf("SETUP"));
        assertSame(RtspMethods.GET_PARAMETER, RtspMethods.valueOf("GET_PARAMETER"));
    }

    @Test
    void valueOfNormalizesLowercaseInputUnderUsLocale() {
        assertSame(RtspMethods.DESCRIBE, RtspMethods.valueOf("describe"));
        assertSame(RtspMethods.PLAY, RtspMethods.valueOf("play"));
        assertSame(RtspMethods.REDIRECT, RtspMethods.valueOf("redirect"));
    }

    @Test
    void valueOfNormalizesLowercaseInputUnderTurkishLocale() {
        // In Turkish locale (tr_TR), 'i' uppercases to 'İ' (U+0130) under the JVM default.
        // RtspMethods.valueOf must pin Locale.US so RTSP method names that contain 'i' such as
        // "describe" or "redirect" continue to resolve to the cached uppercase entries.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            HttpMethod describe = RtspMethods.valueOf("describe");
            HttpMethod redirect = RtspMethods.valueOf("redirect");
            assertSame(RtspMethods.DESCRIBE, describe);
            assertSame(RtspMethods.REDIRECT, redirect);
            // Sanity-check: with the locale-default toUpperCase the names would have contained
            // U+0130 instead of plain 'I'. Asserting the resolved names round-trip to ASCII
            // pins down that the fix is taking effect.
            assertEquals("DESCRIBE", describe.name());
            assertEquals("REDIRECT", redirect.name());
        } finally {
            Locale.setDefault(original);
        }
    }
}
