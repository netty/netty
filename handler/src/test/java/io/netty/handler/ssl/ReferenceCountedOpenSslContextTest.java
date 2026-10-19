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
package io.netty.handler.ssl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReferenceCountedOpenSslContextTest {

    @Test
    public void testMaxCertificateListBytesFloor() {
        assertEquals(-1, ReferenceCountedOpenSslContext.maxCertificateListBytes(-1));
        assertEquals(0, ReferenceCountedOpenSslContext.maxCertificateListBytes(0));
        assertEquals(16 * 1024, ReferenceCountedOpenSslContext.maxCertificateListBytes(1));
        assertEquals(16 * 1024, ReferenceCountedOpenSslContext.maxCertificateListBytes(16 * 1024));
        assertEquals(16 * 1024 + 1, ReferenceCountedOpenSslContext.maxCertificateListBytes(16 * 1024 + 1));
    }
}
