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
package io.netty.handler.codec.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that exercise {@link HttpVersion} parsing with the JVM-default Locale flipped to a value
 * that exposes the Turkish dotted-I problem (U+0130). {@link Locale#setDefault} is process-global
 * mutable state, so this class is marked {@link Isolated} to keep it from leaking into the rest
 * of the codec-http suite, which runs in concurrent mode
 * ({@code junit.jupiter.execution.parallel.mode.default = concurrent}).
 */
@Isolated("Mutates Locale.getDefault() which is JVM-global state.")
class HttpVersionLocaleTest {

    @Test
    void testLowercaseIotaProtocolNameUnderTurkishLocale() {
        // Turkish locale maps 'i' -> 'İ' (U+0130) under the JVM-default toUpperCase().
        // The constructor must use Locale.US so an HTTP-derived protocol name like "icap"
        // round-trips to the ASCII "ICAP" instead of being corrupted to "İCAP".
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            HttpVersion version = HttpVersion.valueOf("icap/1.0");
            assertEquals("ICAP", version.protocolName());
            for (int i = 0; i < version.protocolName().length(); i++) {
                assertTrue(version.protocolName().charAt(i) < 0x80,
                        "protocolName must remain ASCII regardless of JVM default locale");
            }
            assertEquals("ICAP/1.0", version.text());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void testProtocolNameConstructorUnderTurkishLocale() {
        // Same Locale.US guarantee for the (protocolName, major, minor, ...) constructor.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            HttpVersion version = new HttpVersion("icap", 1, 0, true);
            assertEquals("ICAP", version.protocolName());
            assertEquals("ICAP/1.0", version.text());
        } finally {
            Locale.setDefault(original);
        }
    }
}
