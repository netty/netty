/*
 * Copyright 2022 The Netty Project
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

import javax.net.ssl.SSLEngine;

/**
 * Provides compression and decompression implementations for TLS Certificate Compression
 * (<a href="https://tools.ietf.org/html/rfc8879">RFC 8879</a>).
 */
public interface OpenSslCertificateCompressionAlgorithm {

    /**
     * Compress the given input with the specified algorithm and return the compressed bytes.
     *
     * @param engine                    the {@link SSLEngine}
     * @param uncompressedCertificate   the uncompressed certificate
     * @return                          the compressed form of the certificate
     * @throws Exception                thrown if an error occurs while compressing
     */
    byte[] compress(SSLEngine engine, byte[] uncompressedCertificate) throws Exception;

    /**
     * Decompress the given input with the specified algorithm and return the decompressed bytes.
     *
     * <h3>Implementation
     * <a href="https://tools.ietf.org/html/rfc8879#section-5">Security Considerations</a></h3>
     * <p>Implementations SHOULD bound the memory usage when decompressing the CompressedCertificate message.</p>
     * <p>
     * Implementations MUST limit the size of the resulting decompressed chain to the specified {@code uncompressedLen},
     * and they MUST abort the connection (throw an exception) if the size of the output of the decompression
     * function exceeds that limit.
     * </p>
     *
     * @param engine                    the {@link SSLEngine}
     * @param uncompressedLen           the expected length of the decompressed certificate that will be returned.
     * @param compressedCertificate     the compressed form of the certificate
     * @return                          the decompressed form of the certificate
     * @throws Exception                thrown if an error occurs while decompressing or output size exceeds
     *                                  {@code uncompressedLen}
     */
    byte[] decompress(SSLEngine engine, int uncompressedLen, byte[] compressedCertificate) throws Exception;

    /**
     * Return the ID for the compression algorithm provided for by a given implementation.
     *
     * @return compression algorithm ID as specified by
     * <a href="https://datatracker.ietf.org/doc/html/rfc8879">RFC8879</a>.
     */
    int algorithmId();
}
