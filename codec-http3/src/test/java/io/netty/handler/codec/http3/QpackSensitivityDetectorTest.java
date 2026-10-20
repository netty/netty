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
package io.netty.handler.codec.http3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QpackSensitivityDetectorTest {

    @Test
    void neverSensitiveAlwaysReturnsFalse() {
        assertNotNull(QpackSensitivityDetector.NEVER_SENSITIVE);
        assertFalse(QpackSensitivityDetector.NEVER_SENSITIVE.isSensitive("authorization", "Bearer x"));
        assertFalse(QpackSensitivityDetector.NEVER_SENSITIVE.isSensitive("cookie", "sid=abc"));
        assertFalse(QpackSensitivityDetector.NEVER_SENSITIVE.isSensitive("", ""));
    }

    @Test
    void alwaysSensitiveAlwaysReturnsTrue() {
        assertNotNull(QpackSensitivityDetector.ALWAYS_SENSITIVE);
        assertTrue(QpackSensitivityDetector.ALWAYS_SENSITIVE.isSensitive("authorization", "Bearer x"));
        assertTrue(QpackSensitivityDetector.ALWAYS_SENSITIVE.isSensitive(":path", "/"));
        assertTrue(QpackSensitivityDetector.ALWAYS_SENSITIVE.isSensitive("", ""));
    }

    @Test
    void customDetectorFlagsCredentialHeaders() {
        QpackSensitivityDetector detector = (name, value) -> {
            String n = name.toString().toLowerCase();
            return "authorization".equals(n)
                    || "cookie".equals(n)
                    || "set-cookie".equals(n)
                    || "proxy-authorization".equals(n);
        };

        assertTrue(detector.isSensitive("Authorization", "Bearer secret"));
        assertTrue(detector.isSensitive("cookie", "sid=abc"));
        assertTrue(detector.isSensitive("Set-Cookie", "sid=abc; HttpOnly"));
        assertTrue(detector.isSensitive("Proxy-Authorization", "Basic xxx"));

        assertFalse(detector.isSensitive("content-type", "text/plain"));
        assertFalse(detector.isSensitive(":method", "GET"));
    }
}

