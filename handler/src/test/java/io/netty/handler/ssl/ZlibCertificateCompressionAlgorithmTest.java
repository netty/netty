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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZlibCertificateCompressionAlgorithmTest {

    private final ZlibCertificateCompressionAlgorithm algorithm = ZlibCertificateCompressionAlgorithm.INSTANCE;

    @Test
    void roundTrip() throws Exception {
        byte[] certificate = new byte[16384];
        byte[] value = "certificate-chain".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < certificate.length; i += value.length) {
            System.arraycopy(value, 0, certificate, i, Math.min(value.length, certificate.length - i));
        }

        byte[] compressed = algorithm.compress(null, certificate);

        assertArrayEquals(certificate, algorithm.decompress(null, certificate.length, compressed));
        assertEquals(1, algorithm.algorithmId());
    }

    @Test
    void rejectsOutputLargerThanAdvertised() throws Exception {
        byte[] certificate = "certificate-chain".getBytes(StandardCharsets.US_ASCII);
        byte[] compressed = algorithm.compress(null, certificate);

        assertThrows(DataFormatException.class,
                () -> algorithm.decompress(null, certificate.length - 1, compressed));
    }

    @Test
    void rejectsOutputSmallerThanAdvertised() throws Exception {
        byte[] certificate = "certificate-chain".getBytes(StandardCharsets.US_ASCII);
        byte[] compressed = algorithm.compress(null, certificate);

        assertThrows(DataFormatException.class,
                () -> algorithm.decompress(null, certificate.length + 1, compressed));
    }

    @Test
    void rejectsTrailingInput() throws Exception {
        byte[] certificate = "certificate-chain".getBytes(StandardCharsets.US_ASCII);
        byte[] compressed = algorithm.compress(null, certificate);
        compressed = Arrays.copyOf(compressed, compressed.length + 1);

        byte[] input = compressed;
        assertThrows(DataFormatException.class,
                () -> algorithm.decompress(null, certificate.length, input));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(DataFormatException.class,
                () -> algorithm.decompress(null, 16, new byte[] { 1, 2, 3, 4 }));
    }
}
