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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

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
    void repeatedCompressionReturnsTheSameResult() throws Exception {
        byte[] certificate = "certificate-chain-compressed-twice".getBytes(StandardCharsets.US_ASCII);

        byte[] first = algorithm.compress(null, certificate);
        byte[] second = algorithm.compress(null, certificate.clone());

        assertArrayEquals(first, second);
        assertArrayEquals(certificate, algorithm.decompress(null, certificate.length, second));
    }

    @Test
    void manyDistinctCertificatesRoundTrip() throws Exception {
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 200; i++) {
                byte[] certificate = ("certificate-chain-" + i).getBytes(StandardCharsets.US_ASCII);
                byte[] compressed = algorithm.compress(null, certificate);
                assertArrayEquals(certificate, algorithm.decompress(null, certificate.length, compressed));
            }
        }
    }

    @Test
    void concurrentCompressionOfMoreCertificatesThanCachedRoundTrips() throws Exception {
        final int threads = 8;
        final int certificates = 40;
        final CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> results = new ArrayList<Future<Void>>();
            for (int t = 0; t < threads; t++) {
                final int offset = t;
                results.add(executor.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        start.await();
                        for (int i = 0; i < 2000; i++) {
                            byte[] certificate = ("concurrent-certificate-chain-" + (i + offset) % certificates)
                                    .getBytes(StandardCharsets.US_ASCII);
                            byte[] compressed = algorithm.compress(null, certificate);
                            assertArrayEquals(certificate,
                                    algorithm.decompress(null, certificate.length, compressed));
                        }
                        return null;
                    }
                }));
            }
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void certificateLargerThanCacheLimitRoundTrips() throws Exception {
        byte[] certificate = new byte[64 * 1024];
        for (int i = 0; i < certificate.length; i++) {
            certificate[i] = (byte) (i * 31);
        }

        assertArrayEquals(algorithm.compress(null, certificate), algorithm.compress(null, certificate));
        assertArrayEquals(certificate,
                algorithm.decompress(null, certificate.length, algorithm.compress(null, certificate)));
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

    @Test
    void rejectsPresetDictionary() {
        byte[] certificate = "certificate-chain".getBytes(StandardCharsets.US_ASCII);
        Deflater deflater = new Deflater();
        byte[] buffer = new byte[certificate.length + 64];
        int length;
        try {
            deflater.setDictionary("dictionary".getBytes(StandardCharsets.US_ASCII));
            deflater.setInput(certificate);
            deflater.finish();
            length = deflater.deflate(buffer);
        } finally {
            deflater.end();
        }
        byte[] compressed = Arrays.copyOf(buffer, length);

        assertThrows(DataFormatException.class,
                () -> algorithm.decompress(null, certificate.length, compressed));
    }

    @Test
    void rejectsLengthLargerThanTlsMaximum() {
        assertThrows(DataFormatException.class,
                () -> algorithm.decompress(null, 0x1000000, new byte[0]));
    }
}
