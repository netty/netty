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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupsConverterTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "secp224r1, P-224",
            "prime256v1, P-256",
            "secp256r1, P-256",
            "secp384r1, P-384",
            "secp521r1, P-521",
            "x25519, X25519",
            "x448, X448",
            "x25519mlkem768, X25519MLKEM768",
            // Misspelled alias shipped in earlier releases, kept for backwards compatibility.
            "x25519mlkem786, X25519MLKEM768"
    })
    void convertsJavaNamesToOpenSslNames(String javaName, String expectedOpenSslName) {
        assertEquals(expectedOpenSslName, GroupsConverter.toOpenSsl(javaName));
    }

    @Test
    void unknownAndAlreadyConvertedNamesArePassedThrough() {
        assertEquals("X25519MLKEM768", GroupsConverter.toOpenSsl("X25519MLKEM768"));
        assertEquals("X25519", GroupsConverter.toOpenSsl("X25519"));
        assertEquals("unknown", GroupsConverter.toOpenSsl("unknown"));
    }
}
