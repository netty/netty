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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.net.ssl.SSLEngine;

final class ZlibCertificateCompressionAlgorithm implements OpenSslCertificateCompressionAlgorithm {
    static final ZlibCertificateCompressionAlgorithm INSTANCE = new ZlibCertificateCompressionAlgorithm();

    // The zlib algorithm identifier assigned by RFC 8879.
    private static final int ALGORITHM_ID = 1;
    private static final int BUFFER_SIZE = 8192;

    private ZlibCertificateCompressionAlgorithm() { }

    @Override
    public byte[] compress(SSLEngine engine, byte[] uncompressedCertificate) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(uncompressedCertificate);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream(uncompressedCertificate.length);
            byte[] buffer = new byte[BUFFER_SIZE];
            while (!deflater.finished()) {
                int length = deflater.deflate(buffer);
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    @Override
    public byte[] decompress(SSLEngine engine, int uncompressedLen, byte[] compressedCertificate)
            throws DataFormatException {
        if (uncompressedLen < 0) {
            throw new DataFormatException("uncompressed length must be non-negative");
        }

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressedCertificate);
            byte[] output = new byte[uncompressedLen];
            int offset = 0;
            while (!inflater.finished() && offset < output.length) {
                int length = inflater.inflate(output, offset, output.length - offset);
                if (length == 0) {
                    break;
                }
                offset += length;
            }
            if (!inflater.finished() || offset != uncompressedLen || inflater.getRemaining() != 0) {
                throw new DataFormatException("decompressed certificate length does not match uncompressed length");
            }
            return output;
        } finally {
            inflater.end();
        }
    }

    @Override
    public int algorithmId() {
        return ALGORITHM_ID;
    }
}
