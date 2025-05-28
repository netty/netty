/*
 * Copyright 2017 The Netty Project
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenSslTest {

    @Test
    void testDefaultCiphers() {
        if (!OpenSsl.isTlsv13Supported()) {
            assertTrue(
                    OpenSsl.DEFAULT_CIPHERS.size() <= SslUtils.DEFAULT_CIPHER_SUITES.length);
        }
    }

    @Test
    void availableJavaCipherSuitesMustNotContainNullOrEmptyElement() {
        for (String suite :OpenSsl.availableJavaCipherSuites()) {
            assertNotNull(suite);
            assertFalse(suite.isEmpty());
        }
    }

    @Test
    void availableOpenSslCipherSuitesMustNotContainNullOrEmptyElement() {
        for (String suite :OpenSsl.availableOpenSslCipherSuites()) {
            assertNotNull(suite);
            assertFalse(suite.isEmpty());
        }
    }
}
