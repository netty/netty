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
package io.netty.build.jextract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceManifestTest {

    @Test
    void rendersDeterministicContentWithoutTimestamps() {
        final String first = new ProvenanceManifest("22", "macos", "MacOSX14.5").render();
        final String second = new ProvenanceManifest("22", "macos", "MacOSX14.5").render();

        assertEquals(first, second, "the manifest must be byte-stable for identical inputs");
        assertTrue(first.contains("jextract.version=22"), first);
        assertTrue(first.contains("os=macos"), first);
        assertTrue(first.contains("sdk=MacOSX14.5"), first);
        // No date/time token that would defeat the regen-diff gate.
        assertFalse(first.matches("(?s).*\\d{4}-\\d\\d-\\d\\d.*"), first);
    }

    @Test
    void trimsSdkAndOmitsItWhenBlank() {
        assertTrue(new ProvenanceManifest("22", "macos", "  MacOSX14.5  ").render().contains("sdk=MacOSX14.5"));

        final String noSdk = new ProvenanceManifest("22", "linux", null).render();
        assertFalse(noSdk.contains("sdk="), noSdk);
        assertTrue(noSdk.contains("os=linux"), noSdk);

        assertFalse(new ProvenanceManifest("22", "linux", "   ").render().contains("sdk="));
    }

    @Test
    void normalizesOsNames() {
        assertEquals("macos", ProvenanceManifest.normalizeOs("Mac OS X"));
        assertEquals("macos", ProvenanceManifest.normalizeOs("Darwin"));
        assertEquals("linux", ProvenanceManifest.normalizeOs("Linux"));
        assertEquals("windows", ProvenanceManifest.normalizeOs("Windows 11"));
        assertEquals("unknown", ProvenanceManifest.normalizeOs(null));
    }
}
