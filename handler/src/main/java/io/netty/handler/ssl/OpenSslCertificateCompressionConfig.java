/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.ssl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.internal.tcnative.CertificateCompressionAlgo;
import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;

public class OpenSslCertificateCompressionConfig implements
        Iterable<OpenSslCertificateCompressionConfig.AlgorithmDirectionPair> {

    private List<AlgorithmDirectionPair> algorithmList = new ArrayList<AlgorithmDirectionPair>();

    /**
     * Adds a certificate compression algorithm to the given {@link SSLContext}.
     * For servers, algorithm preference order is dictated by the order of algorithm registration.
     * Most preferred algorithm should be registered first.
     *
     * This method is currently only supported when {@code BoringSSL} is used.
     *
     * @param algorithm implementation of the compression and or decompression algorithm as a
     * {@link CertificateCompressionAlgo}
     * @param direction indicates whether decompression support should be advertized, compression should be applied for
     *                  peers which support it, or both. This allows the caller to support one way compression only.
     * <PRE>
     * {@link SSL#SSL_CERT_COMPRESSION_DIRECTION_COMPRESS}
     * {@link SSL#SSL_CERT_COMPRESSION_DIRECTION_DECOMPRESS}
     * {@link SSL#SSL_CERT_COMPRESSION_DIRECTION_BOTH}
     * </PRE>
     * @return {@code OpenSslCertificateCompressionConfig} for a fluent API.
     */
    public OpenSslCertificateCompressionConfig addAlgorithm(CertificateCompressionAlgo algorithm, int direction) {
        algorithmList.add(new AlgorithmDirectionPair(algorithm, direction));
        return this;
    }

    @Override
    public Iterator<AlgorithmDirectionPair> iterator() {
        return algorithmList.iterator();
    }

    public static class AlgorithmDirectionPair {

        public CertificateCompressionAlgo algorithm;
        public int direction;

        public AlgorithmDirectionPair(CertificateCompressionAlgo algorithm, int direction) {
            this.algorithm = algorithm;
            this.direction = direction;
        }
    }
}
